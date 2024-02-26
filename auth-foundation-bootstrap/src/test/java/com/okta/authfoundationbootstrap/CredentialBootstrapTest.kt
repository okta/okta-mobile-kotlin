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
package com.okta.authfoundationbootstrap

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.credential.CredentialDataSource
import com.okta.authfoundation.credential.CredentialDataSource.Companion.createCredentialDataSource
import com.okta.authfoundation.credential.RoomTokenStorage
import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.credential.storage.TokenDatabase
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.TestTokenEncryptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class CredentialBootstrapTest {
    @get:Rule val oktaRule = OktaRule()

    private lateinit var database: TokenDatabase
    private lateinit var roomTokenStorage: RoomTokenStorage
    private lateinit var token: Token
    private lateinit var credentialDataSource: CredentialDataSource

    private fun initialize() {
        CredentialBootstrap.initialize(credentialDataSource)
    }

    @Before fun reset() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TokenDatabase::class.java
        ).allowMainThreadQueries().build()
        roomTokenStorage = RoomTokenStorage(database, TestTokenEncryptionHandler())
        token = createToken(accessToken = "mainToken")
        credentialDataSource = oktaRule.createOidcClient().createCredentialDataSource(roomTokenStorage)
        CredentialBootstrap.reset()
    }

    @Test fun testCredentialFailsBeforeInitialize(): Unit = runTest {
        val exception = assertFailsWith<IllegalStateException> {
            CredentialBootstrap.defaultCredential()
        }
        assertThat(exception).hasMessageThat().isEqualTo("CredentialBoostrap not initialized. Please call initialize before attempting to access properties and methods.")
    }

    @Test fun testCredentialDataSourceFailsBeforeInitialize(): Unit = runTest {
        assertFailsWith<Exception> {
            CredentialBootstrap.credentialDataSource
        }
    }

    @Test fun testInitializeTwiceFails(): Unit = runTest {
        initialize()
        val exception = assertFailsWith<IllegalStateException> {
            initialize()
        }
        assertThat(exception).hasMessageThat().isEqualTo("Credential bootstrap was already initialized.")
    }

    @Test fun `defaultCredential returns null if there is no default credential set`() = runTest {
        initialize()
        assertThat(CredentialBootstrap.defaultCredential()).isNull()
    }

    @Test fun `defaultCredential should return the credential set as default credential in token storage`() = runTest {
        initialize()
        roomTokenStorage.add(token, Token.Metadata("id", emptyMap(), null, isDefault = true))
        assertThat(CredentialBootstrap.defaultCredential()?.token).isEqualTo(token)
    }

    @Test fun `defaultCredential should return the credential that was called with credential's setDefault method`() = runTest {
        initialize()
        roomTokenStorage.add(token, Token.Metadata("id", emptyMap(), null, isDefault = false))
        assertThat(CredentialBootstrap.defaultCredential()).isNull()
        credentialDataSource.getCredential("id")!!.setDefault()
        assertThat(CredentialBootstrap.defaultCredential()?.token).isEqualTo(token)
    }

    @Test fun `token in defaultCredential should change if setDefaultCredential was called after accessing defaultCredential`() = runTest {
        initialize()
        roomTokenStorage.add(token, Token.Metadata("id", emptyMap(), null, isDefault = true))
        val credential = CredentialBootstrap.defaultCredential()
        val newToken = createToken(accessToken = "otherToken")
        val credential2 = CredentialBootstrap.setDefaultCredential(newToken)
        assertThat(credential).isSameInstanceAs(credential2)
        assertThat(credential?.token).isEqualTo(newToken)
    }

    @Test fun `defaultCredential should return the same instance`() = runTest {
        initialize()
        roomTokenStorage.add(token, Token.Metadata("id", emptyMap(), null, isDefault = true))
        val credential = CredentialBootstrap.defaultCredential()
        val credential2 = CredentialBootstrap.defaultCredential()
        assertThat(credential).isSameInstanceAs(credential2)
    }

    @Test fun `setDefaultCredential stores token and returns new default credential`() = runTest {
        initialize()
        val credential = CredentialBootstrap.setDefaultCredential(token)
        val allCredentials = credentialDataSource.findCredential { true }
        assertThat(allCredentials).isEqualTo(listOf(credential))
    }

    @Test fun testCredentialDataSourceIsInitialized(): Unit = runTest {
        initialize()
        assertThat(CredentialBootstrap.credentialDataSource).isNotNull()
    }

    @Test fun testDeletedCredentialCreatesNewCredential(): Unit = runTest {
        initialize()
        CredentialBootstrap.setDefaultCredential(token)
        val credential1 = CredentialBootstrap.defaultCredential()!!
        credential1.delete()
        assertThat(CredentialBootstrap.defaultCredential()).isNull()
        CredentialBootstrap.setDefaultCredential(token)
        val credential2 = CredentialBootstrap.defaultCredential()
        assertThat(credential1).isNotNull()
        assertThat(credential2).isNotNull()
        assertThat(credential1).isNotSameInstanceAs(credential2)
    }

    @Test fun testCredentialReturnsSameInstanceWhenAsync(): Unit = runTest {
        initialize()
        CredentialBootstrap.setDefaultCredential(token)
        val credential1 = async(Dispatchers.IO) { CredentialBootstrap.defaultCredential() }
        val credential2 = async(Dispatchers.IO) { CredentialBootstrap.defaultCredential() }
        assertThat(credential1.await()).isNotNull()
        assertThat(credential1.await()).isSameInstanceAs(credential2.await())
    }

    @Test fun testOidcClientIsAvailableAfterInitialization() {
        initialize()
        assertThat(CredentialBootstrap.oidcClient).isNotNull()
    }

    @After fun tearDown() {
        database.close()
    }

    private fun createToken(
        scope: String = "openid email profile offline_access",
        accessToken: String = "exampleAccessToken",
        expiresIn: Int = 12345,
        idToken: String? = null,
        refreshToken: String? = null,
        deviceSecret: String? = null,
    ): Token {
        return Token(
            tokenType = "Bearer",
            expiresIn = expiresIn,
            accessToken = accessToken,
            scope = scope,
            refreshToken = refreshToken,
            deviceSecret = deviceSecret,
            idToken = idToken,
            issuedTokenType = null,
        )
    }
}
