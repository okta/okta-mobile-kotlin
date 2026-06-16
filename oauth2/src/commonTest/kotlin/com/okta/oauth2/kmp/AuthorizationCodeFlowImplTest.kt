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
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.oauth2.internal.parseQueryParameter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
    fun start_WithNullScope_UsesClientDefaultScope() =
        runTest {
            val flow = createFlowWithScope(listOf("openid", "profile"))

            val result = flow.start(redirectUrl = "com.example.app:/callback")

            assertTrue(result.isSuccess)
            val url = result.getOrThrow().url
            assertEquals("openid profile", parseQueryParameter(url, "scope"))
        }

    @Test
    fun start_WithExplicitScope_UsesExplicitScope() =
        runTest {
            val flow = createFlow()

            val result = flow.start(redirectUrl = "com.example.app:/callback", scope = "openid offline_access")

            assertTrue(result.isSuccess)
            val url = result.getOrThrow().url
            assertEquals("openid offline_access", parseQueryParameter(url, "scope"))
        }

    @Test
    fun start_BuildsCorrectAuthorizationUrl() =
        runTest {
            val flow = createFlow()

            val result = flow.start(redirectUrl = "com.example.app:/callback", scope = "openid")

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

            val result = flow.start(redirectUrl = "com.example.app:/callback")

            assertTrue(result.isFailure)
        }

    @Test
    fun resume_WhenStateMismatch_ReturnsResumeException() =
        runTest {
            val flow = createFlow()
            val context =
                flow.start(redirectUrl = "com.example.app:/callback", scope = "openid").getOrThrow()
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
                flow.start(redirectUrl = "com.example.app:/callback", scope = "openid").getOrThrow()
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
                flow.start(redirectUrl = "com.example.app:/callback", scope = "openid").getOrThrow()
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
                flow.start(redirectUrl = "com.example.app:/callback", scope = "openid").getOrThrow()
            val uriWithoutCode = "com.example.app:/callback?state=${context.state}"

            val result = flow.resume(uriWithoutCode, context)

            assertTrue(result.isFailure)
            assertIs<AuthorizationCodeFlow.MissingResultCodeException>(result.exceptionOrNull())
        }
}
