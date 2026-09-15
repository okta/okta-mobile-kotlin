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
package com.okta.directauth.app

import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.api.http.ApiResponse
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.authfoundation.client.OAuth2EndpointOverrides
import com.okta.authfoundation.client.OidcClock
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.kmp.OAuth2Client

/**
 * A fake [ApiExecutor] that dispatches by exact request URL, records every request received, and
 * lets a test enqueue one response per call to a given URL.
 *
 * Built against the SDK's public [ApiExecutor] contract only, so it needs no network and no test
 * dependency beyond `kotlin-test`. Used to drive a real [com.okta.oauth2.kmp.CrossAppAccessFlow]
 * end to end — producing genuine [com.okta.oauth2.kmp.CrossAppAccessException] instances, which
 * cannot be constructed directly outside the `oauth2` module — while asserting exactly how many
 * requests reached each server.
 */
class RecordingApiExecutor : ApiExecutor {
    private val queuedResponses = mutableMapOf<String, MutableList<Pair<Int, String>>>()
    private val _capturedRequests = mutableListOf<ApiRequest>()

    /**
     * When positive, [execute] suspends for this long (in virtual time, under a
     * `TestDispatcher`) before recording the request or resolving a response — giving a test a
     * genuine suspension point to cancel around, so a cancel-and-supersede assertion can verify a
     * superseded attempt never reached the point of being recorded.
     */
    var responseDelayMillis: Long = 0

    /** Every request this executor has received, in order. Cancelled attempts are never added. */
    val capturedRequests: List<ApiRequest> = _capturedRequests

    /** Number of requests whose URL exactly equals [url]. */
    fun countTo(url: String): Int = _capturedRequests.count { it.url() == url }

    /**
     * Enqueues one response for [url]. Successive calls for the same [url] are returned in
     * order — one per request — which is what a re-redemption assertion needs to return two
     * distinct resource tokens from two requests to the same token endpoint.
     */
    fun enqueue(
        url: String,
        statusCode: Int,
        body: String,
    ) {
        queuedResponses.getOrPut(url) { mutableListOf() }.add(statusCode to body)
    }

    override suspend fun execute(request: ApiRequest): Result<ApiResponse> {
        if (responseDelayMillis > 0) {
            kotlinx.coroutines.delay(responseDelayMillis)
        }
        _capturedRequests.add(request)
        val url = request.url()
        val queued = queuedResponses[url]
        val (statusCode, body) =
            queued?.takeIf { it.isNotEmpty() }?.removeAt(0)
                ?: return Result.failure(IllegalStateException("RecordingApiExecutor: no stubbed response for $url"))
        return Result.success(fakeResponse(statusCode, body))
    }
}

private fun fakeResponse(
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

/**
 * A minimal OIDC discovery document naming [issuer], a token endpoint at [tokenEndpoint], and an
 * introspection endpoint at [introspectionEndpoint].
 *
 * Only the idp client's discovery is skipped via [buildTestOAuth2Client]'s endpoint overrides — a
 * Cross App Access target is built through [com.okta.oauth2.kmp.CrossAppAccessTarget.forIssuer] /
 * `forAuthorizationServerId`, which fetches real discovery for the target by design (the whole
 * point of naming it by issuer rather than pre-building its client). Stub this response for the
 * target's issuer in any test that reaches the second step.
 */
fun discoveryDocumentJson(
    issuer: String,
    tokenEndpoint: String = "$issuer/v1/token",
    introspectionEndpoint: String = "$issuer/v1/introspect",
): String = """{"issuer":"$issuer","authorization_endpoint":"$issuer/v1/authorize","token_endpoint":"$tokenEndpoint","introspection_endpoint":"$introspectionEndpoint"}"""

/** An authorization-server error response, e.g. `invalid_grant` or `invalid_scope`. */
fun errorResponseJson(
    error: String,
    description: String? = null,
): String =
    buildString {
        append("""{"error":"$error"""")
        if (description != null) append(""","error_description":"$description"""")
        append("}")
    }

/** A token-exchange response shaped like an ID-JAG issuance. */
fun idJagResponseJson(
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

/** An RFC 7662 introspection response. */
fun introspectResponseJson(active: Boolean): String = """{"active":$active}"""

/** A JWT-bearer response shaped like a resource access token issuance. */
fun resourceTokenResponseJson(
    accessToken: String = "resource-access-token",
    expiresIn: Int = 86400,
    scope: String? = "chat.read chat.history",
): String =
    buildString {
        append("""{"token_type":"Bearer","access_token":"$accessToken","expires_in":$expiresIn""")
        if (scope != null) append(""","scope":"$scope"""")
        append("}")
    }

/**
 * Builds a real [OAuth2Client] whose requests are all routed through [executor], with discovery
 * skipped entirely (all non-PAR endpoints overridden) so the only network call a test needs to
 * stub is the token endpoint itself.
 */
fun buildTestOAuth2Client(
    issuerUrl: String,
    clientId: String,
    executor: ApiExecutor,
    clientSecret: String = "test-client-secret",
    clock: OidcClock = OidcClock { 0L },
): OAuth2Client =
    OAuth2ClientBuilder
        .create(issuerUrl = issuerUrl, clientId = clientId, scope = listOf("openid")) {
            this.clientSecret = clientSecret
            this.apiExecutor = executor
            this.clock = clock
            this.endpointOverrides =
                OAuth2EndpointOverrides(
                    authorizationEndpoint = "$issuerUrl/v1/authorize",
                    tokenEndpoint = "$issuerUrl/v1/token",
                    userInfoEndpoint = "$issuerUrl/v1/userinfo",
                    jwksUri = "$issuerUrl/v1/keys",
                    introspectionEndpoint = "$issuerUrl/v1/introspect",
                    revocationEndpoint = "$issuerUrl/v1/revoke",
                    endSessionEndpoint = "$issuerUrl/v1/logout",
                    deviceAuthorizationEndpoint = "$issuerUrl/v1/device/authorize"
                )
        }.getOrThrow()

/** A controllable [OidcClock] for deterministically testing ID-JAG expiry. */
class MutableClock(
    var epochSecond: Long = 0L,
) : OidcClock {
    override fun currentTimeEpochSecond(): Long = epochSecond
}

/** A minimal [TokenInfo] fake for tests, since the SDK's own implementation is internal. */
class FakeTokenInfo(
    override val id: String = "test-token-id",
    override val clientId: String = "test-client-id",
    override val issuerUrl: String = "https://idp.example.com",
    override val tokenType: String = "Bearer",
    override val expiresIn: Int = 3600,
    override val accessToken: String = "access-token-value",
    override val scope: String? = "openid profile",
    override val refreshToken: String? = "refresh-token-value",
    override val idToken: String? = "id-token-value",
    override val deviceSecret: String? = null,
    override val issuedTokenType: String? = null,
) : TokenInfo
