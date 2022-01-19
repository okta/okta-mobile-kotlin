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
package com.okta.oauth2

import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.client.internal.internalTokenRequest
import com.okta.authfoundation.dto.OidcTokens
import okhttp3.FormBody
import okhttp3.Request

class ResourceOwnerFlow(
    private val oidcClient: OidcClient,
) {
    sealed class Result {
        class Error(val message: String, val exception: Exception? = null) : Result()
        class Tokens(val tokens: OidcTokens) : Result()
    }

    suspend fun start(username: String, password: String): Result {
        val formBodyBuilder = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .add("client_id", oidcClient.configuration.clientId)
            .add("grant_type", "password")
            .add("scope", oidcClient.configuration.scopes.joinToString(" "))

        val request = Request.Builder()
            .post(formBodyBuilder.build())
            .url(oidcClient.endpoints.tokenEndpoint)
            .build()

        return when (val tokenResult = oidcClient.internalTokenRequest(request)) {
            is OidcClientResult.Error -> {
                Result.Error("Token request failed.", tokenResult.exception)
            }
            is OidcClientResult.Success -> {
                Result.Tokens(tokenResult.result)
            }
        }
    }
}
