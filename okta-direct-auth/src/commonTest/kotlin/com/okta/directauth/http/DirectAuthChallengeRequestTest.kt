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
package com.okta.directauth.http

import com.okta.authfoundation.ChallengeGrantType
import com.okta.authfoundation.GrantType
import com.okta.authfoundation.api.http.ApiRequestMethod
import com.okta.authfoundation.api.http.KtorHttpExecutor
import com.okta.authfoundation.api.log.AuthFoundationLogger
import com.okta.authfoundation.api.log.LogLevel
import com.okta.directauth.model.DirectAuthenticationContext
import com.okta.directauth.model.DirectAuthenticationIntent
import com.okta.directauth.model.MfaContext
import com.okta.directauth.model.OobChannel
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DirectAuthChallengeRequestTest {
    private lateinit var context: DirectAuthenticationContext

    @BeforeTest
    fun setUp() {
        context =
            DirectAuthenticationContext(
                issuerUrl = "https://example.okta.com",
                clientId = "test_client_id",
                scope = listOf("openid", "email", "profile", "offline_access"),
                authorizationServerId = "",
                clientSecret = "",
                grantTypes = listOf(GrantType.Password, GrantType.Otp),
                acrValues = emptyList(),
                directAuthenticationIntent = DirectAuthenticationIntent.SIGN_IN,
                apiExecutor = KtorHttpExecutor(),
                logger =
                    object : AuthFoundationLogger {
                        override fun write(
                            message: String,
                            tr: Throwable?,
                            logLevel: LogLevel,
                        ) {
                            // No-op logger for tests
                        }
                    },
                clock = { 1654041600 }, // 2022-06-01
                additionalParameters = mapOf()
            )
    }

    @Test
    fun challengeRequest_WithDefaultParameters() {
        val mfaContext = MfaContext(listOf(ChallengeGrantType.OobMfa, ChallengeGrantType.OtpMfa), "test_mfa_token")
        val request =
            DirectAuthChallengeRequest(
                context = context,
                mfaContext = mfaContext,
                challengeTypesSupported = listOf(ChallengeGrantType.OobMfa, ChallengeGrantType.OtpMfa),
                oobChannel = null
            )

        assertEquals("https://example.okta.com/oauth2/v1/challenge", request.url())
        assertEquals(ApiRequestMethod.POST, request.method())
        assertEquals("application/x-www-form-urlencoded", request.contentType())
        assertEquals(listOf("application/json"), request.headers()["Accept"])
        assertEquals(listOf(userAgentValue()), request.headers()["User-Agent"])
        assertNull(request.query())

        val formParameters = request.formParameters()
        assertEquals(listOf("test_client_id"), formParameters["client_id"])
        assertEquals(listOf("test_mfa_token"), formParameters["mfa_token"])
        assertEquals(listOf("${ChallengeGrantType.OobMfa.value} ${ChallengeGrantType.OtpMfa.value}"), formParameters["challenge_types_supported"])
        assertFalse(formParameters.containsKey("client_secret"))
        assertFalse(formParameters.containsKey("channel_hint"))
    }

    @Test
    fun challengeRequest_WithTrailingSlashInIssuerUrl() {
        val trailingSlashContext = context.copy(issuerUrl = "https://example.okta.com/")
        val mfaContext = MfaContext(listOf(ChallengeGrantType.OobMfa, ChallengeGrantType.OtpMfa), "test_mfa_token")
        val request =
            DirectAuthChallengeRequest(
                context = trailingSlashContext,
                mfaContext = mfaContext,
                challengeTypesSupported = listOf(ChallengeGrantType.OobMfa, ChallengeGrantType.OtpMfa),
                oobChannel = null
            )

        assertEquals("https://example.okta.com/oauth2/v1/challenge", request.url())
    }

    @Test
    fun challengeRequest_WithCustomParameters() {
        val customContext =
            context.copy(
                authorizationServerId = "aus_test_id",
                clientSecret = "test_client_secret",
                additionalParameters = mapOf("custom" to "value")
            )

        val mfaContext = MfaContext(listOf(ChallengeGrantType.OobMfa, ChallengeGrantType.OtpMfa), "test_mfa_token")
        val request =
            DirectAuthChallengeRequest(
                context = customContext,
                mfaContext = mfaContext,
                challengeTypesSupported = listOf(ChallengeGrantType.OobMfa, ChallengeGrantType.OtpMfa, ChallengeGrantType.WebAuthnMfa),
                oobChannel = OobChannel.PUSH
            )

        assertEquals("https://example.okta.com/oauth2/aus_test_id/v1/challenge", request.url())
        assertEquals(mapOf("custom" to "value"), request.query())

        val formParameters = request.formParameters()
        assertEquals(listOf("test_client_id"), formParameters["client_id"])
        assertEquals(listOf("test_client_secret"), formParameters["client_secret"])
        assertEquals(listOf("test_mfa_token"), formParameters["mfa_token"])
        assertEquals(listOf("${ChallengeGrantType.OobMfa.value} ${ChallengeGrantType.OtpMfa.value} ${ChallengeGrantType.WebAuthnMfa.value}"), formParameters["challenge_types_supported"])
        assertEquals(listOf("push"), formParameters["channel_hint"])
    }
}
