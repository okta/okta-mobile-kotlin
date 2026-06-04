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

class SessionTokenFlowTest {
    @Test
    fun start_WhenSuccess_ReturnsTokenInfo() =
        runTest {
            val fakeToken = FakeSessionTokenInfo()
            val mockFlow =
                object : SessionTokenFlow {
                    override suspend fun start(
                        sessionToken: String,
                        redirectUrl: String,
                        extraRequestParameters: Map<String, String>,
                        scope: String,
                    ): Result<TokenInfo> = Result.success(fakeToken)
                }

            val result = mockFlow.start("example-session-token", "com.example.app:/callback")

            assertTrue(result.isSuccess)
            val tokenInfo = result.getOrNull()
            assertNotNull(tokenInfo)
            assertEquals("Bearer", tokenInfo.tokenType)
            assertEquals("test-access-token", tokenInfo.accessToken)
        }

    @Test
    fun start_WhenEndpointUnavailable_ReturnsFailure() =
        runTest {
            val mockFlow =
                object : SessionTokenFlow {
                    override suspend fun start(
                        sessionToken: String,
                        redirectUrl: String,
                        extraRequestParameters: Map<String, String>,
                        scope: String,
                    ): Result<TokenInfo> = Result.failure(IllegalStateException("OIDC Endpoints not available."))
                }

            val result = mockFlow.start("example-session-token", "com.example.app:/callback")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("OIDC Endpoints not available.") == true)
        }

    @Test
    fun start_WhenNoLocationHeader_ReturnsFailure() =
        runTest {
            val mockFlow =
                object : SessionTokenFlow {
                    override suspend fun start(
                        sessionToken: String,
                        redirectUrl: String,
                        extraRequestParameters: Map<String, String>,
                        scope: String,
                    ): Result<TokenInfo> = Result.failure(IllegalStateException("No location header in response."))
                }

            val result = mockFlow.start("example-session-token", "com.example.app:/callback")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("No location header") == true)
        }

    @Test
    fun start_WithExtraParameters_ReturnsTokenInfo() =
        runTest {
            var capturedExtraParams: Map<String, String>? = null
            val mockFlow =
                object : SessionTokenFlow {
                    override suspend fun start(
                        sessionToken: String,
                        redirectUrl: String,
                        extraRequestParameters: Map<String, String>,
                        scope: String,
                    ): Result<TokenInfo> {
                        capturedExtraParams = extraRequestParameters
                        return Result.success(FakeSessionTokenInfo())
                    }
                }

            mockFlow.start(
                sessionToken = "example-session-token",
                redirectUrl = "com.example.app:/callback",
                extraRequestParameters = mapOf("extraOne" to "bar"),
                scope = "openid profile email"
            )

            assertNotNull(capturedExtraParams)
            assertEquals("bar", capturedExtraParams["extraOne"])
        }
}

private class FakeSessionTokenInfo : TokenInfo {
    override val id: String = "test-id"
    override val clientId: String = "test-client"
    override val issuerUrl: String = "https://example.okta.com"
    override val tokenType: String = "Bearer"
    override val expiresIn: Int = 3600
    override val accessToken: String = "test-access-token"
    override val scope: String? = "openid profile"
    override val refreshToken: String? = "test-refresh-token"
    override val idToken: String? = null
    override val deviceSecret: String? = null
    override val issuedTokenType: String? = null
}
