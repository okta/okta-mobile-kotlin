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
import com.okta.authfoundation.api.http.ApiFormRequest
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.api.http.ApiResponse
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.authfoundation.client.OAuth2ClientResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DeviceAuthorizationFlowImplTest {
    // Discovery JSON with device_authorization_endpoint populated.
    private val discoveryWithDevice =
        """
        {
            "issuer": "https://example.okta.com/oauth2/default",
            "authorization_endpoint": "https://example.okta.com/oauth2/default/v1/authorize",
            "token_endpoint": "https://example.okta.com/oauth2/default/v1/token",
            "device_authorization_endpoint": "https://example.okta.com/oauth2/default/v1/device/authorize"
        }
        """.trimIndent()

    // Discovery JSON without device_authorization_endpoint.
    private val discoveryWithoutDevice =
        """
        {
            "issuer": "https://example.okta.com/oauth2/default",
            "authorization_endpoint": "https://example.okta.com/oauth2/default/v1/authorize",
            "token_endpoint": "https://example.okta.com/oauth2/default/v1/token"
        }
        """.trimIndent()

    private val deviceAuthResponse =
        """
        {
            "device_code": "dc-test-123",
            "user_code": "ABCD-1234",
            "verification_uri": "https://example.okta.com/activate",
            "verification_uri_complete": "https://example.okta.com/activate?user_code=ABCD-1234",
            "expires_in": 600,
            "interval": 5
        }
        """.trimIndent()

    private val tokenResponse =
        """
        {
            "token_type": "Bearer",
            "expires_in": 3600,
            "access_token": "test-access-token",
            "scope": "openid"
        }
        """.trimIndent()

    /**
     * Creates a [DeviceAuthorizationFlowImpl] backed by a stub [ApiExecutor].
     *
     * The first response is always the discovery response (consumed when endpoints are first resolved).
     * Subsequent responses are consumed in order for each API call made by the flow.
     */
    private fun createFlow(
        discovery: String,
        vararg apiResponses: Pair<Int, String>,
    ): DeviceAuthorizationFlowImpl {
        val allResponses = listOf(200 to discovery) + apiResponses.toList()
        var callIndex = 0
        val apiExecutor =
            object : ApiExecutor {
                override suspend fun execute(request: ApiRequest): Result<ApiResponse> {
                    val (statusCode, body) = allResponses[callIndex++ % allResponses.size]
                    return Result.success(
                        object : ApiResponse {
                            override val statusCode: Int = statusCode
                            override val body: ByteArray = body.toByteArray()
                            override val headers: Map<String, List<String>> = emptyMap()
                            override val contentLength: Long = body.length.toLong()
                            override val contentType: String = "application/json"
                        }
                    )
                }
            }
        val client =
            OAuth2ClientBuilder
                .create(
                    issuerUrl = "https://example.okta.com/oauth2/default",
                    clientId = "test-client-id",
                    scope = listOf("openid")
                ) {
                    this.apiExecutor = apiExecutor
                }.getOrThrow()
        return DeviceAuthorizationFlowImpl(client)
    }

    // ──────────────────────────── start() ────────────────────────────

    @Test
    fun start_WithValidScope_ReturnsFlowContext() =
        runTest {
            // Responses: [discovery, deviceAuthResponse]
            val flow = createFlow(discoveryWithDevice, 200 to deviceAuthResponse)
            val result = flow.start("openid profile")

            assertTrue(result.isSuccess)
            val ctx = result.getOrThrow()
            assertEquals("dc-test-123", ctx.deviceCode)
            assertEquals("ABCD-1234", ctx.userCode)
            assertEquals("https://example.okta.com/activate", ctx.verificationUri)
            assertEquals("https://example.okta.com/activate?user_code=ABCD-1234", ctx.verificationUriComplete)
            assertEquals(600, ctx.expiresIn)
            assertEquals(5, ctx.interval)
        }

    @Test
    fun start_WhenDeviceEndpointNull_ReturnsFailure() =
        runTest {
            // Responses: [discovery without device endpoint] — no further calls expected
            val flow = createFlow(discoveryWithoutDevice)
            val result = flow.start("openid")

            assertTrue(result.isFailure)
            assertIs<OAuth2ClientResult.Error.OidcEndpointsNotAvailableException>(result.exceptionOrNull())
        }

    @Test
    fun start_SendsCorrectFormParams() =
        runTest {
            var capturedRequest: ApiRequest? = null
            var callIndex = 0
            val responses = listOf(200 to discoveryWithDevice, 200 to deviceAuthResponse)
            val apiExecutor =
                object : ApiExecutor {
                    override suspend fun execute(request: ApiRequest): Result<ApiResponse> {
                        val (statusCode, body) = responses[callIndex % responses.size]
                        if (callIndex == 1) capturedRequest = request
                        callIndex++
                        return Result.success(
                            object : ApiResponse {
                                override val statusCode = statusCode
                                override val body = body.toByteArray()
                                override val headers: Map<String, List<String>> = emptyMap()
                                override val contentLength = body.length.toLong()
                                override val contentType = "application/json"
                            }
                        )
                    }
                }
            val client =
                OAuth2ClientBuilder
                    .create(
                        issuerUrl = "https://example.okta.com/oauth2/default",
                        clientId = "test-client-id",
                        scope = listOf("openid")
                    ) {
                        this.apiExecutor = apiExecutor
                    }.getOrThrow()
            val flow = DeviceAuthorizationFlowImpl(client)

            flow.start("openid profile")

            val formRequest = assertIs<ApiFormRequest>(capturedRequest)
            assertEquals(
                "https://example.okta.com/oauth2/default/v1/device/authorize",
                formRequest.url()
            )
            val params = formRequest.formParameters().mapValues { (_, v) -> v.first() }
            assertEquals("test-client-id", params["client_id"])
            assertEquals("openid profile", params["scope"])
        }

    // ──────────────────────────── resume() ────────────────────────────

    private fun makeContext(
        expiresIn: Int = 60,
        interval: Int = 5,
    ) = DeviceAuthorizationFlowContext(
        verificationUri = "https://example.okta.com/activate",
        verificationUriComplete = null,
        userCode = "ABCD-1234",
        expiresIn = expiresIn,
        deviceCode = "dc-test-123",
        interval = interval
    )

    @Test
    fun resume_WhenImmediateSuccess_ReturnsTokenInfo() =
        runTest {
            // Responses: [discovery, tokenResponse]
            val flow = createFlow(discoveryWithDevice, 200 to tokenResponse)
            val delays = mutableListOf<Long>()
            flow.delayFunction = { ms -> delays += ms }

            val result = flow.resume(makeContext())

            assertTrue(result.isSuccess)
            assertEquals("test-access-token", result.getOrThrow().accessToken)
            assertEquals(listOf(5000L), delays)
        }

    @Test
    fun resume_WhenAuthorizationPending_PollsUntilSuccess() =
        runTest {
            val pending = """{"error":"authorization_pending","error_description":"Authorization pending"}"""
            // Responses: [discovery, pending, pending, tokenResponse]
            val flow =
                createFlow(
                    discoveryWithDevice,
                    400 to pending,
                    400 to pending,
                    200 to tokenResponse
                )
            val delays = mutableListOf<Long>()
            flow.delayFunction = { ms -> delays += ms }

            val result = flow.resume(makeContext())

            assertTrue(result.isSuccess)
            assertEquals("test-access-token", result.getOrThrow().accessToken)
            assertEquals(listOf(5000L, 5000L, 5000L), delays)
        }

    @Test
    fun resume_WhenSlowDown_IncreasesInterval() =
        runTest {
            val slowDown = """{"error":"slow_down","error_description":"Slow down"}"""
            // Responses: [discovery, slowDown, tokenResponse]
            val flow =
                createFlow(
                    discoveryWithDevice,
                    400 to slowDown,
                    200 to tokenResponse
                )
            val delays = mutableListOf<Long>()
            flow.delayFunction = { ms -> delays += ms }

            val result = flow.resume(makeContext())

            assertTrue(result.isSuccess)
            assertEquals("test-access-token", result.getOrThrow().accessToken)
            // First iteration uses interval=5s; slow_down bumps to 10s for the second
            assertEquals(listOf(5000L, 10000L), delays)
        }

    @Test
    fun resume_WhenTimeout_ThrowsTimeoutException() =
        runTest {
            val pending = """{"error":"authorization_pending","error_description":"Authorization pending"}"""
            // expiresIn=10, interval=5 → 2 iterations, then timeout
            // Responses: [discovery, pending, pending]
            val flow = createFlow(discoveryWithDevice, 400 to pending, 400 to pending)
            flow.delayFunction = { /* no-op */ }

            val result = flow.resume(makeContext(expiresIn = 10, interval = 5))

            assertTrue(result.isFailure)
            assertIs<DeviceAuthorizationFlow.TimeoutException>(result.exceptionOrNull())
        }

    @Test
    fun resume_WhenNonPollingError_PropagatesError() =
        runTest {
            val denied = """{"error":"access_denied","error_description":"Access denied"}"""
            // Responses: [discovery, access_denied]
            val flow = createFlow(discoveryWithDevice, 400 to denied)
            flow.delayFunction = { /* no-op */ }

            val result = flow.resume(makeContext())

            assertTrue(result.isFailure)
            val ex = assertIs<OAuth2ClientResult.Error.HttpResponseException>(result.exceptionOrNull())
            assertEquals("access_denied", ex.error)
            assertEquals(400, ex.responseCode)
        }
}
