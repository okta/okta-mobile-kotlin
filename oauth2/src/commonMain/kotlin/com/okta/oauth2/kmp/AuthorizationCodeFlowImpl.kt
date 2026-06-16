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
package com.okta.oauth2.kmp

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.oauth2.PkceGenerator
import com.okta.oauth2.internal.generateUuid
import com.okta.oauth2.internal.parseQueryParameter
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom

@OptIn(InternalAuthFoundationApi::class)
internal class AuthorizationCodeFlowImpl(
    private val client: OAuth2Client,
) : AuthorizationCodeFlow {
    override suspend fun start(
        redirectUrl: String,
        extraRequestParameters: Map<String, String>,
        scope: String?,
    ): Result<AuthorizationCodeFlowContext> =
        runCatching {
            val endpoints =
                client.endpointsOrNull()
                    ?: throw IllegalStateException("OIDC Endpoints not available.")
            val authorizationEndpoint =
                endpoints.authorizationEndpoint
                    ?: throw IllegalStateException("Authorization endpoint not available.")

            val codeVerifier = PkceGenerator.codeVerifier()
            val state = generateUuid()
            val nonce = generateUuid()
            val maxAge = extraRequestParameters["max_age"]?.toIntOrNull()

            val urlBuilder = URLBuilder().takeFrom(authorizationEndpoint)

            for ((key, value) in extraRequestParameters) {
                urlBuilder.parameters.append(key, value)
            }

            urlBuilder.parameters.append("code_challenge", PkceGenerator.codeChallenge(codeVerifier))
            urlBuilder.parameters.append("code_challenge_method", PkceGenerator.CODE_CHALLENGE_METHOD)
            urlBuilder.parameters.append("client_id", client.configuration.clientId)
            urlBuilder.parameters.append("scope", scope ?: client.configuration.defaultScope)
            urlBuilder.parameters.append("redirect_uri", redirectUrl)
            urlBuilder.parameters.append("response_type", "code")
            urlBuilder.parameters.append("state", state)
            urlBuilder.parameters.append("nonce", nonce)

            AuthorizationCodeFlowContext(
                url = urlBuilder.buildString(),
                redirectUrl = redirectUrl,
                codeVerifier = codeVerifier,
                state = state,
                nonce = nonce,
                maxAge = maxAge
            )
        }

    override suspend fun resume(
        uri: String,
        flowContext: AuthorizationCodeFlowContext,
    ): Result<TokenInfo> =
        runCatching {
            if (!uri.startsWith(flowContext.redirectUrl)) {
                throw AuthorizationCodeFlow.RedirectSchemeMismatchException()
            }

            val error = parseQueryParameter(uri, "error")
            if (error != null) {
                val errorDescription = parseQueryParameter(uri, "error_description") ?: "An error occurred."
                throw AuthorizationCodeFlow.ResumeException(errorDescription, error)
            }

            val stateParam = parseQueryParameter(uri, "state")
            if (flowContext.state != stateParam) {
                throw AuthorizationCodeFlow.ResumeException("Failed due to state mismatch.", "state_mismatch")
            }

            val code = parseQueryParameter(uri, "code") ?: throw AuthorizationCodeFlow.MissingResultCodeException()

            val formParams =
                mapOf(
                    "redirect_uri" to flowContext.redirectUrl,
                    "code_verifier" to flowContext.codeVerifier,
                    "client_id" to client.configuration.clientId,
                    "grant_type" to "authorization_code",
                    "code" to code
                )

            client
                .tokenRequest(
                    formParams = formParams,
                    nonce = flowContext.nonce,
                    maxAge = flowContext.maxAge
                ).getOrThrow()
        }
}
