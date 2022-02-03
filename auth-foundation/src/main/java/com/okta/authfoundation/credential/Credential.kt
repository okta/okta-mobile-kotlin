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

import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.client.dto.OidcIntrospectInfo
import com.okta.authfoundation.client.dto.OidcUserInfo

class Credential internal constructor(
    val oidcClient: OidcClient,
    private val storage: TokenStorage,
    @Volatile private var _token: Token? = null,
    @Volatile private var _metadata: Map<String, String> = emptyMap()
) {
    val token: Token?
        get() {
            return _token
        }

    val metadata: Map<String, String>
        get() {
            return _metadata.toMap() // Making a defensive copy, so it's not modified outside our control.
        }

    suspend fun getUserInfo(): OidcClientResult<OidcUserInfo> {
        val accessToken = token?.accessToken ?: return OidcClientResult.Error(IllegalStateException("No Access Token."))
        return oidcClient.getUserInfo(accessToken)
    }

    suspend fun introspectToken(tokenType: TokenType): OidcClientResult<OidcIntrospectInfo> {
        val localToken = token ?: return OidcClientResult.Error(IllegalStateException("No token."))
        val token = when (tokenType) {
            TokenType.REFRESH_TOKEN -> {
                localToken.refreshToken ?: return OidcClientResult.Error(IllegalStateException("No refresh token."))
            }
            TokenType.ACCESS_TOKEN -> {
                localToken.accessToken
            }
            TokenType.ID_TOKEN -> {
                localToken.idToken ?: return OidcClientResult.Error(IllegalStateException("No id token."))
            }
        }

        return oidcClient.introspectToken(tokenType, token)
    }

    suspend fun storeToken(token: Token?, metadata: Map<String, String> = _metadata) {
        _token = token
        _metadata = metadata
        // TODO: Store
    }

    suspend fun remove() {
        storeToken(null)
    }

    suspend fun refreshToken(
        scopes: Set<String> = scopes(),
    ): OidcClientResult<Token> {
        val refresh = token?.refreshToken ?: return OidcClientResult.Error(IllegalStateException("No Refresh Token."))
        return oidcClient.refreshToken(refresh, scopes).also { result ->
            if (result is OidcClientResult.Success) {
                storeToken(result.result)
            }
        }
    }

    suspend fun revokeToken(tokenType: TokenType): OidcClientResult<Unit> {
        val localToken = token ?: return OidcClientResult.Error(IllegalStateException("No token."))
        val token = when (tokenType) {
            TokenType.REFRESH_TOKEN -> {
                localToken.refreshToken ?: return OidcClientResult.Error(IllegalStateException("No refresh token."))
            }
            TokenType.ACCESS_TOKEN -> {
                localToken.accessToken
            }
            TokenType.ID_TOKEN -> {
                return OidcClientResult.Error(IllegalArgumentException("ID token can't be revoked."))
            }
        }
        return oidcClient.revokeToken(token)
    }

    fun scopes(): Set<String> {
        return token?.scope?.split(" ")?.toSet() ?: oidcClient.configuration.defaultScopes
    }
}
