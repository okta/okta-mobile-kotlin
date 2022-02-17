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
import com.okta.authfoundation.client.internal.endpointsOrNull
import com.okta.authfoundation.client.internal.internalTokenRequest
import com.okta.authfoundation.credential.Token as CredentialToken
import okhttp3.FormBody
import okhttp3.Request

// https://openid.net/specs/openid-connect-native-sso-1_0.html
class TokenExchangeFlow private constructor(
    private val oidcClient: OidcClient,
) {
    companion object {
        fun OidcClient.tokenExchangeFlow(): TokenExchangeFlow {
            return TokenExchangeFlow(this)
        }
    }

    sealed class Result {
        class Error internal constructor(val message: String, val exception: Exception? = null) : Result()
        class Token internal constructor(val token: CredentialToken) : Result()
    }

    suspend fun start(
        idToken: String,
        deviceSecret: String,
        audience: String = "api://default",
        scopes: Set<String> = oidcClient.configuration.defaultScopes,
    ): Result {
        val endpoints = oidcClient.endpointsOrNull() ?: return Result.Error("Endpoints not available.")

        val formBodyBuilder = FormBody.Builder()
            .add("audience", audience)
            .add("subject_token_type", "urn:ietf:params:oauth:token-type:id_token")
            .add("subject_token", idToken)
            .add("actor_token_type", "urn:x-oath:params:oauth:token-type:device-secret")
            .add("actor_token", deviceSecret)
            .add("client_id", oidcClient.configuration.clientId)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
            .add("scope", scopes.joinToString(" "))

        val request = Request.Builder()
            .post(formBodyBuilder.build())
            .url(endpoints.tokenEndpoint)
            .build()

        return when (val tokenResult = oidcClient.internalTokenRequest(request)) {
            is OidcClientResult.Error -> {
                Result.Error("Token request failed.", tokenResult.exception)
            }
            is OidcClientResult.Success -> {
                Result.Token(tokenResult.result)
            }
        }
    }
}
