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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RedirectEndSessionFlowTest {
    @Test
    fun start_WhenSuccess_ReturnsFlowContext() =
        runTest {
            val mockFlow =
                object : RedirectEndSessionFlow {
                    override suspend fun start(
                        idToken: String,
                        redirectUrl: String,
                        extraRequestParameters: Map<String, String>,
                    ): Result<RedirectEndSessionFlowContext> =
                        Result.success(
                            RedirectEndSessionFlowContext(
                                url = "https://example.okta.com/logout?id_token_hint=$idToken",
                                redirectUrl = redirectUrl,
                                state = "test-state"
                            )
                        )

                    override fun resume(
                        uri: String,
                        flowContext: RedirectEndSessionFlowContext,
                    ): Result<Unit> = Result.success(Unit)
                }

            val result = mockFlow.start("test-id-token", "com.example.app:/logout")

            assertTrue(result.isSuccess)
            val context = result.getOrNull()
            assertNotNull(context)
            assertEquals("com.example.app:/logout", context.redirectUrl)
            assertTrue(context.url.contains("test-id-token"))
        }

    @Test
    fun resume_WhenSuccess_ReturnsUnit() =
        runTest {
            val mockFlow =
                object : RedirectEndSessionFlow {
                    override suspend fun start(
                        idToken: String,
                        redirectUrl: String,
                        extraRequestParameters: Map<String, String>,
                    ): Result<RedirectEndSessionFlowContext> =
                        Result.success(
                            RedirectEndSessionFlowContext(
                                url = "https://example.okta.com/logout",
                                redirectUrl = "com.example.app:/logout",
                                state = "test-state"
                            )
                        )

                    override fun resume(
                        uri: String,
                        flowContext: RedirectEndSessionFlowContext,
                    ): Result<Unit> = Result.success(Unit)
                }

            val context = mockFlow.start("id-token", "com.example.app:/logout").getOrThrow()
            val result = mockFlow.resume("com.example.app:/logout?state=test-state", context)

            assertTrue(result.isSuccess)
        }

    @Test
    fun resume_WhenEndpointUnavailable_ReturnsFailure() =
        runTest {
            val mockFlow =
                object : RedirectEndSessionFlow {
                    override suspend fun start(
                        idToken: String,
                        redirectUrl: String,
                        extraRequestParameters: Map<String, String>,
                    ): Result<RedirectEndSessionFlowContext> = Result.failure(IllegalStateException("OIDC Endpoints not available."))

                    override fun resume(
                        uri: String,
                        flowContext: RedirectEndSessionFlowContext,
                    ): Result<Unit> = Result.success(Unit)
                }

            val result = mockFlow.start("id-token", "com.example.app:/logout")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("OIDC Endpoints not available.") == true)
        }

    @Test
    fun resume_WhenStateMismatch_ReturnsFailure() =
        runTest {
            val mockFlow =
                object : RedirectEndSessionFlow {
                    override suspend fun start(
                        idToken: String,
                        redirectUrl: String,
                        extraRequestParameters: Map<String, String>,
                    ): Result<RedirectEndSessionFlowContext> =
                        Result.success(
                            RedirectEndSessionFlowContext(
                                url = "https://example.okta.com/logout",
                                redirectUrl = "com.example.app:/logout",
                                state = "expected-state"
                            )
                        )

                    override fun resume(
                        uri: String,
                        flowContext: RedirectEndSessionFlowContext,
                    ): Result<Unit> =
                        if (uri.contains("wrong-state")) {
                            Result.failure(RedirectEndSessionFlow.ResumeException("Failed due to state mismatch."))
                        } else {
                            Result.success(Unit)
                        }
                }

            val context = mockFlow.start("id-token", "com.example.app:/logout").getOrThrow()
            val result = mockFlow.resume("com.example.app:/logout?state=wrong-state", context)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("state mismatch") == true)
        }

    @Test
    fun resume_WhenErrorReturned_ReturnsFailure() =
        runTest {
            val mockFlow =
                object : RedirectEndSessionFlow {
                    override suspend fun start(
                        idToken: String,
                        redirectUrl: String,
                        extraRequestParameters: Map<String, String>,
                    ): Result<RedirectEndSessionFlowContext> =
                        Result.success(
                            RedirectEndSessionFlowContext(
                                url = "https://example.okta.com/logout",
                                redirectUrl = "com.example.app:/logout",
                                state = "test-state"
                            )
                        )

                    override fun resume(
                        uri: String,
                        flowContext: RedirectEndSessionFlowContext,
                    ): Result<Unit> = Result.failure(RedirectEndSessionFlow.ResumeException("access_denied"))
                }

            val context = mockFlow.start("id-token", "com.example.app:/logout").getOrThrow()
            val result = mockFlow.resume("com.example.app:/logout?error=access_denied", context)

            assertTrue(result.isFailure)
            assertNotNull(result.exceptionOrNull())
        }

    @Test
    fun start_WithExtraParameters_IncludesThemInContext() =
        runTest {
            var capturedExtraParams: Map<String, String>? = null
            val mockFlow =
                object : RedirectEndSessionFlow {
                    override suspend fun start(
                        idToken: String,
                        redirectUrl: String,
                        extraRequestParameters: Map<String, String>,
                    ): Result<RedirectEndSessionFlowContext> {
                        capturedExtraParams = extraRequestParameters
                        return Result.success(
                            RedirectEndSessionFlowContext(
                                url = "https://example.okta.com/logout",
                                redirectUrl = redirectUrl,
                                state = "test-state"
                            )
                        )
                    }

                    override fun resume(
                        uri: String,
                        flowContext: RedirectEndSessionFlowContext,
                    ): Result<Unit> = Result.success(Unit)
                }

            mockFlow.start(
                idToken = "test-id-token",
                redirectUrl = "com.example.app:/logout",
                extraRequestParameters = mapOf("ui_locales" to "en")
            )

            assertNotNull(capturedExtraParams)
            assertEquals("en", capturedExtraParams["ui_locales"])
        }
}
