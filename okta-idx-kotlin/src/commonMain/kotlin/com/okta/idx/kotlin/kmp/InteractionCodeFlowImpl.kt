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
package com.okta.idx.kotlin.kmp

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.idx.kotlin.kmp.v1.InteractContext
import com.okta.idx.kotlin.kmp.v1.InteractResponse
import com.okta.idx.kotlin.kmp.v1.Response
import com.okta.idx.kotlin.kmp.v1.asFormRequest
import com.okta.idx.kotlin.kmp.v1.asJsonRequest
import com.okta.idx.kotlin.kmp.v1.introspectRequest
import com.okta.idx.kotlin.kmp.v1.toIdxResponse
import com.okta.idx.kotlin.kmp.v1.tokenRequestFormParamsFromInteractionCode
import io.ktor.http.Url
import kotlinx.coroutines.withContext

/**
 * The sole implementation of [InteractionCodeFlow] — constructed only via [InteractionCodeFlow.invoke].
 */
@OptIn(InternalAuthFoundationApi::class)
internal class InteractionCodeFlowImpl(
    override val client: OAuth2Client,
    private val deviceTokenCookieHook: DeviceTokenCookieHook = defaultDeviceTokenCookieHook(),
) : InteractionCodeFlow {
    internal var flowContext: InteractionCodeFlowContext? = null
        private set

    override suspend fun start(
        redirectUri: String,
        extraStartRequestParameters: Map<String, String>,
    ): Result<Unit> =
        runCatching {
            val interactContext =
                withContext(client.configuration.computeDispatcher) {
                    InteractContext.create(
                        client = client,
                        redirectUrl = redirectUri,
                        extraParameters = extraStartRequestParameters
                    )
                } ?: throw OidcEndpointsNotAvailableException()

            val request = interactContext.toRequest(extraHeaders = deviceTokenHeaders())
            val response =
                client.configuration.apiExecutor
                    .execute(request)
                    .getOrThrow()
            if (response.statusCode !in 200..299) {
                throw IllegalStateException("Interact request failed with status ${response.statusCode}")
            }
            val body = response.body?.decodeToString() ?: throw IllegalStateException("Empty response body")
            val interactResponse = client.configuration.json.decodeFromString(InteractResponse.serializer(), body)

            flowContext =
                InteractionCodeFlowContext(
                    codeVerifier = interactContext.codeVerifier,
                    interactionHandle = interactResponse.interactionHandle,
                    state = interactContext.state,
                    redirectUrl = redirectUri,
                    nonce = interactContext.nonce,
                    maxAge = interactContext.maxAge
                )
        }

    override suspend fun resume(): Result<IdxResponse> =
        runCatching {
            val context = flowContext ?: throw InteractionCodeFlow.FlowNotStartedException()
            val request =
                withContext(client.configuration.computeDispatcher) {
                    introspectRequest(client, context, extraHeaders = deviceTokenHeaders())
                } ?: throw OidcEndpointsNotAvailableException()
            executeIdxRequest(request)
        }

    override suspend fun proceed(remediation: IdxRemediation): Result<IdxResponse> =
        runCatching {
            val extraHeaders = originHeaders() + deviceTokenHeaders()
            val request =
                withContext(client.configuration.computeDispatcher) {
                    remediation.asJsonRequest(client, extraHeaders)
                }
            executeIdxRequest(request).also { idxResponse ->
                idxResponse.remediations.firstOrNull()?.copyValuesFromPrevious(remediation)
            }
        }

    override suspend fun exchangeInteractionCodeForTokens(remediation: IdxRemediation): Result<TokenInfo> =
        runCatching {
            val context = flowContext ?: throw InteractionCodeFlow.FlowNotStartedException()
            if (remediation.type != IdxRemediation.Type.ISSUE) {
                throw InteractionCodeFlow.InvalidRemediationException()
            }
            val formParams =
                withContext(client.configuration.computeDispatcher) {
                    remediation["code_verifier"]?.value = context.codeVerifier
                    remediation.asFormRequest().formParameters().mapValues { (_, values) -> values.first() }
                }
            client.tokenRequest(formParams, context.nonce, context.maxAge).getOrThrow()
        }

    override suspend fun evaluateRedirectUri(uri: String): Result<IdxRedirectOutcome> =
        runCatching {
            val context = flowContext ?: throw InteractionCodeFlow.FlowNotStartedException()
            if (!uri.startsWith(context.redirectUrl)) {
                throw InteractionCodeFlow.RedirectUriMismatchException()
            }
            val parsedUri = Url(uri)
            val errorQueryParameter = parsedUri.parameters["error"]
            val stateQueryParameter = parsedUri.parameters["state"]

            if (errorQueryParameter == "interaction_required") {
                if (context.state != stateQueryParameter) {
                    throw InteractionCodeFlow.StateMismatchException()
                }
                return@runCatching IdxRedirectOutcome.InteractionRequired(resume().getOrThrow())
            }
            if (errorQueryParameter != null) {
                val errorDescription = parsedUri.parameters["error_description"] ?: "An error occurred."
                throw InteractionCodeFlow.RedirectErrorException(errorQueryParameter, errorDescription)
            }
            val interactionCodeQueryParameter = parsedUri.parameters["interaction_code"]
            if (interactionCodeQueryParameter != null) {
                if (context.state != stateQueryParameter) {
                    throw InteractionCodeFlow.StateMismatchException()
                }
                val formParams =
                    withContext(client.configuration.computeDispatcher) {
                        tokenRequestFormParamsFromInteractionCode(client, context, interactionCodeQueryParameter)
                    }
                val tokenInfo = client.tokenRequest(formParams, context.nonce, context.maxAge).getOrThrow()
                return@runCatching IdxRedirectOutcome.Tokens(tokenInfo)
            }
            throw InteractionCodeFlow.UnhandledRedirectException()
        }

    private suspend fun originHeaders(): Map<String, List<String>> {
        val issuer = client.endpointsOrNull()?.issuer ?: return emptyMap()
        val origin =
            runCatching {
                val url = Url(issuer)
                "${url.protocol.name}://${url.host}"
            }.getOrNull() ?: return emptyMap()
        return mapOf("Origin" to listOf(origin))
    }

    private suspend fun deviceTokenHeaders(): Map<String, List<String>> {
        val deviceToken = deviceTokenCookieHook.deviceToken() ?: return emptyMap()
        return mapOf("Cookie" to listOf("DT=$deviceToken"))
    }

    /** HTTP responses below 500 are valid IDX responses (errors/messages embedded in the body); only 500+ is a hard failure. */
    private suspend fun executeIdxRequest(request: ApiRequest): IdxResponse {
        val response =
            client.configuration.apiExecutor
                .execute(request)
                .getOrThrow()
        if (response.statusCode >= 500) {
            throw IllegalStateException("Request failed with status ${response.statusCode}")
        }
        val body = response.body?.decodeToString() ?: throw IllegalStateException("Empty response body")
        val v1Response = client.configuration.json.decodeFromString(Response.serializer(), body)
        return v1Response.toIdxResponse(client.configuration.json)
    }
}

/** Thrown when OIDC discovery hasn't completed/failed, mirroring auth-foundation's equivalent internal exception. */
internal class OidcEndpointsNotAvailableException : Exception("OIDC endpoints are not available.")
