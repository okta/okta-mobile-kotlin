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
import com.okta.authfoundation.credential.CredentialDataSource.Companion.createCredentialDataSource
import com.okta.authfoundation.credential.events.CredentialCreatedEvent
import com.okta.authfoundation.credential.storage.TokenDatabase
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.TestTokenEncryptionHandler
import io.mockk.coVerify
import io.mockk.spyk
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
        credentialDataSource = oktaRule.createOidcClient().createCredentialDataSource(roomTokenStorage)
    }

    @Test fun `test allIds returns all token IDs from token storage`() = runTest {
        roomTokenStorage.add(token, Token.Metadata("id1", emptyMap(), null))
        roomTokenStorage.add(createToken(), Token.Metadata("id2", emptyMap(), null))
        assertThat(credentialDataSource.allIds()).isEqualTo(listOf("id1", "id2"))
    }

    @Test fun `get metadata for an existing token`() = runTest {
        val metadata = Token.Metadata("id1", emptyMap(), null)
        roomTokenStorage.add(token, metadata)
        assertThat(credentialDataSource.metadata("id1")).isEqualTo(metadata)
    }

    @Test fun `get metadata for a non-existing token`() = runTest {
        assertThat(credentialDataSource.metadata("non-existing-id")).isNull()
    }

    @Test fun testCreate(): Unit = runTest {
        val tokenStorage = spyk(roomTokenStorage)
        val oidcClient = oktaRule.createOidcClient()
        val dataSource = oidcClient.createCredentialDataSource(tokenStorage)
        val credential = dataSource.createCredential(token)
        assertThat(credential.token).isEqualTo(token)
        assertThat(credential.tags).isEmpty()
        assertThat(credential.oidcClient.credential).isSameInstanceAs(credential)
        assertThat(credential.oidcClient.endpoints).isSameInstanceAs(oidcClient.endpoints)
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
        val exception = assertFailsWith<IllegalArgumentException> {
            credentialDataSource.replaceCredential("non-existent-id", token)
        }
        assertThat(exception.message).isEqualTo("Can't replace non-existing token with id: non-existent-id")
    }

    @Test fun `replaceToken successfully replaces token in tokenStorage`() = runTest {
        roomTokenStorage.add(token, Token.Metadata("id", emptyMap(), null))
        val newToken = createToken(accessToken = "newToken")
        val credential = credentialDataSource.replaceCredential("id", newToken)

        assertThat(roomTokenStorage.getToken("id")).isEqualTo(newToken)
        assertThat(credential.token).isEqualTo(newToken)
    }

    @Test fun `replaceToken successfully replaces token in cached credential objects`() = runTest {
        val credential = credentialDataSource.createCredential(token)
        val newToken = createToken(accessToken = "newToken")
        val credential2 = credentialDataSource.replaceCredential(credential.storageIdentifier, newToken)
        assertThat(credential).isEqualTo(credential2)
        assertThat(credential.token).isEqualTo(newToken)
        assertThat(roomTokenStorage.getToken(credential.storageIdentifier)).isEqualTo(newToken)
    }

    @Test fun `getCredential fetches the correct credential`() = runTest {
        roomTokenStorage.add(token, Token.Metadata("id", emptyMap(), null))
        roomTokenStorage.add(
            createToken(accessToken = "another-token"), Token.Metadata("another-token", emptyMap(), null)
        )
        assertThat(credentialDataSource.getCredential("id")!!.token).isEqualTo(token)
    }

    @Test fun `getCredential returns null if a token with id doesn't exist`() = runTest {
        assertThat(credentialDataSource.getCredential("non-existent-id")).isNull()
    }

    @Test fun `getCredential fetches from cache when possible`() = runTest {
        val tokenStorage = spyk(roomTokenStorage)
        val dataSource = oktaRule.createOidcClient().createCredentialDataSource(tokenStorage)
        tokenStorage.add(token, Token.Metadata("id", emptyMap(), null))

        val credential1 = dataSource.getCredential("id")
        val credential2 = dataSource.getCredential("id")

        coVerify(exactly = 1) { tokenStorage.getToken("id") }
        assertThat(credential1).isSameInstanceAs(credential2)
    }

    @Test fun `findCredential fetches all credentials that match the expression`() = runTest {
        val token2 = createToken(accessToken = "token2")
        roomTokenStorage.add(token, Token.Metadata("id", mapOf("someField" to "sameValue"), null))
        roomTokenStorage.add(token2, Token.Metadata("id2", mapOf("someField" to "sameValue"), null))

        val result = credentialDataSource.findCredential { metadata -> metadata.tags["someField"] == "sameValue" }
        assertThat(result.map { it.token }).isEqualTo(listOf(token, token2))
    }

    @Test fun `containsDefaultCredential returns if there is a default credential is in token storage`() = runTest {
        roomTokenStorage.add(token, Token.Metadata("id", emptyMap(), null, isDefault = false))
        roomTokenStorage.add(createToken(accessToken = "token2"), Token.Metadata("id2", emptyMap(), null, isDefault = false))
        assertThat(credentialDataSource.containsDefaultCredential()).isFalse()
        roomTokenStorage.add(createToken(accessToken = "token3"), Token.Metadata("id3", emptyMap(), null, isDefault = true))
        assertThat(credentialDataSource.containsDefaultCredential()).isTrue()
    }

    @Test fun testRemove(): Unit = runTest {
        val dataSource = oktaRule.createOidcClient().createCredentialDataSource(roomTokenStorage)
        val credential1 = dataSource.createCredential(createToken())
        dataSource.createCredential(createToken())
        assertThat(dataSource.allIds()).hasSize(2)
        credential1.delete()
        assertThat(dataSource.allIds()).hasSize(1)
    }

    @After fun tearDown() {
        database.close()
    }
}
