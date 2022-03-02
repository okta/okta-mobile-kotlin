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
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.credential.Token as CredentialToken
import com.okta.oauth2.events.CustomizeAuthorizationUrlEvent
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request
import java.util.UUID

/**
 * [AuthorizationCodeFlow] encapsulates the behavior required to authentication using an OIDC Browser redirect flow.
 *
 * See [Authorization Code Flow documentation](https://developer.okta.com/docs/guides/implement-grant-type/authcodepkce/main/#about-the-authorization-code-grant-with-pkce)
 */
class AuthorizationCodeFlow private constructor(
    private val oidcClient: OidcClient,
) {
    companion object {
        /**
         * Initializes an authorization code flow using the [OidcClient].
         *
         * @receiver the [OidcClient] used to perform the low level OIDC requests, as well as with which to use the configuration from.
         */
        fun OidcClient.authorizationCodeFlow(): AuthorizationCodeFlow {
            return AuthorizationCodeFlow(this)
        }
    }

    /**
     * A model representing all the possible states of a [AuthorizationCodeFlow.resume] call.
     */
    sealed class ResumeResult {
        /**
         * An error that occurs when the [OidcClient] hasn't been properly configured, or a network error occurs.
         */
        object EndpointsNotAvailable : ResumeResult()

        /**
         * A model representing the context and current state for an authorization session.
         */
        class Context internal constructor(
            /**
             * The current authentication Url.
             */
            val url: HttpUrl,
            internal val codeVerifier: String,
            internal val state: String,
        ) : ResumeResult()
    }

    /**
     * A model representing all possible states of a [AuthorizationCodeFlow.resume] call.
     */
    sealed class Result {
        /**
         * An error indicating the redirect scheme of the supplied url doesn't match the configured redirect scheme.
         *
         * This could be due to the supplied url being intended for another feature of the app, a misconfiguration, or an attempted
         * attack.
         */
        object RedirectSchemeMismatch : Result()

        /**
         * An error resulting from an interaction with the Authorization Server.
         */
        class Error internal constructor(
            /**
             * An error message intended to be displayed to the user.
             */
            val message: String,
            /**
             * The exception, if available which caused the error.
             */
            val exception: Exception? = null,
        ) : Result()

        /**
         * An error representing an irrecoverable error where we didn't get the `code` back in order to complete the Authorization Code
         * Flow.
         */
        object MissingResultCode : Result()

        /**
         * Represents a successful authentication, and contains the [CredentialToken] returned.
         */
        class Token internal constructor(
            /**
             * The [CredentialToken] representing the user logged in via the [AuthorizationCodeFlow].
             */
            val token: CredentialToken,
        ) : Result()
    }

    /**
     * Initiates the OIDC Authorization Code redirect flow.
     *
     * See [AuthorizationCodeFlow.resume] for completing the flow.
     *
     * @param scopes the scopes to request during sign in. Defaults to the configured [OidcClient] [OidcConfiguration.defaultScopes].
     */
    suspend fun start(
        scopes: Set<String> = oidcClient.configuration.defaultScopes,
    ): ResumeResult {
        return start(PkceGenerator.codeVerifier(), UUID.randomUUID().toString(), scopes)
    }

    internal suspend fun start(
        codeVerifier: String,
        state: String,
        scopes: Set<String>
    ): ResumeResult {
        val endpoints = oidcClient.endpointsOrNull() ?: return ResumeResult.EndpointsNotAvailable

        val urlBuilder = endpoints.authorizationEndpoint.newBuilder()
        urlBuilder.addQueryParameter("code_challenge", PkceGenerator.codeChallenge(codeVerifier))
        urlBuilder.addQueryParameter("code_challenge_method", PkceGenerator.CODE_CHALLENGE_METHOD)
        urlBuilder.addQueryParameter("client_id", oidcClient.configuration.clientId)
        urlBuilder.addQueryParameter("scope", scopes.joinToString(" "))
        urlBuilder.addQueryParameter("redirect_uri", oidcClient.configuration.signInRedirectUri)
        urlBuilder.addQueryParameter("response_type", "code")
        urlBuilder.addQueryParameter("state", state)

        val event = CustomizeAuthorizationUrlEvent(urlBuilder)
        oidcClient.configuration.eventCoordinator.sendEvent(event)

        return ResumeResult.Context(urlBuilder.build(), codeVerifier, state)
    }

    /**
     * Resumes the OIDC Authorization Code redirect flow.
     * This method takes the returned redirect [Uri], and communicates with the Authorization Server to exchange that for a token.
     *
     * @param uri the redirect [Uri] which includes the authorization code to complete the flow.
     * @param flowContext the [AuthorizationCodeFlow.ResumeResult.Context] used internally to maintain state.
     */
    suspend fun resume(uri: Uri, flowContext: ResumeResult.Context): Result {
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

        val endpoints = oidcClient.endpointsOrNull() ?: return Result.Error("Endpoints not available.")

        val formBodyBuilder = FormBody.Builder()
            .add("redirect_uri", oidcClient.configuration.signInRedirectUri)
            .add("code_verifier", flowContext.codeVerifier)
            .add("client_id", oidcClient.configuration.clientId)
            .add("grant_type", "authorization_code")
            .add("code", code)

        val request = Request.Builder()
            .post(formBodyBuilder.build())
            .url(endpoints.tokenEndpoint)
            .build()

        return when (val tokenResult = oidcClient.tokenRequest(request)) {
            is OidcClientResult.Error -> {
                Result.Error("Token request failed.", tokenResult.exception)
            }
            is OidcClientResult.Success -> {
                Result.Token(tokenResult.result)
            }
        }
    }
}
