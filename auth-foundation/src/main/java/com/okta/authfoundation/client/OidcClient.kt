/*
 * Copyright 2021-Present Okta, Inc.
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
package com.okta.authfoundation.client

import com.okta.authfoundation.client.dto.OidcIntrospectInfo
import com.okta.authfoundation.client.dto.OidcUserInfo
import com.okta.authfoundation.client.internal.internalTokenRequest
import com.okta.authfoundation.client.internal.performRequest
import com.okta.authfoundation.client.internal.performRequestNonJson
import com.okta.authfoundation.credential.Credential
import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.credential.TokenType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request

class OidcClient private constructor(
    val configuration: OidcConfiguration,
    val endpoints: OidcEndpoints,
    val credential: Credential? = null,
) {
    constructor(
        configuration: OidcConfiguration,
        endpoints: OidcEndpoints,
    ) : this(configuration, endpoints, null)

    companion object {
        suspend fun create(
            configuration: OidcConfiguration,
            discoveryUrl: HttpUrl
        ): OidcClientResult<OidcClient> {
            val request = Request.Builder()
                .url(discoveryUrl)
                .build()
            return when (val dtoResult = configuration.performRequest<OidcEndpoints>(request)) {
                is OidcClientResult.Error -> {
                    OidcClientResult.Error(dtoResult.exception)
                }
                is OidcClientResult.Success -> {
                    OidcClientResult.Success(OidcClient(configuration, dtoResult.result))
                }
            }
        }
    }

    internal fun withCredential(credential: Credential): OidcClient {
        return OidcClient(configuration, endpoints, credential)
    }

    suspend fun getUserInfo(accessToken: String): OidcClientResult<OidcUserInfo> {
        val request = Request.Builder()
            .addHeader("authorization", "Bearer $accessToken")
            .url(endpoints.userInfoEndpoint)
            .build()

        return configuration.performRequest<JsonObject, OidcUserInfo>(request) {
            OidcUserInfo(it)
        }
    }

    suspend fun refreshToken(
        refreshToken: String,
        scopes: Set<String> = configuration.defaultScopes,
    ): OidcClientResult<Token> {
        val formBody = FormBody.Builder()
            .add("client_id", configuration.clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("scope", scopes.joinToString(separator = " "))
            .build()

        val request = Request.Builder()
            .url(endpoints.tokenEndpoint)
            .post(formBody)
            .build()

        return internalTokenRequest(request)
    }

    suspend fun revokeToken(token: String): OidcClientResult<Unit> {
        val formBody = FormBody.Builder()
            .add("client_id", configuration.clientId)
            .add("token", token)
            .build()

        val request = Request.Builder()
            .url(endpoints.revocationEndpoint)
            .post(formBody)
            .build()

        return configuration.performRequestNonJson(request)
    }

    suspend fun introspectToken(
        tokenType: TokenType,
        token: String,
    ): OidcClientResult<OidcIntrospectInfo> {
        val tokenTypeHint: String = when (tokenType) {
            TokenType.ACCESS_TOKEN -> {
                "access_token"
            }
            TokenType.REFRESH_TOKEN -> {
                "refresh_token"
            }
            TokenType.ID_TOKEN -> {
                "id_token"
            }
            TokenType.DEVICE_SECRET -> {
                "device_secret"
            }
        }

        val formBody = FormBody.Builder()
            .add("client_id", configuration.clientId)
            .add("token", token)
            .add("token_type_hint", tokenTypeHint)
            .build()

        val request = Request.Builder()
            .url(endpoints.introspectionEndpoint)
            .post(formBody)
            .build()

        return configuration.performRequest<JsonObject, OidcIntrospectInfo>(request) {
            val active = (it["active"] as JsonPrimitive).boolean
            OidcIntrospectInfo(it, active)
        }
    }
}
