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
import com.okta.authfoundation.credential.Credential
import com.okta.authfoundation.credential.Token
import com.okta.legacytokenmigration.LegacyTokenMigration.hasMarkedTokensAsMigrated
import com.okta.legacytokenmigration.LegacyTokenMigration.markTokensAsMigrated
import com.okta.legacytokenmigration.LegacyTokenMigration.sharedPreferences
import com.okta.oidc.Tokens
import com.okta.oidc.clients.sessions.SessionClient
import com.okta.oidc.util.AuthorizationException
import com.okta.testhelpers.OktaRule
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import java.lang.AssertionError

@RunWith(RobolectricTestRunner::class)
class LegacyTokenMigrationTest {
    @get:Rule val oktaRule = OktaRule()

    private lateinit var sharedPreferences: SharedPreferences

    @Before fun reset() {
        sharedPreferences = ApplicationProvider.getApplicationContext<Context>().sharedPreferences()
        sharedPreferences.edit().clear().apply()
    }

    private fun mockCredential(): Credential {
        return mock()
    }

    private fun mockSessionClient(token: Tokens?): SessionClient {
        return mock {
            on { tokens } doReturn token
        }
    }

    private fun mockLegacyToken(): Tokens {
        return mock {
            on { expiresIn } doReturn 300
            on { accessToken } doReturn "ExampleAccessToken"
            on { scope } doReturn arrayOf("openid", "profile", "offline_access")
            on { refreshToken } doReturn "ExampleRefreshToken"
            on { idToken } doReturn "ExampleIdToken"
        }
    }

    @Test fun testMigrate(): Unit = runBlocking {
        val credential = mockCredential()
        val sessionClient = mockSessionClient(token = mockLegacyToken())
        val result = LegacyTokenMigration.migrate(
            context = ApplicationProvider.getApplicationContext(),
            sessionClient = sessionClient,
            credential = credential,
        )
        assertThat(result).isEqualTo(LegacyTokenMigration.Result.SuccessfullyMigrated)
        assertThat(sharedPreferences.hasMarkedTokensAsMigrated()).isTrue()
        verify(sessionClient).clear()
        val token = Token(
            tokenType = "Bearer",
            expiresIn = 300,
            accessToken = "ExampleAccessToken",
            scope = "openid profile offline_access",
            refreshToken = "ExampleRefreshToken",
            idToken = "ExampleIdToken",
            deviceSecret = null,
            issuedTokenType = null,
        )
        verify(credential).storeToken(eq(token), anyOrNull())
    }

    @Test fun testMigrateWithNullTokenReturnsMissingLegacyToken(): Unit = runBlocking {
        val credential = mockCredential()
        val sessionClient = mockSessionClient(token = null)
        val result = LegacyTokenMigration.migrate(
            context = ApplicationProvider.getApplicationContext(),
            sessionClient = sessionClient,
            credential = credential,
        )
        assertThat(result).isEqualTo(LegacyTokenMigration.Result.MissingLegacyToken)
        assertThat(sharedPreferences.hasMarkedTokensAsMigrated()).isFalse()
        verify(sessionClient, never()).clear()
        verify(credential, never()).storeToken(anyOrNull(), anyOrNull())
    }

    @Test fun testMigrateWithPreviouslyMigrated(): Unit = runBlocking {
        val credential = mockCredential()
        val sessionClient = mockSessionClient(token = null)
        sharedPreferences.markTokensAsMigrated()
        val result = LegacyTokenMigration.migrate(
            context = ApplicationProvider.getApplicationContext(),
            sessionClient = sessionClient,
            credential = credential,
        )
        assertThat(result).isEqualTo(LegacyTokenMigration.Result.PreviouslyMigrated)
        verify(sessionClient, never()).clear()
        verify(credential, never()).storeToken(anyOrNull(), anyOrNull())
    }

    @Test fun testMigrateWithThrowingLegacyTokensReturnsError(): Unit = runBlocking {
        val credential = mockCredential()
        val sessionClient = mock<SessionClient> {
            on { tokens } doThrow (AuthorizationException("From test.", AssertionError()))
        }

        val result = LegacyTokenMigration.migrate(
            context = ApplicationProvider.getApplicationContext(),
            sessionClient = sessionClient,
            credential = credential,
        )
        assertThat(result).isInstanceOf(LegacyTokenMigration.Result.Error::class.java)
        val error = result as LegacyTokenMigration.Result.Error
        assertThat(error.exception).isInstanceOf(AuthorizationException::class.java)
        assertThat(error.exception).hasMessageThat().isEqualTo("From test.")
        assertThat(sharedPreferences.hasMarkedTokensAsMigrated()).isFalse()
        verify(credential, never()).storeToken(anyOrNull(), anyOrNull())
    }
}
