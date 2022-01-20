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
package com.okta.oauth2

import android.net.Uri
import com.okta.oauth2.events.CustomizeAuthorizationUrlEvent
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.client.internal.internalTokenRequest
import com.okta.authfoundation.dto.OidcTokens
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request
import java.util.UUID

class AuthorizationCodeFlow private constructor(
    private val oidcClient: OidcClient,
) {
    companion object {
        fun OidcClient.authorizationCodeFlow(): AuthorizationCodeFlow {
            return AuthorizationCodeFlow(this)
        }
    }

    class Context internal constructor(
        internal val codeVerifier: String,
        internal val state: String,
        val url: HttpUrl,
    )

    sealed class Result {
        object RedirectSchemeMismatch : Result()
        class Error(val message: String, val exception: Exception? = null) : Result()
        object MissingResultCode : Result()
        class Tokens(val tokens: OidcTokens) : Result()
    }

    fun start(): Context {
        return start(PkceGenerator.codeVerifier(), UUID.randomUUID().toString())
    }

    internal fun start(
        codeVerifier: String,
        state: String,
    ): Context {
        val urlBuilder = oidcClient.endpoints.authorizationEndpoint.newBuilder()
        urlBuilder.addQueryParameter("code_challenge", PkceGenerator.codeChallenge(codeVerifier))
        urlBuilder.addQueryParameter("code_challenge_method", PkceGenerator.CODE_CHALLENGE_METHOD)
        urlBuilder.addQueryParameter("client_id", oidcClient.configuration.clientId)
        urlBuilder.addQueryParameter("scope", oidcClient.configuration.scopes.joinToString(" "))
        urlBuilder.addQueryParameter("redirect_uri", oidcClient.configuration.signInRedirectUri)
        urlBuilder.addQueryParameter("response_type", "code")
        urlBuilder.addQueryParameter("state", state)

        val event = CustomizeAuthorizationUrlEvent(urlBuilder)
        oidcClient.configuration.eventCoordinator.sendEvent(event)

        return Context(codeVerifier, state, urlBuilder.build())
    }

    suspend fun resume(uri: Uri, flowContext: Context): Result {
        if (!uri.toString().startsWith(oidcClient.configuration.signInRedirectUri)) {
            return Result.RedirectSchemeMismatch
        }

        val errorQueryParameter = uri.getQueryParameter("error")
        if (errorQueryParameter != null) {
            val errorDescription = uri.getQueryParameter("error_description") ?: "An error occurred."
            return Result.Error(errorDescription)
        }

        val stateQueryParameter = uri.getQueryParameter("state")
        if (flowContext.state != stateQueryParameter) {
            val error = "Failed due to state mismatch."
            return Result.Error(error)
        }

        val code = uri.getQueryParameter("code") ?: return Result.MissingResultCode

        val formBodyBuilder = FormBody.Builder()
            .add("redirect_uri", oidcClient.configuration.signInRedirectUri)
            .add("code_verifier", flowContext.codeVerifier)
            .add("client_id", oidcClient.configuration.clientId)
            .add("grant_type", "authorization_code")
            .add("code", code)

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
