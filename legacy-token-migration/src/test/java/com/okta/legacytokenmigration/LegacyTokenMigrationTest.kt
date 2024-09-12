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
package com.okta.legacytokenmigration

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.credential.CredentialDataSource
import com.okta.authfoundation.credential.Token
import com.okta.legacytokenmigration.LegacyTokenMigration.hasMarkedTokensAsMigrated
import com.okta.legacytokenmigration.LegacyTokenMigration.markTokensAsMigrated
import com.okta.legacytokenmigration.LegacyTokenMigration.sharedPreferences
import com.okta.oidc.Tokens
import com.okta.oidc.clients.sessions.SessionClient
import com.okta.oidc.util.AuthorizationException
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.TestClock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class LegacyTokenMigrationTest {
    @get:Rule val oktaRule = OktaRule()

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var tokenUUID: UUID

    @Before fun reset() {
        tokenUUID = UUID.randomUUID()
        sharedPreferences = ApplicationProvider.getApplicationContext<Context>().sharedPreferences()
        sharedPreferences.edit().clear().apply()
        mockkObject(CredentialDataSource)
        mockkObject(OidcConfiguration)
        mockkStatic(UUID::class)
        every { OidcConfiguration.default } returns OidcConfiguration(
            "clientId",
            "defaultScope",
            "issuer"
        )
        every { UUID.randomUUID() } returns tokenUUID
    }

    @After fun tearDown() {
        unmockkAll()
    }

    private fun mockSessionClient(token: Tokens?): SessionClient {
        return mockk {
            every { tokens } returns token
            every { clear() } just runs
        }
    }

    private fun mockLegacyToken(): Tokens {
        return mockk {
            every { expiresIn } returns 300
            every { accessToken } returns "ExampleAccessToken"
            every { scope } returns arrayOf("openid", "profile", "offline_access")
            every { refreshToken } returns "ExampleRefreshToken"
            every { idToken } returns "ExampleIdToken"
        }
    }

    @Test fun testMigrate(): Unit = runBlocking {
        val credentialDataSource = mockk<CredentialDataSource> {
            coEvery { createCredential(any(), any(), any()) } returns mockk {
                every { id } returns "mock-token-id"
            }
        }
        coEvery { CredentialDataSource.getInstance() } returns credentialDataSource

        val sessionClient = mockSessionClient(token = mockLegacyToken())
        val result = LegacyTokenMigration.migrate(
            context = ApplicationProvider.getApplicationContext(),
            sessionClient = sessionClient,
        )
        assertThat(result).isEqualTo(LegacyTokenMigration.Result.SuccessfullyMigrated("mock-token-id"))
        assertThat(sharedPreferences.hasMarkedTokensAsMigrated()).isTrue()
        verify { sessionClient.clear() }
        val token = Token(
            id = tokenUUID.toString(),
            tokenType = "Bearer",
            expiresIn = 300,
            accessToken = "ExampleAccessToken",
            scope = "openid profile offline_access",
            refreshToken = "ExampleRefreshToken",
            idToken = "ExampleIdToken",
            deviceSecret = null,
            issuedTokenType = null,
            oidcConfiguration = mockk {
                every { clock } returns TestClock()
            }
        )
        coVerify { credentialDataSource.createCredential(token) }
    }

    @Test fun testMigrateWithNullTokenReturnsMissingLegacyToken(): Unit = runBlocking {
        val sessionClient = mockSessionClient(token = null)
        val result = LegacyTokenMigration.migrate(
            context = ApplicationProvider.getApplicationContext(),
            sessionClient = sessionClient,
        )
        assertThat(result).isEqualTo(LegacyTokenMigration.Result.MissingLegacyToken)
        assertThat(sharedPreferences.hasMarkedTokensAsMigrated()).isFalse()
        verify(exactly = 0) { sessionClient.clear() }
    }

    @Test fun testMigrateWithPreviouslyMigrated(): Unit = runBlocking {
        val sessionClient = mockSessionClient(token = null)
        sharedPreferences.markTokensAsMigrated("token-id")
        val result = LegacyTokenMigration.migrate(
            context = ApplicationProvider.getApplicationContext(),
            sessionClient = sessionClient,
        )
        (result as LegacyTokenMigration.Result.PreviouslyMigrated).tokenId
        assertThat(result).isEqualTo(LegacyTokenMigration.Result.PreviouslyMigrated("token-id"))
        verify(exactly = 0) { sessionClient.clear() }
    }

    @Test fun testMigrateWithThrowingLegacyTokensReturnsError(): Unit = runBlocking {
        val sessionClient = mockk<SessionClient> {
            every { tokens } throws AuthorizationException("From test.", AssertionError())
        }

        val result = LegacyTokenMigration.migrate(
            context = ApplicationProvider.getApplicationContext(),
            sessionClient = sessionClient,
        )
        assertThat(result).isInstanceOf(LegacyTokenMigration.Result.Error::class.java)
        val error = result as LegacyTokenMigration.Result.Error
        assertThat(error.exception).isInstanceOf(AuthorizationException::class.java)
        assertThat(error.exception).hasMessageThat().isEqualTo("From test.")
        assertThat(sharedPreferences.hasMarkedTokensAsMigrated()).isFalse()
    }
}
