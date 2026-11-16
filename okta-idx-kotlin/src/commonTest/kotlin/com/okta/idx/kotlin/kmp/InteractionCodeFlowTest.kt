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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InteractionCodeFlowTest {
    @BeforeTest
    fun setUp() = setUpDeviceTokenTestDouble()

    @AfterTest
    fun tearDown() = tearDownDeviceTokenTestDouble()

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
            "remediation": {
                "value": [
                    {
                        "name": "identify",
                        "method": "POST",
                        "href": "https://example.okta.com/idp/idx/identify",
                        "value": [ { "name": "identifier", "label": "Username" } ]
                    }
                ]
            }
        }
        """.trimIndent()

    private val successResponse =
        """
        {
            "expiresAt": "2021-05-21T16:41:22.000Z",
            "intent": "LOGIN",
            "successWithInteractionCode": {
                "name": "issue",
                "method": "POST",
                "href": "https://example.okta.com/oauth2/default/v1/token",
                "value": [
                    { "name": "code_verifier", "required": true },
                    { "name": "code", "required": true },
                    { "name": "grant_type", "value": "interaction_code", "required": true },
                    { "name": "client_id", "value": "test-client-id", "required": true }
                ]
            }
        }
        """.trimIndent()

    private val tokenResponse =
        """{"token_type":"Bearer","expires_in":3600,"access_token":"abc123","scope":"openid profile"}"""

    private fun createFlow(vararg extraResponses: Pair<Int, String>): InteractionCodeFlow {
        val allResponses = listOf(200 to discovery) + extraResponses.toList()
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
                    scope = listOf("openid", "profile")
                ) {
                    this.apiExecutor = apiExecutor
                }.getOrThrow()
        return InteractionCodeFlow(client)
    }

    @Test
    fun start_Resume_Proceed_ExchangeInteractionCodeForTokens_HappyPath() =
        runTest {
            val flow = createFlow(200 to interactResponse, 200 to identifyResponse, 200 to successResponse, 200 to tokenResponse)

            val startResult = flow.start(redirectUri = "com.example.app:/callback")
            assertTrue(startResult.isSuccess)

            val resumeResult = flow.resume()
            assertTrue(resumeResult.isSuccess)
            val identifyIdxResponse = resumeResult.getOrThrow()
            assertEquals(IdxResponse.Intent.LOGIN, identifyIdxResponse.intent)
            val identify = identifyIdxResponse.remediations["identify"]
            assertTrue(identify != null)

            val proceedResult = flow.proceed(identify)
            assertTrue(proceedResult.isSuccess)
            val successIdxResponse = proceedResult.getOrThrow()
            assertTrue(successIdxResponse.isLoginSuccessful)
            val issueRemediation = successIdxResponse.remediations[IdxRemediation.Type.ISSUE]
            assertTrue(issueRemediation != null)

            val tokenResult = flow.exchangeInteractionCodeForTokens(issueRemediation)
            assertTrue(tokenResult.isSuccess)
            assertEquals("abc123", tokenResult.getOrThrow().accessToken)
        }

    @Test
    fun resume_BeforeStart_ReturnsFlowNotStartedException() =
        runTest {
            val flow = createFlow()
            val result = flow.resume()
            assertTrue(result.isFailure)
            assertIs<InteractionCodeFlow.FlowNotStartedException>(result.exceptionOrNull())
        }

    @Test
    fun exchangeInteractionCodeForTokens_BeforeStart_ReturnsFlowNotStartedException() =
        runTest {
            val flow = createFlow()
            val fakeRemediation =
                IdxRemediation(
                    type = IdxRemediation.Type.ISSUE,
                    name = "issue",
                    form = IdxRemediation.Form(emptyList()),
                    authenticators = IdxAuthenticatorCollection(emptyList()),
                    capabilities = IdxCapabilityCollection(emptySet()),
                    method = "POST",
                    href = "https://example.okta.com/oauth2/default/v1/token",
                    accepts = null
                )
            val result = flow.exchangeInteractionCodeForTokens(fakeRemediation)
            assertTrue(result.isFailure)
            assertIs<InteractionCodeFlow.FlowNotStartedException>(result.exceptionOrNull())
        }

    @Test
    fun exchangeInteractionCodeForTokens_NonIssueRemediation_ReturnsInvalidRemediationException() =
        runTest {
            val flow = createFlow(200 to interactResponse)
            flow.start(redirectUri = "com.example.app:/callback").getOrThrow()

            val fakeRemediation =
                IdxRemediation(
                    type = IdxRemediation.Type.IDENTIFY,
                    name = "identify",
                    form = IdxRemediation.Form(emptyList()),
                    authenticators = IdxAuthenticatorCollection(emptyList()),
                    capabilities = IdxCapabilityCollection(emptySet()),
                    method = "POST",
                    href = "https://example.okta.com/idp/idx/identify",
                    accepts = null
                )
            val result = flow.exchangeInteractionCodeForTokens(fakeRemediation)
            assertTrue(result.isFailure)
            assertIs<InteractionCodeFlow.InvalidRemediationException>(result.exceptionOrNull())
        }

    @Test
    fun evaluateRedirectUri_BeforeStart_ReturnsFlowNotStartedException() =
        runTest {
            val flow = createFlow()
            val result = flow.evaluateRedirectUri("com.example.app:/callback?state=abc")
            assertTrue(result.isFailure)
            assertIs<InteractionCodeFlow.FlowNotStartedException>(result.exceptionOrNull())
        }

    @Test
    fun evaluateRedirectUri_MismatchedRedirectUri_ReturnsRedirectUriMismatchException() =
        runTest {
            val flow = createFlow(200 to interactResponse)
            flow.start(redirectUri = "com.example.app:/callback").getOrThrow()

            val result = flow.evaluateRedirectUri("com.other.app:/callback?state=abc")
            assertTrue(result.isFailure)
            assertIs<InteractionCodeFlow.RedirectUriMismatchException>(result.exceptionOrNull())
        }

    @Test
    fun evaluateRedirectUri_StateMismatchOnInteractionCode_ReturnsStateMismatchException() =
        runTest {
            val flow = createFlow(200 to interactResponse)
            flow.start(redirectUri = "com.example.app:/callback").getOrThrow()

            val result = flow.evaluateRedirectUri("com.example.app:/callback?state=wrong-state&interaction_code=abc")
            assertTrue(result.isFailure)
            assertIs<InteractionCodeFlow.StateMismatchException>(result.exceptionOrNull())
        }

    @Test
    fun evaluateRedirectUri_IdpError_ReturnsRedirectErrorException() =
        runTest {
            val flow = createFlow(200 to interactResponse)
            flow.start(redirectUri = "com.example.app:/callback").getOrThrow()

            val result =
                flow.evaluateRedirectUri(
                    "com.example.app:/callback?error=access_denied&error_description=User+cancelled"
                )
            assertTrue(result.isFailure)
            val exception = assertIs<InteractionCodeFlow.RedirectErrorException>(result.exceptionOrNull())
            assertEquals("access_denied", exception.errorId)
            assertEquals("User cancelled", exception.message)
        }

    @Test
    fun evaluateRedirectUri_NoRecognizedParameters_ReturnsUnhandledRedirectException() =
        runTest {
            val flow = createFlow(200 to interactResponse)
            flow.start(redirectUri = "com.example.app:/callback").getOrThrow()

            val result = flow.evaluateRedirectUri("com.example.app:/callback?foo=bar")
            assertTrue(result.isFailure)
            assertIs<InteractionCodeFlow.UnhandledRedirectException>(result.exceptionOrNull())
        }

    @Test
    fun evaluateRedirectUri_InteractionCode_ReturnsTokensOutcome() =
        runTest {
            val flow = createFlow(200 to interactResponse, 200 to tokenResponse) as InteractionCodeFlowImpl
            flow.start(redirectUri = "com.example.app:/callback").getOrThrow()
            val state = flow.flowContext!!.state

            val result = flow.evaluateRedirectUri("com.example.app:/callback?state=$state&interaction_code=abc")
            assertTrue(result.isSuccess)
            val outcome = assertIs<IdxRedirectOutcome.Tokens>(result.getOrThrow())
            assertEquals("abc123", outcome.response.accessToken)
        }

    @Test
    fun evaluateRedirectUri_InteractionRequired_ReturnsInteractionRequiredOutcome() =
        runTest {
            val flow = createFlow(200 to interactResponse, 200 to identifyResponse) as InteractionCodeFlowImpl
            flow.start(redirectUri = "com.example.app:/callback").getOrThrow()
            val state = flow.flowContext!!.state

            val result = flow.evaluateRedirectUri("com.example.app:/callback?state=$state&error=interaction_required")
            assertTrue(result.isSuccess)
            val outcome = assertIs<IdxRedirectOutcome.InteractionRequired>(result.getOrThrow())
            assertEquals(IdxResponse.Intent.LOGIN, outcome.response.intent)
        }
}
