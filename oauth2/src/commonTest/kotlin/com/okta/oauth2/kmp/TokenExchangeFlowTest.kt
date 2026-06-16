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

class TokenExchangeFlowTest {
    @Test
    fun start_WhenSuccess_ReturnsTokenInfo() =
        runTest {
            val fakeToken = FakeTokenInfo()
            val mockFlow =
                object : TokenExchangeFlow {
                    override suspend fun start(
                        idToken: String,
                        deviceSecret: String,
                        audience: String?,
                        scope: String?,
                    ): Result<TokenInfo> = Result.success(fakeToken)
                }

            val result = mockFlow.start("id-token", "device-secret")

            assertTrue(result.isSuccess)
            val tokenInfo = result.getOrNull()
            assertNotNull(tokenInfo)
            assertEquals("test-access-token", tokenInfo.accessToken)
            assertEquals("Bearer", tokenInfo.tokenType)
        }

    @Test
    fun start_WhenError_ReturnsFailure() =
        runTest {
            val mockFlow =
                object : TokenExchangeFlow {
                    override suspend fun start(
                        idToken: String,
                        deviceSecret: String,
                        audience: String?,
                        scope: String?,
                    ): Result<TokenInfo> = Result.failure(IllegalStateException("invalid_grant"))
                }

            val result = mockFlow.start("id-token", "device-secret")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("invalid_grant") == true)
        }

    @Test
    fun start_WhenEndpointUnavailable_ReturnsFailure() =
        runTest {
            val mockFlow =
                object : TokenExchangeFlow {
                    override suspend fun start(
                        idToken: String,
                        deviceSecret: String,
                        audience: String?,
                        scope: String?,
                    ): Result<TokenInfo> = Result.failure(IllegalStateException("OIDC Endpoints not available."))
                }

            val result = mockFlow.start("id-token", "device-secret")

            assertTrue(result.isFailure)
        }

    @Test
    fun start_WithAudience_PassesAudience() =
        runTest {
            var capturedAudience: String? = null
            val mockFlow =
                object : TokenExchangeFlow {
                    override suspend fun start(
                        idToken: String,
                        deviceSecret: String,
                        audience: String?,
                        scope: String?,
                    ): Result<TokenInfo> {
                        capturedAudience = audience
                        return Result.success(FakeTokenInfo())
                    }
                }

            mockFlow.start("id-token", "device-secret", audience = "api://custom")

            assertEquals("api://custom", capturedAudience)
        }
}
