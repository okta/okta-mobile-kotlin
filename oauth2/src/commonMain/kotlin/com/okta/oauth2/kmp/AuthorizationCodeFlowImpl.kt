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
import com.okta.authfoundation.api.http.ApiFormRequest
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.oauth2.PkceGenerator
import com.okta.oauth2.internal.generateUuid
import com.okta.oauth2.internal.parseQueryParameter
import com.okta.oauth2.kmp.internal.ParAuthorizationErrorResponse
import com.okta.oauth2.kmp.internal.ParAuthorizationRequest
import com.okta.oauth2.kmp.internal.ParAuthorizationResponse
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json

@OptIn(InternalAuthFoundationApi::class)
internal class AuthorizationCodeFlowImpl(
    override val client: OAuth2Client,
) : AuthorizationCodeFlow {
    override suspend fun start(
        redirectUrl: String,
        scope: List<String>,
        extraRequestParameters: Map<String, String>,
    ): Result<AuthorizationCodeFlowContext> =
        runCatching {
            val endpoints = client.endpointsOrThrow()
            val authorizationEndpoint =
                endpoints.authorizationEndpoint
                    ?: throw IllegalStateException("Authorization endpoint not available.")

            val codeVerifier = PkceGenerator.codeVerifier()
            val state = generateUuid()
            val nonce = generateUuid()
            val maxAge = extraRequestParameters["max_age"]?.toIntOrNull()
            val authorizationRequestParams =
                buildAuthorizationRequestParameters(
                    redirectUrl = redirectUrl,
                    scope = scope,
                    extraRequestParameters = extraRequestParameters,
                    codeVerifier = codeVerifier,
                    state = state,
                    nonce = nonce
                )
            val requiresPar = endpoints.requirePushedAuthorizationRequests
            val parEndpoint = endpoints.pushedAuthorizationRequestEndpoint
            val canUsePar = client.configuration.enablePushedAuthorizationRequests || requiresPar
            val shouldFallbackOnParFailure =
                !requiresPar && client.configuration.allowPushedAuthorizationRequestFallback

            val (authorizationUrl, requestUri, usedPar) =
                if (parEndpoint != null && canUsePar) {
                    // Computed outside the PAR runCatching below so a failing clientAssertionProvider
                    // (e.g. an invalidated signing key) surfaces as its own failure instead of being
                    // misclassified as a PAR request failure and silently falling back.
                    val clientAuthParams = client.configuration.clientAuthenticationFormParameters(parEndpoint)
                    val pushedRequest =
                        runCatching {
                            performPushedAuthorizationRequest(
                                endpoint = parEndpoint,
                                formParams = authorizationRequestParams + clientAuthParams,
                                json = client.configuration.json
                            )
                        }
                    // runCatching also catches CancellationException; rethrow it so a cancelled
                    // caller doesn't observe a fabricated successful fallback result.
                    currentCoroutineContext().ensureActive()
                    if (pushedRequest.isSuccess) {
                        val parResponse = pushedRequest.getOrThrow()
                        Triple(
                            buildParAuthorizationUrl(
                                authorizationEndpoint = authorizationEndpoint,
                                requestUri = parResponse.requestUri
                            ),
                            parResponse.requestUri,
                            true
                        )
                    } else if (shouldFallbackOnParFailure) {
                        Triple(buildAuthorizationUrl(authorizationEndpoint, authorizationRequestParams), null, false)
                    } else {
                        val message =
                            "Pushed Authorization Request failed and fallback is not allowed."
                        if (requiresPar) {
                            throw AuthorizationCodeFlow.PushedAuthorizationRequiredException(message, pushedRequest.exceptionOrNull())
                        }
                        throw AuthorizationCodeFlow.PushedAuthorizationRequestException(message, pushedRequest.exceptionOrNull())
                    }
                } else {
                    if (requiresPar) {
                        throw AuthorizationCodeFlow.PushedAuthorizationRequiredException(
                            "Authorization server requires PAR, but no pushed_authorization_request_endpoint was discovered."
                        )
                    }
                    Triple(buildAuthorizationUrl(authorizationEndpoint, authorizationRequestParams), null, false)
                }

            AuthorizationCodeFlowContext(
                url = authorizationUrl,
                redirectUrl = redirectUrl,
                usedPushedAuthorizationRequest = usedPar,
                pushedAuthorizationRequestUri = requestUri,
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

    private fun buildAuthorizationRequestParameters(
        redirectUrl: String,
        scope: List<String>,
        extraRequestParameters: Map<String, String>,
        codeVerifier: String,
        state: String,
        nonce: String,
    ): Map<String, String> =
        extraRequestParameters.toMutableMap().apply {
            this["code_challenge"] = PkceGenerator.codeChallenge(codeVerifier)
            this["code_challenge_method"] = PkceGenerator.CODE_CHALLENGE_METHOD
            this["client_id"] = client.configuration.clientId
            this["scope"] = scope.joinToString(" ")
            this["redirect_uri"] = redirectUrl
            this["response_type"] = "code"
            this["state"] = state
            this["nonce"] = nonce
        }

    private fun buildAuthorizationUrl(
        authorizationEndpoint: String,
        authorizationRequestParams: Map<String, String>,
    ): String {
        val urlBuilder = URLBuilder().takeFrom(authorizationEndpoint)
        for ((key, value) in authorizationRequestParams) {
            urlBuilder.parameters.append(key, value)
        }
        return urlBuilder.buildString()
    }

    private fun buildParAuthorizationUrl(
        authorizationEndpoint: String,
        requestUri: String,
    ): String {
        val urlBuilder = URLBuilder().takeFrom(authorizationEndpoint)
        urlBuilder.parameters.append("client_id", client.configuration.clientId)
        urlBuilder.parameters.append("request_uri", requestUri)
        return urlBuilder.buildString()
    }

    private suspend fun performPushedAuthorizationRequest(
        endpoint: String,
        formParams: Map<String, String>,
        json: Json,
    ): ParAuthorizationResponse {
        val request: ApiFormRequest = ParAuthorizationRequest(endpoint, formParams)
        val response =
            client.configuration.apiExecutor
                .execute(request)
                .getOrThrow()
        val body = response.body?.decodeToString().orEmpty()
        if (response.statusCode !in 200..299) {
            val message = parseParErrorMessage(json, body, response.statusCode)
            throw IllegalStateException(message)
        }
        val parResponse =
            runCatching {
                json.decodeFromString<ParAuthorizationResponse>(body)
            }.getOrElse { error ->
                throw IllegalStateException("Failed to parse PAR response.", error)
            }
        if (parResponse.requestUri.isBlank()) {
            throw IllegalStateException("PAR response did not include request_uri.")
        }
        return parResponse
    }

    private fun parseParErrorMessage(
        json: Json,
        body: String,
        statusCode: Int,
    ): String {
        val defaultMessage = "PAR request failed with HTTP $statusCode."
        if (body.isBlank()) return defaultMessage
        val errorResponse = runCatching { json.decodeFromString<ParAuthorizationErrorResponse>(body) }.getOrNull()
        val description = errorResponse?.errorDescription?.takeIf { it.isNotBlank() }
        val error = errorResponse?.error?.takeIf { it.isNotBlank() }
        val detail = description ?: error ?: return defaultMessage
        return "PAR request failed with HTTP $statusCode: $detail"
    }
}
