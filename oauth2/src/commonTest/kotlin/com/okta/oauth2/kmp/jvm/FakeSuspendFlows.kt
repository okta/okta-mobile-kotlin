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
import com.okta.oauth2.kmp.AuthorizationCodeFlowContext
import com.okta.oauth2.kmp.BrowserRedirectHandler
import com.okta.oauth2.kmp.DeviceAuthorizationFlowContext
import com.okta.oauth2.kmp.FakeTokenInfo
import com.okta.oauth2.kmp.RedirectEndSessionFlowContext
import com.okta.oauth2.kmp.AuthorizationCodeFlow as KotlinAuthorizationCodeFlow
import com.okta.oauth2.kmp.DeviceAuthorizationFlow as KotlinDeviceAuthorizationFlow
import com.okta.oauth2.kmp.RedirectEndSessionFlow as KotlinRedirectEndSessionFlow
import com.okta.oauth2.kmp.ResourceOwnerFlow as KotlinResourceOwnerFlow
import com.okta.oauth2.kmp.SessionTokenFlow as KotlinSessionTokenFlow
import com.okta.oauth2.kmp.TokenExchangeFlow as KotlinTokenExchangeFlow

/**
 * Kotlin bridge providing fake suspend-interface implementations for Java tests.
 *
 * Java test files cannot implement Kotlin suspend interfaces directly,
 * so this Kotlin object provides the anonymous-object delegates that the
 * Java [TestFlowFactory] passes to each flow's constructor.
 */
internal object FakeSuspendFlows {
    @JvmStatic
    fun successResourceOwnerDelegate(): KotlinResourceOwnerFlow =
        object : KotlinResourceOwnerFlow {
            override suspend fun start(
                username: String,
                password: String,
                scope: String,
            ): Result<TokenInfo> = Result.success(FakeTokenInfo())
        }

    @JvmStatic
    fun failingResourceOwnerDelegate(): KotlinResourceOwnerFlow =
        object : KotlinResourceOwnerFlow {
            override suspend fun start(
                username: String,
                password: String,
                scope: String,
            ): Result<TokenInfo> = Result.failure(IllegalArgumentException("invalid_grant"))
        }

    @JvmStatic
    fun successDeviceAuthorizationDelegate(): KotlinDeviceAuthorizationFlow =
        object : KotlinDeviceAuthorizationFlow {
            override suspend fun start(scope: String): Result<DeviceAuthorizationFlowContext> =
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

            override suspend fun resume(flowContext: DeviceAuthorizationFlowContext): Result<TokenInfo> = Result.success(FakeTokenInfo())
        }

    @JvmStatic
    fun failingDeviceAuthorizationDelegate(): KotlinDeviceAuthorizationFlow =
        object : KotlinDeviceAuthorizationFlow {
            override suspend fun start(scope: String): Result<DeviceAuthorizationFlowContext> = Result.failure(IllegalStateException("OIDC Endpoints not available."))

            override suspend fun resume(flowContext: DeviceAuthorizationFlowContext): Result<TokenInfo> = Result.failure(IllegalStateException("access_denied"))
        }

    @JvmStatic
    fun successTokenExchangeDelegate(): KotlinTokenExchangeFlow =
        object : KotlinTokenExchangeFlow {
            override suspend fun start(
                idToken: String,
                deviceSecret: String,
                audience: String?,
                scope: String,
            ): Result<TokenInfo> = Result.success(FakeTokenInfo())
        }

    @JvmStatic
    fun failingTokenExchangeDelegate(): KotlinTokenExchangeFlow =
        object : KotlinTokenExchangeFlow {
            override suspend fun start(
                idToken: String,
                deviceSecret: String,
                audience: String?,
                scope: String,
            ): Result<TokenInfo> = Result.failure(IllegalStateException("invalid_grant"))
        }

    @JvmStatic
    fun successSessionTokenDelegate(): KotlinSessionTokenFlow =
        object : KotlinSessionTokenFlow {
            override suspend fun start(
                sessionToken: String,
                redirectUrl: String,
                extraRequestParameters: Map<String, String>,
                scope: String,
            ): Result<TokenInfo> = Result.success(FakeTokenInfo())
        }

    @JvmStatic
    fun failingSessionTokenDelegate(): KotlinSessionTokenFlow =
        object : KotlinSessionTokenFlow {
            override suspend fun start(
                sessionToken: String,
                redirectUrl: String,
                extraRequestParameters: Map<String, String>,
                scope: String,
            ): Result<TokenInfo> = Result.failure(IllegalStateException("OIDC Endpoints not available."))
        }

    @JvmStatic
    fun successRedirectEndSessionDelegate(redirectUri: String): KotlinRedirectEndSessionFlow =
        object : KotlinRedirectEndSessionFlow {
            override suspend fun start(
                idToken: String,
                redirectUrl: String,
                extraRequestParameters: Map<String, String>,
            ): Result<RedirectEndSessionFlowContext> =
                Result.success(
                    RedirectEndSessionFlowContext(
                        url = "https://example.okta.com/logout?id_token_hint=$idToken",
                        redirectUrl = redirectUri,
                        state = "test-state"
                    )
                )

            override fun resume(
                uri: String,
                flowContext: RedirectEndSessionFlowContext,
            ): Result<Unit> = Result.success(Unit)
        }

    @JvmStatic
    fun failingRedirectEndSessionDelegate(): KotlinRedirectEndSessionFlow =
        object : KotlinRedirectEndSessionFlow {
            override suspend fun start(
                idToken: String,
                redirectUrl: String,
                extraRequestParameters: Map<String, String>,
            ): Result<RedirectEndSessionFlowContext> = Result.failure(IllegalStateException("OIDC Endpoints not available."))

            override fun resume(
                uri: String,
                flowContext: RedirectEndSessionFlowContext,
            ): Result<Unit> = Result.failure(KotlinRedirectEndSessionFlow.ResumeException("access_denied"))
        }

    @JvmStatic
    fun fakeBrowserRedirectHandler(redirectUri: String): BrowserRedirectHandler =
        object : BrowserRedirectHandler {
            override suspend fun handleRedirect(url: String): String = redirectUri
        }

    @JvmStatic
    fun successAuthorizationCodeDelegate(): KotlinAuthorizationCodeFlow =
        object : KotlinAuthorizationCodeFlow {
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

    @JvmStatic
    fun failingAuthorizationCodeDelegate(): KotlinAuthorizationCodeFlow =
        object : KotlinAuthorizationCodeFlow {
            override suspend fun start(
                redirectUrl: String,
                extraRequestParameters: Map<String, String>,
                scope: String,
            ): Result<AuthorizationCodeFlowContext> = Result.failure(IllegalStateException("OIDC Endpoints not available."))

            override suspend fun resume(
                uri: String,
                flowContext: AuthorizationCodeFlowContext,
            ): Result<TokenInfo> = Result.failure(IllegalStateException("OIDC Endpoints not available."))
        }
}
