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
import java.util.Collections

class Credential internal constructor(
    oidcClient: OidcClient,
    private val storage: TokenStorage,
    @Volatile private var _token: Token? = null,
    @Volatile private var _metadata: Map<String, String> = emptyMap()
) {
    val oidcClient: OidcClient = oidcClient.withCredential(this)

    val token: Token?
        get() {
            return _token
        }

    val metadata: Map<String, String>
        get() {
            return Collections.unmodifiableMap(_metadata)
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
            TokenType.DEVICE_SECRET -> {
                localToken.deviceSecret ?: return OidcClientResult.Error(IllegalStateException("No device secret."))
            }
        }

        return oidcClient.introspectToken(tokenType, token)
    }

    suspend fun storeToken(token: Token? = _token, metadata: Map<String, String> = _metadata) {
        val metadataCopy = metadata.toMap() // Making a defensive copy, so it's not modified outside our control.
        val localToken = _token
        if (localToken != null && token != null) {
            storage.replace(
                existingEntry = TokenStorage.Entry(localToken, _metadata),
                updatedEntry = TokenStorage.Entry(token, metadataCopy),
            )
        } else if (localToken != null) {
            storage.remove(TokenStorage.Entry(localToken, _metadata))
        } else if (token != null) {
            storage.add(TokenStorage.Entry(token, metadataCopy))
        }
        _token = token
        _metadata = metadataCopy
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
                storeToken(
                    result.result.copy(
                        // Device Secret isn't returned when refreshing.
                        deviceSecret = result.result.deviceSecret ?: _token?.deviceSecret,
                    )
                )
            }
        }
    }

    suspend fun revokeToken(tokenType: RevokeTokenType): OidcClientResult<Unit> {
        val localToken = token ?: return OidcClientResult.Error(IllegalStateException("No token."))
        val token = when (tokenType) {
            RevokeTokenType.REFRESH_TOKEN -> {
                localToken.refreshToken ?: return OidcClientResult.Error(IllegalStateException("No refresh token."))
            }
            RevokeTokenType.ACCESS_TOKEN -> {
                localToken.accessToken
            }
            RevokeTokenType.DEVICE_SECRET -> {
                localToken.deviceSecret ?: return OidcClientResult.Error(IllegalStateException("No device secret."))
            }
        }
        return oidcClient.revokeToken(token)
    }

    fun scopes(): Set<String> {
        return token?.scope?.split(" ")?.toSet() ?: oidcClient.configuration.defaultScopes
    }
}
