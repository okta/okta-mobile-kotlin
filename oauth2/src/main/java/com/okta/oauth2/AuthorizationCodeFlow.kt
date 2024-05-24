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
import com.okta.authfoundation.client.OAuth2Client
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.client.internal.SdkVersionsRegistry
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
class AuthorizationCodeFlow(
    private val client: OAuth2Client,
) {
    companion object {
        init {
            SdkVersionsRegistry.register(SDK_VERSION)
        }
    }

    /**
     * Initializes an authorization code flow.
     */
    constructor() : this(OAuth2Client.default)

    /**
     * Initializes an authorization code flow using the [OidcConfiguration].
     *
     * @param oidcConfiguration the [OidcConfiguration] specifying the authorization servers.
     */
    constructor(oidcConfiguration: OidcConfiguration) : this(OAuth2Client.createFromConfiguration(oidcConfiguration))

    /**
     * A model representing the context and current state for an authorization session.
     */
    class Context internal constructor(
        /**
         * The current authentication Url.
         */
        val url: HttpUrl,
        internal val redirectUrl: String,
        internal val codeVerifier: String,
        internal val state: String,
        internal val nonce: String,
        internal val maxAge: Int?
    )

    /**
     * Used in a [OAuth2ClientResult.Error.exception] from [resume].
     */
    class ResumeException internal constructor(
        message: String,

        /**
         * The error returned from the server.
         *
         * See [developer.okta.com](https://developer.okta.com/docs/reference/api/oidc/#possible-errors).
         */
        val errorId: String,
    ) : Exception(message)

    /**
     * Used in a [OAuth2ClientResult.Error.exception] from [resume].
     *
     * The redirect scheme of the [Uri] didn't match the one configured for the associated [OAuth2Client].
     */
    class RedirectSchemeMismatchException internal constructor() : Exception()

    /**
     * Used in a [OAuth2ClientResult.Error.exception] from [resume].
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
     * @param redirectUrl the redirect URL.
     * @param extraRequestParameters the extra key value pairs to send to the authorize endpoint.
     *  See [Authorize Documentation](https://developer.okta.com/docs/reference/api/oidc/#authorize) for parameter options.
     * @param scope the scopes to request during sign in. Defaults to the configured [OAuth2Client] [OidcConfiguration.defaultScope].
     */
    suspend fun start(
        redirectUrl: String,
        extraRequestParameters: Map<String, String> = emptyMap(),
        scope: String = client.configuration.defaultScope,
        state: String = UUID.randomUUID().toString()
    ): OAuth2ClientResult<Context> {
        return start(
            redirectUrl = redirectUrl,
            codeVerifier = PkceGenerator.codeVerifier(),
            state = state,
            nonce = UUID.randomUUID().toString(),
            extraRequestParameters = extraRequestParameters,
            scope = scope
        )
    }

    internal suspend fun start(
        redirectUrl: String,
        codeVerifier: String,
        state: String,
        nonce: String,
        extraRequestParameters: Map<String, String>,
        scope: String,
    ): OAuth2ClientResult<Context> {
        val endpoint = client.endpointsOrNull()?.authorizationEndpoint ?: return client.endpointNotAvailableError()

        val urlBuilder = endpoint.newBuilder()

        for (entry in extraRequestParameters.entries) {
            urlBuilder.addQueryParameter(entry.key, entry.value)
        }

        val maxAge = extraRequestParameters["max_age"]?.toIntOrNull()

        urlBuilder.addQueryParameter("code_challenge", PkceGenerator.codeChallenge(codeVerifier))
        urlBuilder.addQueryParameter("code_challenge_method", PkceGenerator.CODE_CHALLENGE_METHOD)
        urlBuilder.addQueryParameter("client_id", client.configuration.clientId)
        urlBuilder.addQueryParameter("scope", scope)
        urlBuilder.addQueryParameter("redirect_uri", redirectUrl)
        urlBuilder.addQueryParameter("response_type", "code")
        urlBuilder.addQueryParameter("state", state)
        urlBuilder.addQueryParameter("nonce", nonce)

        return OAuth2ClientResult.Success(Context(urlBuilder.build(), redirectUrl, codeVerifier, state, nonce, maxAge))
    }

    /**
     * Resumes the OIDC Authorization Code redirect flow.
     * This method takes the returned redirect [Uri], and communicates with the Authorization Server to exchange that for a token.
     *
     * @param uri the redirect [Uri] which includes the authorization code to complete the flow.
     * @param flowContext the [AuthorizationCodeFlow.Context] used internally to maintain state.
     */
    suspend fun resume(uri: Uri, flowContext: Context): OAuth2ClientResult<Token> {
        if (!uri.toString().startsWith(flowContext.redirectUrl)) {
            return OAuth2ClientResult.Error(RedirectSchemeMismatchException())
        }

        val errorQueryParameter = uri.getQueryParameter("error")
        if (errorQueryParameter != null) {
            val errorDescription = uri.getQueryParameter("error_description") ?: "An error occurred."
            return OAuth2ClientResult.Error(ResumeException(errorDescription, errorQueryParameter))
        }

        val stateQueryParameter = uri.getQueryParameter("state")
        if (flowContext.state != stateQueryParameter) {
            val error = "Failed due to state mismatch."
            return OAuth2ClientResult.Error(ResumeException(error, "state_mismatch"))
        }

        val code = uri.getQueryParameter("code") ?: return OAuth2ClientResult.Error(MissingResultCodeException())

        val endpoints = client.endpointsOrNull() ?: return client.endpointNotAvailableError()

        val formBodyBuilder = FormBody.Builder()
            .add("redirect_uri", flowContext.redirectUrl)
            .add("code_verifier", flowContext.codeVerifier)
            .add("client_id", client.configuration.clientId)
            .add("grant_type", "authorization_code")
            .add("code", code)

        val request = Request.Builder()
            .post(formBodyBuilder.build())
            .url(endpoints.tokenEndpoint)
            .build()

        return client.tokenRequest(request, flowContext.nonce, flowContext.maxAge)
    }
}
