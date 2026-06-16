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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeviceAuthorizationFlowTest {
    @Test
    fun start_ReturnsFlowContext() =
        runTest {
            val mockFlow =
                object : DeviceAuthorizationFlow {
                    override suspend fun start(scope: String?): Result<DeviceAuthorizationFlowContext> =
                        Result.success(
                            DeviceAuthorizationFlowContext(
                                verificationUri = "https://example.okta.com/activate",
                                verificationUriComplete = "https://example.okta.com/activate?user_code=ABCD-1234",
                                userCode = "ABCD-1234",
                                expiresIn = 600,
                                deviceCode = "device-code-xyz",
                                interval = 5
                            )
                        )

                    override suspend fun resume(flowContext: DeviceAuthorizationFlowContext): Result<TokenInfo> = Result.failure(NotImplementedError())
                }

            val result = mockFlow.start("openid")

            assertTrue(result.isSuccess)
            val ctx = result.getOrNull()
            assertNotNull(ctx)
            assertEquals("https://example.okta.com/activate", ctx.verificationUri)
            assertEquals("ABCD-1234", ctx.userCode)
            assertEquals(600, ctx.expiresIn)
        }

    @Test
    fun resume_WhenSuccess_ReturnsTokenInfo() =
        runTest {
            val fakeToken = FakeTokenInfo()
            val mockFlow =
                object : DeviceAuthorizationFlow {
                    override suspend fun start(scope: String?): Result<DeviceAuthorizationFlowContext> = Result.failure(NotImplementedError())

                    override suspend fun resume(flowContext: DeviceAuthorizationFlowContext): Result<TokenInfo> = Result.success(fakeToken)
                }

            val ctx =
                DeviceAuthorizationFlowContext(
                    verificationUri = "https://example.okta.com/activate",
                    verificationUriComplete = null,
                    userCode = "XY12",
                    expiresIn = 600,
                    deviceCode = "dc",
                    interval = 5
                )
            val result = mockFlow.resume(ctx)

            assertTrue(result.isSuccess)
            assertEquals("test-access-token", result.getOrNull()?.accessToken)
        }

    @Test
    fun resume_WhenError_ReturnsFailure() =
        runTest {
            val mockFlow =
                object : DeviceAuthorizationFlow {
                    override suspend fun start(scope: String?): Result<DeviceAuthorizationFlowContext> = Result.failure(NotImplementedError())

                    override suspend fun resume(flowContext: DeviceAuthorizationFlowContext): Result<TokenInfo> = Result.failure(IllegalStateException("access_denied"))
                }

            val ctx =
                DeviceAuthorizationFlowContext(
                    verificationUri = "https://example.okta.com/activate",
                    verificationUriComplete = null,
                    userCode = "XY12",
                    expiresIn = 600,
                    deviceCode = "dc",
                    interval = 5
                )
            val result = mockFlow.resume(ctx)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("access_denied") == true)
        }

    @Test
    fun start_WhenEndpointUnavailable_ReturnsFailure() =
        runTest {
            val mockFlow =
                object : DeviceAuthorizationFlow {
                    override suspend fun start(scope: String?): Result<DeviceAuthorizationFlowContext> = Result.failure(IllegalStateException("OIDC Endpoints not available."))

                    override suspend fun resume(flowContext: DeviceAuthorizationFlowContext): Result<TokenInfo> = Result.failure(NotImplementedError())
                }

            val result = mockFlow.start("openid")

            assertTrue(result.isFailure)
        }
}
