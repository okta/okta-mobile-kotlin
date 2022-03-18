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
import com.okta.authfoundation.credential.Token
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
        fun OidcClient.createAuthorizationCodeFlow(): AuthorizationCodeFlow {
            return AuthorizationCodeFlow(this)
        }
    }

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
        internal val nonce: String,
    )

    /**
     * Used in a [OidcClientResult.Error.exception] from [resume].
     *
     * Includes a message giving more information as to what went wrong.
     */
    class ResumeException internal constructor(message: String) : Exception(message)

    /**
     * Used in a [OidcClientResult.Error.exception] from [resume].
     *
     * The redirect scheme of the [Uri] didn't match the one configured for the associated [OidcClient].
     */
    class RedirectSchemeMismatchException internal constructor() : Exception()

    /**
     * Used in a [OidcClientResult.Error.exception] from [resume].
     *
     * An error representing an irrecoverable error where we didn't get the `code` back in order to complete the Authorization Code
     * Flow.
     */
    class MissingResultCodeException internal constructor() : Exception()

    /**
     * Initiates the OIDC Authorization Code redirect flow.
     *
     * See [AuthorizationCodeFlow.resume] for completing the flow.
     *
     * @param extraRequestParameters the extra key value pairs to send to the authorize endpoint.
     *  See [Authorize Documentation](https://developer.okta.com/docs/reference/api/oidc/#authorize) for parameter options.
     * @param scopes the scopes to request during sign in. Defaults to the configured [OidcClient] [OidcConfiguration.defaultScopes].
     */
    suspend fun start(
        extraRequestParameters: Map<String, String> = emptyMap(),
        scopes: Set<String> = oidcClient.configuration.defaultScopes,
    ): OidcClientResult<Context> {
        return start(
            codeVerifier = PkceGenerator.codeVerifier(),
            state = UUID.randomUUID().toString(),
            nonce = UUID.randomUUID().toString(),
            extraRequestParameters = extraRequestParameters,
            scopes = scopes
        )
    }

    internal suspend fun start(
        codeVerifier: String,
        state: String,
        nonce: String,
        extraRequestParameters: Map<String, String>,
        scopes: Set<String>,
    ): OidcClientResult<Context> {
        val endpoint = oidcClient.endpointsOrNull()?.authorizationEndpoint ?: return oidcClient.endpointNotAvailableError()

        val urlBuilder = endpoint.newBuilder()

        for (entry in extraRequestParameters.entries) {
            urlBuilder.addQueryParameter(entry.key, entry.value)
        }

        urlBuilder.addQueryParameter("code_challenge", PkceGenerator.codeChallenge(codeVerifier))
        urlBuilder.addQueryParameter("code_challenge_method", PkceGenerator.CODE_CHALLENGE_METHOD)
        urlBuilder.addQueryParameter("client_id", oidcClient.configuration.clientId)
        urlBuilder.addQueryParameter("scope", scopes.joinToString(" "))
        urlBuilder.addQueryParameter("redirect_uri", oidcClient.configuration.signInRedirectUri)
        urlBuilder.addQueryParameter("response_type", "code")
        urlBuilder.addQueryParameter("state", state)
        urlBuilder.addQueryParameter("nonce", nonce)

        return OidcClientResult.Success(Context(urlBuilder.build(), codeVerifier, state, nonce))
    }

    /**
     * Resumes the OIDC Authorization Code redirect flow.
     * This method takes the returned redirect [Uri], and communicates with the Authorization Server to exchange that for a token.
     *
     * @param uri the redirect [Uri] which includes the authorization code to complete the flow.
     * @param flowContext the [AuthorizationCodeFlow.Context] used internally to maintain state.
     */
    suspend fun resume(uri: Uri, flowContext: Context): OidcClientResult<Token> {
        if (!uri.toString().startsWith(oidcClient.configuration.signInRedirectUri)) {
            return OidcClientResult.Error(RedirectSchemeMismatchException())
        }

        val errorQueryParameter = uri.getQueryParameter("error")
        if (errorQueryParameter != null) {
            val errorDescription = uri.getQueryParameter("error_description") ?: "An error occurred."
            return OidcClientResult.Error(ResumeException(errorDescription))
        }

        val stateQueryParameter = uri.getQueryParameter("state")
        if (flowContext.state != stateQueryParameter) {
            val error = "Failed due to state mismatch."
            return OidcClientResult.Error(ResumeException(error))
        }

        val code = uri.getQueryParameter("code") ?: return OidcClientResult.Error(MissingResultCodeException())

        val endpoints = oidcClient.endpointsOrNull() ?: return oidcClient.endpointNotAvailableError()

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

        return oidcClient.tokenRequest(request, flowContext.nonce)
    }
}
