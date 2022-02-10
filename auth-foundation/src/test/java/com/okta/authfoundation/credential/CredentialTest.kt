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

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.OktaSdkDefaults
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClientResult
import com.okta.testhelpers.OktaRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.assertFailsWith

class CredentialTest {
    @get:Rule val oktaRule = OktaRule()

    @Test fun testOidcClient() {
        val credential = createCredential()
        assertThat(credential.oidcClient.credential).isEqualTo(credential)
    }

    @Test fun testMetadataChangeThrows() {
        val map = mutableMapOf<String, String>()
        map["name"] = "Default"
        val credential = createCredential(metadata = map)
        assertFailsWith<UnsupportedOperationException> {
            (credential.metadata as MutableMap<String, String>)["secure"] = "true"
        }
    }

    @Test fun testRemove(): Unit = runBlocking {
        val storage = mock<TokenStorage>()
        val token = createToken()
        val credential = createCredential(token = token, tokenStorage = storage)
        credential.remove()
        verify(storage).remove(eq(TokenStorage.Entry(token, emptyMap())))
    }

    @Test fun testRevokeAccessToken(): Unit = runBlocking {
        val oidcClient = mock<OidcClient> {
            onBlocking { revokeToken(any()) } doReturn OidcClientResult.Success(Unit)
            on { withCredential(any()) } doReturn it
        }
        val credential = createCredential(token = createToken(), oidcClient = oidcClient)
        credential.revokeToken(RevokeTokenType.ACCESS_TOKEN)
        verify(oidcClient).revokeToken(eq("exampleAccessToken"))
    }

    @Test fun testRevokeRefreshToken(): Unit = runBlocking {
        val oidcClient = mock<OidcClient> {
            onBlocking { revokeToken(any()) } doReturn OidcClientResult.Success(Unit)
            on { withCredential(any()) } doReturn it
        }
        val credential = createCredential(token = createToken(refreshToken = "exampleRefreshToken"), oidcClient = oidcClient)
        credential.revokeToken(RevokeTokenType.REFRESH_TOKEN)
        verify(oidcClient).revokeToken(eq("exampleRefreshToken"))
    }

    @Test fun testRevokeDeviceSecret(): Unit = runBlocking {
        val oidcClient = mock<OidcClient> {
            onBlocking { revokeToken(any()) } doReturn OidcClientResult.Success(Unit)
            on { withCredential(any()) } doReturn it
        }
        val credential = createCredential(token = createToken(deviceSecret = "exampleDeviceSecret"), oidcClient = oidcClient)
        credential.revokeToken(RevokeTokenType.DEVICE_SECRET)
        verify(oidcClient).revokeToken(eq("exampleDeviceSecret"))
    }

    @Test fun testRevokeTokenWithNullTokenReturnsError(): Unit = runBlocking {
        val credential = createCredential()
        val result = credential.revokeToken()
        assertThat(result).isInstanceOf(OidcClientResult.Error::class.java)
        val errorResult = result as OidcClientResult.Error<Unit>
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No token.")
    }

    @Test fun testRevokeTokenWithNullRefreshTokenReturnsError(): Unit = runBlocking {
        val credential = createCredential(token = createToken())
        val result = credential.revokeToken(RevokeTokenType.REFRESH_TOKEN)
        assertThat(result).isInstanceOf(OidcClientResult.Error::class.java)
        val errorResult = result as OidcClientResult.Error<Unit>
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No refresh token.")
    }

    @Test fun testRevokeTokenWithNullDeviceSecretReturnsError(): Unit = runBlocking {
        val credential = createCredential(token = createToken())
        val result = credential.revokeToken(RevokeTokenType.DEVICE_SECRET)
        assertThat(result).isInstanceOf(OidcClientResult.Error::class.java)
        val errorResult = result as OidcClientResult.Error<Unit>
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No device secret.")
    }

    @Test fun testScopesReturnDefaultScopes() {
        val credential = createCredential()
        assertThat(credential.scopes()).isEqualTo(setOf("openid", "email", "profile", "offline_access"))
    }


    @Test fun testScopesReturnTokenScopes() {
        val credential = createCredential(token = createToken(scope = "openid bank_account"))
        assertThat(credential.scopes()).isEqualTo(setOf("openid", "bank_account"))
    }

    private fun createCredential(
        token: Token? = null,
        metadata: Map<String, String> = emptyMap(),
        oidcClient: OidcClient = oktaRule.createOidcClient(),
        tokenStorage: TokenStorage = OktaSdkDefaults.defaultStorage(),
    ): Credential {
        return Credential(oidcClient, tokenStorage, token, metadata)
    }

    private fun createToken(
        scope: String = "openid email profile offline_access",
        refreshToken: String? = null,
        deviceSecret: String? = null,
    ): Token {
        return Token(
            tokenType = "Bearer",
            expiresIn = 3600,
            accessToken = "exampleAccessToken",
            scope = scope,
            refreshToken = refreshToken,
            deviceSecret = deviceSecret,
        )
    }
}
