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

import com.okta.authfoundation.client.TokenInfo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthorizationCodeFlowTest {
    @Test
    fun start_WhenSuccess_ReturnsContext() =
        runTest {
            val mockFlow =
                object : AuthorizationCodeFlow {
                    override suspend fun start(
                        redirectUrl: String,
                        extraRequestParameters: Map<String, String>,
                        scope: String,
                    ): Result<AuthorizationCodeFlowContext> =
                        Result.success(
                            AuthorizationCodeFlowContext(
                                url = "https://example.okta.com/authorize?response_type=code",
                                redirectUrl = redirectUrl,
                                codeVerifier = "test-code-verifier",
                                state = "test-state",
                                nonce = "test-nonce",
                                maxAge = null
                            )
                        )

                    override suspend fun resume(
                        uri: String,
                        flowContext: AuthorizationCodeFlowContext,
                    ): Result<TokenInfo> = Result.success(FakeTokenInfo())
                }

            val result = mockFlow.start("com.example.app:/callback")

            assertTrue(result.isSuccess)
            val context = result.getOrNull()
            assertNotNull(context)
            assertEquals("com.example.app:/callback", context.redirectUrl)
            assertTrue(context.url.contains("response_type=code"))
        }

    @Test
    fun resume_WhenSuccess_ReturnsTokenInfo() =
        runTest {
            val fakeToken = FakeTokenInfo()
            val mockFlow =
                object : AuthorizationCodeFlow {
                    override suspend fun start(
                        redirectUrl: String,
                        extraRequestParameters: Map<String, String>,
                        scope: String,
                    ): Result<AuthorizationCodeFlowContext> =
                        Result.success(
                            AuthorizationCodeFlowContext(
                                url = "https://example.okta.com/authorize",
                                redirectUrl = "com.example.app:/callback",
                                codeVerifier = "test-code-verifier",
                                state = "test-state",
                                nonce = "test-nonce",
                                maxAge = null
                            )
                        )

                    override suspend fun resume(
                        uri: String,
                        flowContext: AuthorizationCodeFlowContext,
                    ): Result<TokenInfo> = Result.success(fakeToken)
                }

            val context =
                AuthorizationCodeFlowContext(
                    url = "https://example.okta.com/authorize",
                    redirectUrl = "com.example.app:/callback",
                    codeVerifier = "test-code-verifier",
                    state = "test-state",
                    nonce = "test-nonce",
                    maxAge = null
                )
            val uri = "com.example.app:/callback?code=auth-code&state=test-state"
            val result = mockFlow.resume(uri, context)

            assertTrue(result.isSuccess)
            val tokenInfo = result.getOrNull()
            assertNotNull(tokenInfo)
            assertEquals("test-access-token", tokenInfo.accessToken)
            assertEquals("Bearer", tokenInfo.tokenType)
        }

    @Test
    fun resume_WhenStateMismatch_ThrowsResumeException() =
        runTest {
            val mockFlow =
                object : AuthorizationCodeFlow {
                    override suspend fun start(
                        redirectUrl: String,
                        extraRequestParameters: Map<String, String>,
                        scope: String,
                    ): Result<AuthorizationCodeFlowContext> = Result.failure(IllegalStateException())

                    override suspend fun resume(
                        uri: String,
                        flowContext: AuthorizationCodeFlowContext,
                    ): Result<TokenInfo> =
                        Result.failure(
                            AuthorizationCodeFlow.ResumeException(
                                "Failed due to state mismatch.",
                                "state_mismatch"
                            )
                        )
                }

            val context =
                AuthorizationCodeFlowContext(
                    url = "https://example.okta.com/authorize",
                    redirectUrl = "com.example.app:/callback",
                    codeVerifier = "test-code-verifier",
                    state = "expected-state",
                    nonce = "test-nonce",
                    maxAge = null
                )
            val uri = "com.example.app:/callback?code=auth-code&state=wrong-state"
            val result = mockFlow.resume(uri, context)

            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull()
            assertTrue(exception is AuthorizationCodeFlow.ResumeException)
            assertEquals("state_mismatch", exception.errorId)
        }

    @Test
    fun resume_WhenErrorReturned_ThrowsResumeException() =
        runTest {
            val mockFlow =
                object : AuthorizationCodeFlow {
                    override suspend fun start(
                        redirectUrl: String,
                        extraRequestParameters: Map<String, String>,
                        scope: String,
                    ): Result<AuthorizationCodeFlowContext> = Result.failure(IllegalStateException())

                    override suspend fun resume(
                        uri: String,
                        flowContext: AuthorizationCodeFlowContext,
                    ): Result<TokenInfo> =
                        Result.failure(
                            AuthorizationCodeFlow.ResumeException("Access denied by user.", "access_denied")
                        )
                }

            val context =
                AuthorizationCodeFlowContext(
                    url = "https://example.okta.com/authorize",
                    redirectUrl = "com.example.app:/callback",
                    codeVerifier = "test-code-verifier",
                    state = "test-state",
                    nonce = "test-nonce",
                    maxAge = null
                )
            val uri = "com.example.app:/callback?error=access_denied&error_description=Access+denied"
            val result = mockFlow.resume(uri, context)

            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull()
            assertTrue(exception is AuthorizationCodeFlow.ResumeException)
            assertEquals("access_denied", exception.errorId)
        }

    @Test
    fun resume_WhenRedirectSchemeMismatch_ThrowsRedirectSchemeMismatchException() =
        runTest {
            val mockFlow =
                object : AuthorizationCodeFlow {
                    override suspend fun start(
                        redirectUrl: String,
                        extraRequestParameters: Map<String, String>,
                        scope: String,
                    ): Result<AuthorizationCodeFlowContext> = Result.failure(IllegalStateException())

                    override suspend fun resume(
                        uri: String,
                        flowContext: AuthorizationCodeFlowContext,
                    ): Result<TokenInfo> = Result.failure(AuthorizationCodeFlow.RedirectSchemeMismatchException())
                }

            val context =
                AuthorizationCodeFlowContext(
                    url = "https://example.okta.com/authorize",
                    redirectUrl = "com.example.app:/callback",
                    codeVerifier = "test-code-verifier",
                    state = "test-state",
                    nonce = "test-nonce",
                    maxAge = null
                )
            val uri = "com.different.app:/callback?code=auth-code"
            val result = mockFlow.resume(uri, context)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is AuthorizationCodeFlow.RedirectSchemeMismatchException)
        }

    @Test
    fun start_WhenEndpointUnavailable_ReturnsFailure() =
        runTest {
            val mockFlow =
                object : AuthorizationCodeFlow {
                    override suspend fun start(
                        redirectUrl: String,
                        extraRequestParameters: Map<String, String>,
                        scope: String,
                    ): Result<AuthorizationCodeFlowContext> = Result.failure(IllegalStateException("OIDC Endpoints not available."))

                    override suspend fun resume(
                        uri: String,
                        flowContext: AuthorizationCodeFlowContext,
                    ): Result<TokenInfo> = Result.failure(IllegalStateException())
                }

            val result = mockFlow.start("com.example.app:/callback")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("OIDC Endpoints not available.") == true)
        }
}
