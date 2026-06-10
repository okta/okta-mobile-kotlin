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

class ResourceOwnerFlowTest {
    @Test
    fun start_WithValidCredentials_ReturnsTokenInfo() =
        runTest {
            val capturedParams = mutableMapOf<String, String>()
            val mockFlow =
                object : ResourceOwnerFlow {
                    override suspend fun start(
                        username: String,
                        password: String,
                        scope: String,
                    ): Result<TokenInfo> {
                        capturedParams["username"] = username
                        capturedParams["password"] = password
                        capturedParams["scope"] = scope
                        return Result.success(FakeTokenInfo())
                    }
                }

            val result = mockFlow.start("user@example.com", "secret", "openid profile")

            assertTrue(result.isSuccess)
            assertNotNull(result.getOrNull())
            assertEquals("user@example.com", capturedParams["username"])
            assertEquals("secret", capturedParams["password"])
            assertEquals("openid profile", capturedParams["scope"])
        }

    @Test
    fun start_WithInvalidCredentials_ReturnsFailure() =
        runTest {
            val mockFlow =
                object : ResourceOwnerFlow {
                    override suspend fun start(
                        username: String,
                        password: String,
                        scope: String,
                    ): Result<TokenInfo> = Result.failure(IllegalArgumentException("invalid_grant"))
                }

            val result = mockFlow.start("user@example.com", "wrong", "openid")

            assertTrue(result.isFailure)
            assertEquals("invalid_grant", result.exceptionOrNull()?.message)
        }

    @Test
    fun start_WithNetworkError_ReturnsFailure() =
        runTest {
            val mockFlow =
                object : ResourceOwnerFlow {
                    override suspend fun start(
                        username: String,
                        password: String,
                        scope: String,
                    ): Result<TokenInfo> = Result.failure(java.io.IOException("Network unreachable"))
                }

            val result = mockFlow.start("user@example.com", "pass", "openid")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is java.io.IOException)
        }
}
