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

import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.api.http.ApiResponse
import com.okta.authfoundation.client.OAuth2ClientBuilder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Confirms [InteractionCodeFlowImpl] works on the `jvm` target and that [DeviceTokenCookieHook.NoOp]
 * (the `jvm` default, per [defaultDeviceTokenCookieHook]) never attaches a `Cookie` header — the shared
 * happy-path/redirect-handling behavior itself is exercised once for all targets in `commonTest`'s
 * `InteractionCodeFlowTest`, which also runs under this `jvmTest` task.
 */
class InteractionCodeFlowJvmTest {
    private val discovery =
        """
        {
            "issuer": "https://example.okta.com/oauth2/default",
            "authorization_endpoint": "https://example.okta.com/oauth2/default/v1/authorize",
            "token_endpoint": "https://example.okta.com/oauth2/default/v1/token"
        }
        """.trimIndent()

    private val interactResponse = """{"interaction_handle":"029ZAB"}"""

    private val identifyResponse =
        """
        {
            "expiresAt": "2021-05-21T16:41:22.000Z",
            "intent": "LOGIN",
            "remediation": { "value": [] }
        }
        """.trimIndent()

    @Test
    fun resume_WithDefaultJvmDeviceTokenCookieHook_SendsNoCookieHeader() =
        runTest {
            val allResponses = listOf(200 to discovery, 200 to interactResponse, 200 to identifyResponse)
            var callIndex = 0
            val observedHeaders = mutableListOf<Map<String, List<String>>>()
            val apiExecutor =
                object : ApiExecutor {
                    override suspend fun execute(request: ApiRequest): Result<ApiResponse> {
                        observedHeaders.add(request.headers())
                        val (statusCode, body) = allResponses[callIndex++]
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
                        scope = listOf("openid", "profile")
                    ) {
                        this.apiExecutor = apiExecutor
                    }.getOrThrow()
            val flow = InteractionCodeFlow(client)

            flow.start(redirectUri = "com.example.app:/callback").getOrThrow()
            val resumeResult = flow.resume()

            assertTrue(resumeResult.isSuccess)
            val introspectRequestHeaders = observedHeaders.last()
            assertFalse(introspectRequestHeaders.containsKey("Cookie"), "Expected no Cookie header from the no-op device token hook.")
        }
}
