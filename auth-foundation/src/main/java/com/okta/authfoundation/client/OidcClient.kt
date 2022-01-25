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

import com.okta.authfoundation.dto.OidcIntrospectInfo
import com.okta.authfoundation.dto.OidcTokenType
import com.okta.authfoundation.dto.OidcTokens
import com.okta.authfoundation.dto.OidcUserInfo
import com.okta.authfoundation.util.performRequest
import com.okta.authfoundation.util.performRequestNonJson
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request

class OidcClient internal constructor(
    val configuration: OidcConfiguration,
    val endpoints: OidcEndpoints,
) {
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

    suspend fun getUserInfo(accessToken: String): OidcClientResult<OidcUserInfo> {
        val request = Request.Builder()
            .addHeader("authorization", "Bearer $accessToken")
            .url(endpoints.userInfoEndpoint)
            .build()

        return configuration.performRequest<JsonObject, OidcUserInfo>(request) {
            OidcUserInfo(it)
        }
    }

    suspend fun refreshToken(refreshToken: String): OidcClientResult<OidcTokens> {
        val formBody = FormBody.Builder()
            .add("client_id", configuration.clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("scope", configuration.scopes.joinToString(separator = " "))
            .build()

        val request = Request.Builder()
            .url(endpoints.tokenEndpoint)
            .post(formBody)
            .build()

        return configuration.performRequest(request)
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
        tokenType: OidcTokenType,
        token: String,
    ): OidcClientResult<OidcIntrospectInfo> {
        val tokenTypeHint: String = when (tokenType) {
            OidcTokenType.ACCESS_TOKEN -> {
                "access_token"
            }
            OidcTokenType.REFRESH_TOKEN -> {
                "refresh_token"
            }
            OidcTokenType.ID_TOKEN -> {
                "id_token"
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

    suspend fun storeTokens(tokens: OidcTokens) {
        configuration.storeTokens(tokens)
    }

    suspend fun getTokens(): OidcTokens? {
        return configuration.getTokens()
    }

    private suspend fun OidcConfiguration.getTokens(): OidcTokens? {
        return withContext(storageDispatcher) {
            val tokenJson = storage.get("token_json") ?: return@withContext null
            return@withContext json.decodeFromString<OidcTokens>(tokenJson)
        }
    }

    private suspend fun OidcConfiguration.storeTokens(tokens: OidcTokens) {
        withContext(storageDispatcher) {
            val json = json.encodeToString(tokens)
            storage.save("token_json", json)
        }
    }
}
