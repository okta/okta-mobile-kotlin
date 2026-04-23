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
package com.okta.oauth2.kmp.jvm

import com.okta.authfoundation.client.TokenInfo
import com.okta.oauth2.kmp.DeviceAuthorizationFlowContext
import com.okta.oauth2.kmp.DeviceAuthorizationFlow as KotlinDeviceAuthorizationFlow
import com.okta.oauth2.kmp.ResourceOwnerFlow as KotlinResourceOwnerFlow

/**
 * Factory for creating pre-built flow instances for Java wrapper tests.
 * Java test files cannot implement Kotlin suspend interfaces directly,
 * so this Kotlin file provides the bridge.
 */
object TestFlowFactory {
    @JvmStatic
    fun createSuccessDeviceAuthorizationFlow(): DeviceAuthorizationFlow =
        DeviceAuthorizationFlow(
            object : KotlinDeviceAuthorizationFlow {
                override suspend fun start(scope: String): Result<DeviceAuthorizationFlowContext> =
                    Result.success(
                        DeviceAuthorizationFlowContext(
                            verificationUri = "https://example.okta.com/activate",
                            verificationUriComplete = "https://example.okta.com/activate?user_code=ABCD-1234",
                            userCode = "ABCD-1234",
                            expiresIn = 600,
                            deviceCode = "dc-test-123",
                            interval = 5
                        )
                    )

                override suspend fun resume(flowContext: DeviceAuthorizationFlowContext): Result<TokenInfo> = Result.success(FakeTokenInfo())
            }
        )

    @JvmStatic
    fun createFailingDeviceAuthorizationFlow(): DeviceAuthorizationFlow =
        DeviceAuthorizationFlow(
            object : KotlinDeviceAuthorizationFlow {
                override suspend fun start(scope: String): Result<DeviceAuthorizationFlowContext> = Result.failure(IllegalStateException("OIDC Endpoints not available."))

                override suspend fun resume(flowContext: DeviceAuthorizationFlowContext): Result<TokenInfo> = Result.failure(IllegalStateException("access_denied"))
            }
        )

    @JvmStatic
    fun createSuccessResourceOwnerFlow(): ResourceOwnerFlow =
        ResourceOwnerFlow(
            object : KotlinResourceOwnerFlow {
                override suspend fun start(
                    username: String,
                    password: String,
                    scope: String,
                ): Result<TokenInfo> = Result.success(FakeTokenInfo())
            }
        )

    @JvmStatic
    fun createFailingResourceOwnerFlow(): ResourceOwnerFlow =
        ResourceOwnerFlow(
            object : KotlinResourceOwnerFlow {
                override suspend fun start(
                    username: String,
                    password: String,
                    scope: String,
                ): Result<TokenInfo> = Result.failure(IllegalArgumentException("invalid_grant"))
            }
        )
}

private class FakeTokenInfo : TokenInfo {
    override val id: String = "test-id"
    override val clientId: String = "test-client"
    override val issuerUrl: String = "https://example.okta.com"
    override val tokenType: String = "Bearer"
    override val expiresIn: Int = 3600
    override val accessToken: String = "test-access-token"
    override val scope: String? = "openid"
    override val refreshToken: String? = null
    override val idToken: String? = null
    override val deviceSecret: String? = null
    override val issuedTokenType: String? = null
}
