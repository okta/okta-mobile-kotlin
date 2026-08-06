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
import com.okta.oauth2.internal.parseQueryParameter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AuthorizationCodeFlowImplTest {
    private val discovery =
        """
        {
            "issuer": "https://example.okta.com/oauth2/default",
            "authorization_endpoint": "https://example.okta.com/oauth2/default/v1/authorize",
            "token_endpoint": "https://example.okta.com/oauth2/default/v1/token"
        }
        """.trimIndent()

    private val tokenResponse =
        """
        {
            "token_type": "Bearer",
            "expires_in": 3600,
            "access_token": "test-access-token",
            "scope": "openid profile"
        }
        """.trimIndent()

    private val discoveryWithParOptional =
        """
        {
            "issuer": "https://example.okta.com/oauth2/default",
            "authorization_endpoint": "https://example.okta.com/oauth2/default/v1/authorize",
            "token_endpoint": "https://example.okta.com/oauth2/default/v1/token",
            "pushed_authorization_request_endpoint": "https://example.okta.com/oauth2/default/v1/par"
        }
        """.trimIndent()

    private val discoveryWithParRequired =
        """
        {
            "issuer": "https://example.okta.com/oauth2/default",
            "authorization_endpoint": "https://example.okta.com/oauth2/default/v1/authorize",
            "token_endpoint": "https://example.okta.com/oauth2/default/v1/token",
            "pushed_authorization_request_endpoint": "https://example.okta.com/oauth2/default/v1/par",
            "require_pushed_authorization_requests": true
        }
        """.trimIndent()

    private fun createFlow(vararg apiResponses: Pair<Int, String>) = createFlowWithScope(listOf("openid", "profile"), *apiResponses)

    private fun createFlowWithScope(
        clientScope: List<String>,
        vararg apiResponses: Pair<Int, String>,
    ): AuthorizationCodeFlowImpl {
        val allResponses = listOf(200 to discovery) + apiResponses.toList()
        var callIndex = 0
        val apiExecutor =
            object : ApiExecutor {
                override suspend fun execute(request: ApiRequest): Result<ApiResponse> {
                    val (statusCode, body) = allResponses[callIndex++ % allResponses.size]
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
                    scope = clientScope
                ) {
                    this.apiExecutor = apiExecutor
                }.getOrThrow()
        return AuthorizationCodeFlowImpl(client)
    }

    @Test
    fun start_WithExplicitScope_UsesExplicitScope() =
        runTest {
            val flow = createFlow()

            val result = flow.start(redirectUrl = "com.example.app:/callback", scope = listOf("openid", "offline_access"))

            assertTrue(result.isSuccess)
            val url = result.getOrThrow().url
            assertEquals("openid offline_access", parseQueryParameter(url, "scope"))
        }

    @Test
    fun start_BuildsCorrectAuthorizationUrl() =
        runTest {
            val flow = createFlow()

            val result = flow.start(redirectUrl = "com.example.app:/callback", scope = listOf("openid"))

            assertTrue(result.isSuccess)
            val context = result.getOrThrow()
            val url = context.url
            assertTrue(url.startsWith("https://example.okta.com/oauth2/default/v1/authorize"))
            assertEquals("code", parseQueryParameter(url, "response_type"))
            assertEquals("test-client-id", parseQueryParameter(url, "client_id"))
            assertEquals("com.example.app:/callback", parseQueryParameter(url, "redirect_uri"))
            assertNotNull(parseQueryParameter(url, "state"))
            assertNotNull(parseQueryParameter(url, "nonce"))
            assertNotNull(parseQueryParameter(url, "code_challenge"))
            assertEquals("S256", parseQueryParameter(url, "code_challenge_method"))
        }

    @Test
    fun start_WhenEndpointUnavailable_ReturnsFailure() =
        runTest {
            val discoveryWithoutAuthorize =
                """
                {
                    "issuer": "https://example.okta.com/oauth2/default",
                    "token_endpoint": "https://example.okta.com/oauth2/default/v1/token"
                }
                """.trimIndent()
            val allResponses = listOf(200 to discoveryWithoutAuthorize)
            var callIndex = 0
            val apiExecutor =
                object : ApiExecutor {
                    override suspend fun execute(request: ApiRequest): Result<ApiResponse> {
                        val (statusCode, body) = allResponses[callIndex++ % allResponses.size]
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
                    ) { this.apiExecutor = apiExecutor }
                    .getOrThrow()
            val flow = AuthorizationCodeFlowImpl(client)

            val result = flow.start(redirectUrl = "com.example.app:/callback", scope = listOf("openid"))

            assertTrue(result.isFailure)
        }

    @Test
    fun start_WhenDiscoveryFails_PreservesOriginalCause() =
        runTest {
            val discoveryFailure = IllegalStateException("network unreachable")
            val apiExecutor =
                object : ApiExecutor {
                    override suspend fun execute(request: ApiRequest): Result<ApiResponse> = Result.failure(discoveryFailure)
                }
            val client =
                OAuth2ClientBuilder
                    .create(
                        issuerUrl = "https://example.okta.com/oauth2/default",
                        clientId = "test-client-id",
                        scope = listOf("openid")
                    ) { this.apiExecutor = apiExecutor }
                    .getOrThrow()
            val flow = AuthorizationCodeFlowImpl(client)

            val result = flow.start(redirectUrl = "com.example.app:/callback", scope = listOf("openid"))

            assertTrue(result.isFailure)
            val exception = assertIs<OAuth2ClientResult.Error.OidcEndpointsNotAvailableException>(result.exceptionOrNull())
            assertSame(discoveryFailure, exception.cause)
        }

    @Test
    fun resume_WhenStateMismatch_ReturnsResumeException() =
        runTest {
            val flow = createFlow()
            val context =
                flow.start(redirectUrl = "com.example.app:/callback", scope = listOf("openid")).getOrThrow()
            val uriWithWrongState = "com.example.app:/callback?code=auth-code&state=wrong-state"

            val result = flow.resume(uriWithWrongState, context)

            assertTrue(result.isFailure)
            assertIs<AuthorizationCodeFlow.ResumeException>(result.exceptionOrNull())
            assertEquals("state_mismatch", (result.exceptionOrNull() as AuthorizationCodeFlow.ResumeException).errorId)
        }

    @Test
    fun resume_WhenRedirectSchemeMismatch_ReturnsRedirectSchemeMismatchException() =
        runTest {
            val flow = createFlow()
            val context =
                flow.start(redirectUrl = "com.example.app:/callback", scope = listOf("openid")).getOrThrow()
            val uriWithWrongScheme = "com.other.app:/callback?code=auth-code&state=${context.state}"

            val result = flow.resume(uriWithWrongScheme, context)

            assertTrue(result.isFailure)
            assertIs<AuthorizationCodeFlow.RedirectSchemeMismatchException>(result.exceptionOrNull())
        }

    @Test
    fun resume_WhenErrorInUri_ReturnsResumeException() =
        runTest {
            val flow = createFlow()
            val context =
                flow.start(redirectUrl = "com.example.app:/callback", scope = listOf("openid")).getOrThrow()
            val uriWithError = "com.example.app:/callback?error=access_denied&error_description=Access+denied&state=${context.state}"

            val result = flow.resume(uriWithError, context)

            assertTrue(result.isFailure)
            val ex = assertIs<AuthorizationCodeFlow.ResumeException>(result.exceptionOrNull())
            assertEquals("access_denied", ex.errorId)
        }

    @Test
    fun resume_WhenNoCode_ReturnsMissingResultCodeException() =
        runTest {
            val flow = createFlow()
            val context =
                flow.start(redirectUrl = "com.example.app:/callback", scope = listOf("openid")).getOrThrow()
            val uriWithoutCode = "com.example.app:/callback?state=${context.state}"

            val result = flow.resume(uriWithoutCode, context)

            assertTrue(result.isFailure)
            assertIs<AuthorizationCodeFlow.MissingResultCodeException>(result.exceptionOrNull())
        }

    @Test
    fun start_WhenParSupported_UsesRequestUriAuthorizationUrl() =
        runTest {
            val requests = mutableListOf<ApiRequest>()
            val apiExecutor =
                object : ApiExecutor {
                    override suspend fun execute(request: ApiRequest): Result<ApiResponse> {
                        requests.add(request)
                        val body =
                            when {
                                request.url().endsWith(".well-known/openid-configuration") -> {
                                    discoveryWithParOptional
                                }

                                request.url().endsWith("/v1/par") -> {
                                    """
                                    {
                                        "request_uri": "urn:okta:request:abc123",
                                        "expires_in": 90
                                    }
                                    """.trimIndent()
                                }

                                else -> {
                                    tokenResponse
                                }
                            }
                        return Result.success(
                            object : ApiResponse {
                                override val statusCode = 200
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
                        issuerUrl = "https://example.okta.com",
                        clientId = "test-client-id",
                        scope = listOf("openid")
                    ) {
                        authorizationServerId = "default"
                        this.apiExecutor = apiExecutor
                    }.getOrThrow()
            val flow = AuthorizationCodeFlowImpl(client)

            val result = flow.start(redirectUrl = "com.example.app:/callback", scope = listOf("openid"))

            assertTrue(result.isSuccess)
            val context = result.getOrThrow()
            assertTrue(context.usedPushedAuthorizationRequest)
            assertEquals("urn:okta:request:abc123", context.pushedAuthorizationRequestUri)
            assertEquals("urn:okta:request:abc123", parseQueryParameter(context.url, "request_uri"))
            assertEquals("test-client-id", parseQueryParameter(context.url, "client_id"))
            assertNull(parseQueryParameter(context.url, "response_type"))

            val parRequest = requests.first { it.url().endsWith("/v1/par") } as ApiFormRequest
            val form = parRequest.formParameters().mapValues { it.value.first() }
            assertEquals("code", form["response_type"])
            assertEquals("com.example.app:/callback", form["redirect_uri"])
            assertNotNull(form["state"])
            assertNotNull(form["nonce"])
        }

    @Test
    fun start_WhenParOptionalAndParRequestFails_FallsBackToClassicAuthorizationUrl() =
        runTest {
            val apiExecutor =
                object : ApiExecutor {
                    override suspend fun execute(request: ApiRequest): Result<ApiResponse> {
                        val (statusCode, body) =
                            when {
                                request.url().endsWith(".well-known/openid-configuration") -> 200 to discoveryWithParOptional
                                request.url().endsWith("/v1/par") -> 400 to """{"error":"invalid_request"}"""
                                else -> 200 to tokenResponse
                            }
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
                        issuerUrl = "https://example.okta.com",
                        clientId = "test-client-id",
                        scope = listOf("openid")
                    ) {
                        authorizationServerId = "default"
                        this.apiExecutor = apiExecutor
                    }.getOrThrow()
            val flow = AuthorizationCodeFlowImpl(client)

            val result = flow.start(redirectUrl = "com.example.app:/callback", scope = listOf("openid"))

            assertTrue(result.isSuccess)
            val context = result.getOrThrow()
            assertFalse(context.usedPushedAuthorizationRequest)
            assertNull(context.pushedAuthorizationRequestUri)
            assertEquals("code", parseQueryParameter(context.url, "response_type"))
            assertNull(parseQueryParameter(context.url, "request_uri"))
        }

    @Test
    fun start_WhenParRequiredAndParRequestFails_ReturnsParRequiredException() =
        runTest {
            val apiExecutor =
                object : ApiExecutor {
                    override suspend fun execute(request: ApiRequest): Result<ApiResponse> {
                        val (statusCode, body) =
                            when {
                                request.url().endsWith(".well-known/openid-configuration") -> 200 to discoveryWithParRequired
                                request.url().endsWith("/v1/par") -> 500 to ""
                                else -> 200 to tokenResponse
                            }
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
                        issuerUrl = "https://example.okta.com",
                        clientId = "test-client-id",
                        scope = listOf("openid")
                    ) {
                        authorizationServerId = "default"
                        this.apiExecutor = apiExecutor
                    }.getOrThrow()
            val flow = AuthorizationCodeFlowImpl(client)

            val result = flow.start(redirectUrl = "com.example.app:/callback", scope = listOf("openid"))

            assertTrue(result.isFailure)
            assertIs<AuthorizationCodeFlow.PushedAuthorizationRequiredException>(result.exceptionOrNull())
        }
}
