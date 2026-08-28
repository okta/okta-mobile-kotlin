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

import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.api.http.ApiResponse

/**
 * A fake [ApiExecutor] that dispatches by exact request URL rather than by call order.
 *
 * The module's existing per-flow tests use a positional response queue (`allResponses[callIndex %
 * size]`), which is fragile for a flow that talks to two different hosts: it cannot express "the
 * target's discovery endpoint was never called", and it would let a request meant for one server
 * silently reach the other. Routing by URL makes both mistakes visible.
 */
internal class RoutingApiExecutor : ApiExecutor {
    private val queuedResponses = mutableMapOf<String, MutableList<Pair<Int, String>>>()
    private val singleResponses = mutableMapOf<String, Pair<Int, String>>()
    private val _capturedRequests = mutableListOf<ApiRequest>()

    /** Every request this executor has received, in order. */
    val capturedRequests: List<ApiRequest> = _capturedRequests

    /** Requests whose URL exactly equals [url]. */
    fun requestsTo(url: String): List<ApiRequest> = _capturedRequests.filter { it.url() == url }

    /** Number of requests whose URL exactly equals [url]. */
    fun countTo(url: String): Int = requestsTo(url).size

    /**
     * Stubs a single response, returned for every request to [url] (e.g. discovery documents,
     * which this feature requests at most once per client per test).
     */
    fun stub(
        url: String,
        statusCode: Int,
        body: String,
    ) {
        singleResponses[url] = statusCode to body
    }

    /**
     * Enqueues one response for [url]. Successive calls to [enqueue] for the same [url] are
     * returned in order — one per request — which is what a re-redemption test needs to return
     * two distinct resource tokens from two requests to the same token endpoint.
     */
    fun enqueue(
        url: String,
        statusCode: Int,
        body: String,
    ) {
        queuedResponses.getOrPut(url) { mutableListOf() }.add(statusCode to body)
    }

    override suspend fun execute(request: ApiRequest): Result<ApiResponse> {
        _capturedRequests.add(request)
        val url = request.url()
        val queued = queuedResponses[url]
        val (statusCode, body) =
            if (!queued.isNullOrEmpty()) {
                queued.removeAt(0)
            } else {
                singleResponses[url]
                    ?: return Result.failure(IllegalStateException("CrossAppAccessTestHarness: no stubbed response for $url"))
            }
        return Result.success(fakeResponse(statusCode, body))
    }
}

internal fun fakeResponse(
    statusCode: Int,
    body: String,
): ApiResponse =
    object : ApiResponse {
        override val statusCode = statusCode
        override val body = body.toByteArray()
        override val headers: Map<String, List<String>> = emptyMap()
        override val contentLength = body.length.toLong()
        override val contentType = "application/json"
    }

/** A minimal OIDC discovery document naming [issuer] and a token endpoint at [tokenEndpoint]. */
internal fun discoveryDocument(
    issuer: String,
    tokenEndpoint: String = "$issuer/v1/token",
    grantTypesSupported: List<String>? = null,
): String =
    buildString {
        append("""{"issuer":"$issuer","authorization_endpoint":"$issuer/v1/authorize","token_endpoint":"$tokenEndpoint"""")
        if (grantTypesSupported != null) {
            append(""","grant_types_supported":[${grantTypesSupported.joinToString(",") { "\"$it\"" }}]""")
        }
        append("}")
    }

/** A token-exchange response shaped like an ID-JAG issuance. */
internal fun idJagResponse(
    assertion: String = "id-jag-assertion-value",
    expiresIn: Int = 300,
    scope: String? = null,
): String =
    buildString {
        append(
            """{"issued_token_type":"urn:ietf:params:oauth:token-type:id-jag","access_token":"$assertion","token_type":"N_A","expires_in":$expiresIn"""
        )
        if (scope != null) append(""","scope":"$scope"""")
        append("}")
    }

/** A JWT-bearer response shaped like a resource access token issuance. */
internal fun resourceTokenResponse(
    accessToken: String = "resource-access-token",
    expiresIn: Int = 86400,
    scope: String? = "chat.read chat.history",
): String =
    buildString {
        append("""{"token_type":"Bearer","access_token":"$accessToken","expires_in":$expiresIn""")
        if (scope != null) append(""","scope":"$scope"""")
        append("}")
    }

/** An authorization-server error response, e.g. `invalid_grant` or `invalid_scope`. */
internal fun errorResponse(
    error: String,
    description: String? = null,
): String =
    buildString {
        append("""{"error":"$error"""")
        if (description != null) append(""","error_description":"$description"""")
        append("}")
    }
