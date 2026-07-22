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
package com.okta.authfoundation.client.kmp

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.api.http.ApiResponse
import com.okta.authfoundation.client.Cache
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.authfoundation.client.OAuth2ClientConfiguration
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.OidcClock
import com.okta.authfoundation.client.internal.OAuth2Endpoints
import com.okta.authfoundation.util.CoalescingOrchestrator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(InternalAuthFoundationApi::class)
class RateLimitRetryTest {
    private val testEndpoints =
        OAuth2Endpoints(
            issuer = "https://example.okta.com",
            authorizationEndpoint = "https://example.okta.com/v1/authorize",
            tokenEndpoint = "https://example.okta.com/v1/token",
            userInfoEndpoint = "https://example.okta.com/v1/userinfo",
            jwksUri = "https://example.okta.com/v1/keys",
            introspectionEndpoint = "https://example.okta.com/v1/introspect",
            revocationEndpoint = "https://example.okta.com/v1/revoke",
            endSessionEndpoint = "https://example.okta.com/v1/logout",
            deviceAuthorizationEndpoint = null
        )

    private val noOpCache =
        object : Cache {
            override fun get(key: String): String? = null

            override fun set(
                key: String,
                value: String,
            ) {}

            override fun clear() {}
        }

    private val testClock = OidcClock { 1_000_000L }

    private fun createClientWithExecutor(
        executor: ApiExecutor,
        retryCallback: ((Int) -> RateLimitRetryConfig?)? = null,
    ): OAuth2Client {
        val config =
            OAuth2ClientConfiguration(
                clientId = "test-client",
                defaultScope = "openid",
                issuerUrl = "https://example.okta.com",
                apiExecutor = executor,
                clock = testClock,
                json = Json { ignoreUnknownKeys = true },
                cache = noOpCache,
                authorizationServerId = null,
                clientSecret = null,
                acrValues = null,
                rateLimitRetryCallback = retryCallback
            )
        return OAuth2Client(
            config,
            CoalescingOrchestrator(
                factory = { OAuth2ClientResult.Success(testEndpoints) },
                keepDataInMemory = { true }
            )
        )
    }

    @Test
    fun builder_WithNoCallback_ConfigurationCallbackIsNull() {
        val client =
            OAuth2ClientBuilder
                .create(
                    issuerUrl = "https://example.okta.com",
                    clientId = "client-id",
                    scope = listOf("openid")
                ).getOrThrow()

        assertNull(client.configuration.rateLimitRetryCallback)
    }

    @Test
    fun builder_WithCallback_ConfigurationCallbackIsSet() {
        val callback: (Int) -> RateLimitRetryConfig? = { _ -> RateLimitRetryConfig(MaxRetries(3), MinDelaySeconds(1L)) }

        val client =
            OAuth2ClientBuilder
                .create(
                    issuerUrl = "https://example.okta.com",
                    clientId = "client-id",
                    scope = listOf("openid")
                ) {
                    rateLimitRetryCallback = callback
                }.getOrThrow()

        assertNotNull(client.configuration.rateLimitRetryCallback)
        assertEquals(
            RateLimitRetryConfig(MaxRetries(3), MinDelaySeconds(1L)),
            client.configuration.rateLimitRetryCallback!!.invoke(0)
        )
    }

    @Test
    fun builder_WithCallback_RetriesOn429() =
        runTest {
            var requestCount = 0
            val executor =
                object : ApiExecutor {
                    override suspend fun execute(request: ApiRequest): Result<ApiResponse> {
                        requestCount++
                        return if (requestCount == 1) {
                            Result.success(
                                object : ApiResponse {
                                    override val statusCode = 429
                                    override val body = byteArrayOf()
                                    override val headers: Map<String, List<String>> = emptyMap()
                                    override val contentLength = 0L
                                    override val contentType = "application/json"
                                }
                            )
                        } else {
                            Result.success(
                                object : ApiResponse {
                                    override val statusCode = 200
                                    override val body = """{"active":true}""".toByteArray()
                                    override val headers: Map<String, List<String>> = emptyMap()
                                    override val contentLength = 15L
                                    override val contentType = "application/json"
                                }
                            )
                        }
                    }
                }

            val client =
                OAuth2ClientBuilder
                    .create(
                        issuerUrl = "https://example.okta.com",
                        clientId = "client-id",
                        scope = listOf("openid")
                    ) {
                        apiExecutor = executor
                        rateLimitRetryCallback = { _ -> RateLimitRetryConfig(MaxRetries(3), MinDelaySeconds(0L)) }
                    }.getOrThrow()

            // Inject pre-resolved endpoints so the test doesn't make a network discovery call
            @OptIn(InternalAuthFoundationApi::class)
            val clientWithEndpoints =
                OAuth2Client(
                    client.configuration,
                    CoalescingOrchestrator(
                        factory = { OAuth2ClientResult.Success(testEndpoints) },
                        keepDataInMemory = { true }
                    )
                )

            val result = clientWithEndpoints.introspectToken("access_token", "exampleToken")

            assertTrue(result.isSuccess, "Should succeed after retry")
            assertEquals(2, requestCount, "Should have made 2 requests: 1 failed + 1 retry")
        }

