/*
 * Copyright 2022-Present Okta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.okta.authfoundation.credential

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.AuthFoundation
import com.okta.authfoundation.client.ApplicationContextHolder
import com.okta.authfoundation.credential.events.CredentialCreatedEvent
import com.okta.authfoundation.credential.storage.TokenDatabase
import com.okta.testhelpers.MockAesEncryptionHandler
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.TestTokenEncryptionHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class CredentialDataSourceTest {
    @get:Rule val oktaRule = OktaRule()

    private lateinit var database: TokenDatabase
    private lateinit var roomTokenStorage: RoomTokenStorage
    private lateinit var token: Token
    private lateinit var credentialDataSource: CredentialDataSource

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TokenDatabase::class.java
        ).allowMainThreadQueries().build()
        roomTokenStorage = RoomTokenStorage(database, TestTokenEncryptionHandler())
        token = createToken(accessToken = "mainToken")
        credentialDataSource = CredentialDataSource(roomTokenStorage)
        mockkObject(Credential)
        coEvery { Credential.credentialDataSource() } returns credentialDataSource
        mockkObject(ApplicationContextHolder)
        every { ApplicationContextHolder.appContext } returns ApplicationProvider.getApplicationContext()
        mockkObject(DefaultCredentialIdDataStore)
        every { DefaultCredentialIdDataStore.instance } returns DefaultCredentialIdDataStore(MockAesEncryptionHandler.getInstance())
        mockkObject(AuthFoundation)
        coEvery { AuthFoundation.initializeStorage() } just runs
    }

    @Test fun `test allIds returns all token IDs from token storage`() = runTest {
        roomTokenStorage.add(token, Token.Metadata("id", emptyMap(), idToken = null))
        roomTokenStorage.add(createToken(id = "id2"), Token.Metadata("id2", emptyMap(), idToken = null))
        assertThat(credentialDataSource.allIds()).isEqualTo(listOf("id", "id2"))
    }

    @Test fun `get metadata for an existing token`() = runTest {
        val metadata = Token.Metadata("id", emptyMap(), idToken = null)
        roomTokenStorage.add(token, metadata)
        assertThat(credentialDataSource.metadata("id")).isEqualTo(metadata)
    }

    @Test fun `get metadata for a non-existing token`() = runTest {
        assertThat(credentialDataSource.metadata("non-existing-id")).isNull()
    }

    @Test fun `setMetadata changes metadata`() = runTest {
        val originalMetadata = Token.Metadata("id", emptyMap(), idToken = null)
        roomTokenStorage.add(token, originalMetadata)
        val newMetadata = Token.Metadata("id", mapOf("someKey" to "someValue"), idToken = null)
        credentialDataSource.setMetadata(newMetadata)
        assertThat(credentialDataSource.metadata("id")).isEqualTo(newMetadata)
    }

    @Test fun testCreate(): Unit = runTest {
        val tokenStorage = spyk(roomTokenStorage)
        val dataSource = CredentialDataSource(tokenStorage)
        val credential = dataSource.createCredential(token)
        assertThat(credential.token).isEqualTo(token)
        assertThat(credential.tags).isEmpty()
        coVerify { tokenStorage.add(any(), any(), any()) }
    }

    @Test fun testCreateEmitsEvent(): Unit = runTest {
        val credential = credentialDataSource.createCredential(token)
        assertThat(oktaRule.eventHandler).hasSize(1)
        val event = oktaRule.eventHandler[0]
        assertThat(event).isInstanceOf(CredentialCreatedEvent::class.java)
        val createdEvent = event as CredentialCreatedEvent
        assertThat(createdEvent.credential).isEqualTo(credential)
    }

    @Test fun `replaceToken fails when trying to replace a non-existent token`() = runTest {
        assertFailsWith<NoSuchElementException> {
            credentialDataSource.replaceToken(token)
        }
    }

    @Test fun `replaceToken successfully replaces token in tokenStorage`() = runTest {
        credentialDataSource.createCredential(token)
        val newToken = createToken(accessToken = "newToken")
        credentialDataSource.replaceToken(newToken)
        val credential = credentialDataSource.getCredential("id")

        assertThat(roomTokenStorage.getToken("id")).isEqualTo(newToken)
        assertThat(credential!!.token).isEqualTo(newToken)
    }

    @Test fun `getCredential fetches the correct credential`() = runTest {
        roomTokenStorage.add(token, Token.Metadata("id", emptyMap(), idToken = null))
        roomTokenStorage.add(
            createToken(id = "id2", accessToken = "another-token"), Token.Metadata(id = "id2", emptyMap(), idToken = null)
        )
        assertThat(credentialDataSource.getCredential("id")!!.token).isEqualTo(token)
    }

    @Test fun `getCredential returns null if a token with id doesn't exist`() = runTest {
        assertThat(credentialDataSource.getCredential("non-existent-id")).isNull()
    }

    @Test fun `getCredential fetches from cache when possible`() = runTest {
        val tokenStorage = spyk(roomTokenStorage)
        val dataSource = CredentialDataSource(tokenStorage)
        tokenStorage.add(token, Token.Metadata("id", emptyMap(), idToken = null))

        val credential1 = dataSource.getCredential("id")
        val credential2 = dataSource.getCredential("id")

        coVerify(exactly = 1) { tokenStorage.getToken("id") }
        assertThat(credential1).isSameInstanceAs(credential2)
    }

    @Test fun `findCredential fetches all credentials that match the expression`() = runTest {
        val token2 = createToken(id = "id2", accessToken = "token2")
        roomTokenStorage.add(token, Token.Metadata("id", mapOf("someField" to "sameValue"), idToken = null))
        roomTokenStorage.add(token2, Token.Metadata("id2", mapOf("someField" to "sameValue"), idToken = null))

        val result = credentialDataSource.findCredential { metadata -> metadata.tags["someField"] == "sameValue" }
        assertThat(result.map { it.token }).isEqualTo(listOf(token, token2))
    }

    @Test fun testRemove(): Unit = runTest {
        val dataSource = CredentialDataSource(roomTokenStorage)
        val credential1 = dataSource.createCredential(createToken(id = "id1"))
        dataSource.createCredential(createToken(id = "id2"))
        assertThat(dataSource.allIds()).hasSize(2)
        credential1.delete()
        assertThat(dataSource.allIds()).hasSize(1)
    }

    @After fun tearDown() {
        database.close()
        unmockkAll()
    }
}
