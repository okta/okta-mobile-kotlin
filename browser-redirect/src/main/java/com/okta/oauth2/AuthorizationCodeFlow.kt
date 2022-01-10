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
import com.okta.oidc.OktaSdk
import com.okta.oauth2.events.CustomizeAuthorizationUrlEvent
import com.okta.oidc.kotlin.client.OidcClient
import com.okta.oidc.kotlin.client.OidcClientResult
import com.okta.oidc.kotlin.client.internal.internalTokenRequest
import com.okta.oidc.kotlin.dto.OidcTokens
import com.okta.oidc.kotlin.events.EventCoordinator
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request
import java.util.UUID

class AuthorizationCodeFlow(
    private val configuration: Configuration,
    private val oidcClient: OidcClient,
    private val eventCoordinator: EventCoordinator = OktaSdk.eventCoordinator,
) {
    class Configuration(
        /** The application's redirect URI. */
        val redirectUri: String,

        /** The application's end session redirect URI. */
        val endSessionRedirectUri: String,
    )

    class Context internal constructor(
        internal val codeVerifier: String,
        internal val state: String,
        internal val redirectUri: String,
        val url: HttpUrl,
    )

    sealed class Result {
        object RedirectSchemeMismatch : Result()
        class Error(val message: String, val exception: Exception? = null) : Result()
        object MissingResultCode : Result()
        class Tokens(val tokens: OidcTokens) : Result()
    }

    fun start(): Context {
        return start(configuration.redirectUri)
    }

    internal fun start(
        redirectUri: String,
        codeVerifier: String = PkceGenerator.codeVerifier(),
        state: String = UUID.randomUUID().toString(),
    ): Context {
        val urlBuilder = oidcClient.endpoints.authorizationEndpoint.newBuilder()
        urlBuilder.addQueryParameter("code_challenge", PkceGenerator.codeChallenge(codeVerifier))
        urlBuilder.addQueryParameter("code_challenge_method", PkceGenerator.CODE_CHALLENGE_METHOD)
        urlBuilder.addQueryParameter("client_id", oidcClient.configuration.clientId)
        urlBuilder.addQueryParameter("scope", oidcClient.configuration.scopes.joinToString(" "))
        urlBuilder.addQueryParameter("redirect_uri", redirectUri)
        urlBuilder.addQueryParameter("response_type", "code")
        urlBuilder.addQueryParameter("state", state)

        val event = CustomizeAuthorizationUrlEvent(urlBuilder)
        eventCoordinator.sendEvent(event)

        return Context(codeVerifier, state, configuration.redirectUri, urlBuilder.build())
    }

    suspend fun resume(uri: Uri, context: Context): Result {
        if (!uri.toString().startsWith(context.redirectUri)) {
            return Result.RedirectSchemeMismatch
        }

        val errorQueryParameter = uri.getQueryParameter("error")
        if (errorQueryParameter != null) {
            val errorDescription = uri.getQueryParameter("error_description") ?: "An error occurred."
            return Result.Error(errorDescription)
        }

        val stateQueryParameter = uri.getQueryParameter("state")
        if (context.state != stateQueryParameter) {
            val error = "Failed due to state mismatch."
            return Result.Error(error)
        }

        val code = uri.getQueryParameter("code") ?: return Result.MissingResultCode

        val formBodyBuilder = FormBody.Builder()
            .add("redirect_uri", context.redirectUri)
            .add("code_verifier", context.codeVerifier)
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