    @Test
    fun rateLimitRetryConfig_HoldsCorrectValues() {
        val config = RateLimitRetryConfig(MaxRetries(3), MinDelaySeconds(1L))
        assertEquals(3, config.maxRetries.value)
        assertEquals(1L, config.minDelaySeconds.value)
    }

    @Test
    fun rateLimitRetryConfig_ThrowsOnNegativeMaxRetries() {
        val ex =
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                MaxRetries(-1)
            }
        assertTrue(ex.message!!.contains("-1"))
    }

    @Test
    fun rateLimitRetryConfig_ThrowsOnNegativeMinDelaySeconds() {
        val ex =
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                MinDelaySeconds(-1L)
            }
        assertTrue(ex.message!!.contains("-1"))
    }

    @Test
    fun withNoRetryCallback_Returns429Immediately_WithOneRequest() =
        runTest {
            var requestCount = 0
            val executor =
                object : ApiExecutor {
                    override suspend fun execute(request: ApiRequest): Result<ApiResponse> {
                        requestCount++
                        return Result.success(
                            object : ApiResponse {
                                override val statusCode = 429
                                override val body = byteArrayOf()
                                override val headers: Map<String, List<String>> = emptyMap()
                                override val contentLength = 0L
                                override val contentType = "application/json"
                            }
                        )
                    }
                }

            val client = createClientWithExecutor(executor, retryCallback = null)
            val result = client.introspectToken("access_token", "exampleToken")

            assertTrue(result.isFailure)
            assertEquals(1, requestCount, "Without callback, should not retry — exactly 1 request expected")
        }

    @Test
    fun withRetryCallback_RetriesOnce_WhenFirstCall429SecondCallSucceeds() =
        runTest {
            var requestCount = 0
            val executor =
                object : ApiExecutor {
                    override suspend fun execute(request: ApiRequest): Result<ApiResponse> {
                        requestCount++
                        return if (requestCount == 1) {
                            Result.success(
                                object : ApiResponse {
                                    override val statusCode = 429
                                    override val body = byteArrayOf()
                                    override val headers: Map<String, List<String>> = emptyMap()
                                    override val contentLength = 0L
                                    override val contentType = "application/json"
                                }
                            )
                        } else {
                            Result.success(
                                object : ApiResponse {
                                    override val statusCode = 200
                                    override val body = """{"active":true}""".toByteArray()
                                    override val headers: Map<String, List<String>> = emptyMap()
                                    override val contentLength = 15L
                                    override val contentType = "application/json"
                                }
                            )
                        }
                    }
                }

            val client =
                createClientWithExecutor(
                    executor,
                    retryCallback = { _ -> RateLimitRetryConfig(MaxRetries(3), MinDelaySeconds(0L)) }
                )
            val result = client.introspectToken("access_token", "exampleToken")

            assertTrue(result.isSuccess, "Should succeed after retry, but got: ${result.exceptionOrNull()}")
            assertEquals(2, requestCount, "Should have made exactly 2 requests (1 failed + 1 retry)")
        }

    @Test
    fun withRetryCallback_StopsRetrying_WhenCallbackReturnsNull() =
        runTest {
            var requestCount = 0
            var callbackCount = 0
            val executor =
                object : ApiExecutor {
                    override suspend fun execute(request: ApiRequest): Result<ApiResponse> {
                        requestCount++
                        return Result.success(
                            object : ApiResponse {
                                override val statusCode = 429
                                override val body = byteArrayOf()
                                override val headers: Map<String, List<String>> = emptyMap()
                                override val contentLength = 0L
                                override val contentType = "application/json"
                            }
                        )
                    }
                }

            val client =
                createClientWithExecutor(
                    executor,
                    retryCallback = { _ ->
                        callbackCount++
                        if (callbackCount >= 2) {
                            null
                        } else {
                            RateLimitRetryConfig(MaxRetries(10), MinDelaySeconds(0L))
                        }
                    }
                )
            val result = client.introspectToken("access_token", "exampleToken")

            assertTrue(result.isFailure)
            assertEquals(2, requestCount, "Should have made 2 requests before callback returned null")
        }

    @Test
    fun withRetryCallback_PropagatesException_WhenCallbackThrows() =
        runTest {
            val callbackException = IllegalStateException("callback exploded")
            val executor =
                object : ApiExecutor {
                    override suspend fun execute(request: ApiRequest): Result<ApiResponse> =
                        Result.success(
                            object : ApiResponse {
                                override val statusCode = 429
                                override val body = byteArrayOf()
                                override val headers: Map<String, List<String>> = emptyMap()
                                override val contentLength = 0L
                                override val contentType = "application/json"
                            }
                        )
                }

            val client =
                createClientWithExecutor(
                    executor,
                    retryCallback = { _ -> throw callbackException }
                )
            val result = client.introspectToken("access_token", "exampleToken")

            assertTrue(result.isFailure)
            assertEquals(callbackException, result.exceptionOrNull(), "Callback exception must propagate unchanged")
        }
}
