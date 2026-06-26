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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenExchangeFlowImplTest {
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
    ): Pair<TokenExchangeFlowImpl, () -> ApiRequest?> {
        val allResponses = listOf(200 to discovery) + apiResponses.toList()
        var callIndex = 0
        var capturedRequest: ApiRequest? = null
        val apiExecutor =
            object : ApiExecutor {
                override suspend fun execute(request: ApiRequest): Result<ApiResponse> {
                    val (statusCode, body) = allResponses[callIndex % allResponses.size]
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
                    scope = clientScope
                ) {
                    this.apiExecutor = apiExecutor
                }.getOrThrow()
        return TokenExchangeFlowImpl(client) to { capturedRequest }
    }

    @Test
    fun start_WithNullScope_UsesClientDefaultScope() =
        runTest {
            val (flow, getRequest) = createFlowWithScope(listOf("openid", "profile"), 200 to tokenResponse)

            val result = flow.start("id-token", "device-secret")

            assertTrue(result.isSuccess)
            val params = assertIs<ApiFormRequest>(getRequest()).formParameters().mapValues { (_, v) -> v.first() }
            assertEquals("openid profile", params["scope"])
        }

    @Test
    fun start_WithExplicitScope_UsesExplicitScope() =
        runTest {
            val (flow, getRequest) = createFlow(200 to tokenResponse)

            val result = flow.start("id-token", "device-secret", scope = "openid offline_access")

            assertTrue(result.isSuccess)
            val params = assertIs<ApiFormRequest>(getRequest()).formParameters().mapValues { (_, v) -> v.first() }
            assertEquals("openid offline_access", params["scope"])
        }

    @Test
    fun start_SendsCorrectFormParams() =
        runTest {
            val (flow, getRequest) = createFlow(200 to tokenResponse)

            flow.start("my-id-token", "my-device-secret")

            val formRequest = assertIs<ApiFormRequest>(getRequest())
            val params = formRequest.formParameters().mapValues { (_, v) -> v.first() }
            assertEquals("urn:ietf:params:oauth:grant-type:token-exchange", params["grant_type"])
            assertEquals("urn:ietf:params:oauth:token-type:id_token", params["subject_token_type"])
            assertEquals("my-id-token", params["subject_token"])
            assertEquals("urn:x-oath:params:oauth:token-type:device-secret", params["actor_token_type"])
            assertEquals("my-device-secret", params["actor_token"])
            assertEquals("test-client-id", params["client_id"])
        }

    @Test
    fun start_WithAudience_IncludesAudienceInParams() =
        runTest {
            val (flow, getRequest) = createFlow(200 to tokenResponse)

            flow.start("id-token", "device-secret", audience = "api://custom")

            val params = assertIs<ApiFormRequest>(getRequest()).formParameters().mapValues { (_, v) -> v.first() }
            assertEquals("api://custom", params["audience"])
        }

    @Test
    fun start_WithoutAudience_OmitsAudienceParam() =
        runTest {
            val (flow, getRequest) = createFlow(200 to tokenResponse)

            flow.start("id-token", "device-secret")

            val params = assertIs<ApiFormRequest>(getRequest()).formParameters()
            assertNull(params["audience"])
        }
}
