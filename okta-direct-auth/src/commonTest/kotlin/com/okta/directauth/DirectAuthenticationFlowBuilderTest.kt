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
package com.okta.directauth

import com.okta.authfoundation.ChallengeGrantType
import com.okta.authfoundation.GrantType
import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.api.http.ApiResponse
import com.okta.authfoundation.api.log.AuthFoundationLogger
import com.okta.authfoundation.api.log.LogLevel
import com.okta.authfoundation.client.OidcClock
import com.okta.directauth.model.DirectAuthenticationIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DirectAuthenticationFlowBuilderTest {
    private val issuerUrl = "https://example.okta.com"
    private val clientId = "test_client_id"
    private val scope = listOf("openid", "email", "profile")

    @Test
    fun create_withRequiredParameters_usesDefaultValues() {
        val result = DirectAuthenticationFlowBuilder.create(issuerUrl, clientId, scope)
        assertTrue(result.isSuccess)

        val flow = result.getOrThrow() as DirectAuthenticationFlowImpl
        val context = flow.context

        assertEquals(issuerUrl, context.issuerUrl)
        assertEquals(clientId, context.clientId)
        assertEquals(scope, context.scope)
        assertEquals("", context.authorizationServerId)
        assertEquals("", context.clientSecret)
        assertEquals(DirectAuthenticationIntent.SIGN_IN, context.directAuthenticationIntent)
        assertEquals(
            listOf(GrantType.Password, GrantType.Oob, GrantType.Otp, ChallengeGrantType.OobMfa, ChallengeGrantType.OtpMfa, GrantType.WebAuthn, ChallengeGrantType.WebAuthnMfa),
            context.grantTypes
        )
        assertEquals(emptyList(), context.acrValues)
        assertEquals(emptyMap(), context.additionalParameters)
    }

    @Test
    fun create_withCustomParameters_usesCustomValues() {
        val customAuthServerId = "default"
        val customClientSecret = "custom_secret"
        val customIntent = DirectAuthenticationIntent.RECOVERY
        val customGrantTypes = listOf(GrantType.Password)
        val customAcrValues = listOf("phr", "phrh")
        val customApiExecutor =
            object : ApiExecutor {
                override suspend fun execute(request: ApiRequest): Result<ApiResponse> = throw NotImplementedError()
            }
        val customLogger =
            object : AuthFoundationLogger {
                override fun write(
                    message: String,
                    tr: Throwable?,
                    logLevel: LogLevel,
                ) {}
            }
        val customClock = OidcClock { 123456L }
        val customParams = mapOf("custom" to "value")

        val result =
            DirectAuthenticationFlowBuilder.create(issuerUrl, clientId, scope) {
                authorizationServerId = customAuthServerId
                clientSecret = customClientSecret
                directAuthenticationIntent = customIntent
                supportedGrantType = customGrantTypes
                acrValues = customAcrValues
                apiExecutor = customApiExecutor
                logger = customLogger
                clock = customClock
                additionalParameter = customParams
            }

        assertTrue(result.isSuccess)

        val flow = result.getOrThrow() as DirectAuthenticationFlowImpl
        val context = flow.context

        assertEquals(customAuthServerId, context.authorizationServerId)
        assertEquals(customClientSecret, context.clientSecret)
        assertEquals(customIntent, context.directAuthenticationIntent)
        assertEquals(customGrantTypes, context.grantTypes)
        assertEquals(customAcrValues, context.acrValues)
        assertEquals(customApiExecutor, context.apiExecutor)
        assertEquals(customLogger, context.logger)
        assertEquals(customClock, context.clock)
        assertEquals(customParams, context.additionalParameters)
    }

    @Test
    fun create_withInvalidIssuerUrl_returnsFailure() {
        val result = DirectAuthenticationFlowBuilder.create("http://not-https.com", clientId, scope)
        assertTrue(result.isFailure)

        val exception = result.exceptionOrNull()

        assertIs<IllegalArgumentException>(exception)
        assertEquals("issuerUrl must be a valid https URL.", exception.message)
    }

    @Test
    fun create_withBlankIssuerUrl_returnsFailure() {
        val result = DirectAuthenticationFlowBuilder.create(" ", clientId, scope)
        assertTrue(result.isFailure)

        val exception = result.exceptionOrNull()

        assertIs<IllegalArgumentException>(exception)
        assertEquals("issuerUrl must be a valid https URL.", exception.message)
    }

    @Test
    fun create_withBlankClientId_returnsFailure() {
        val result = DirectAuthenticationFlowBuilder.create(issuerUrl, " ", scope)
        assertTrue(result.isFailure)

        val exception = result.exceptionOrNull()

        assertIs<IllegalArgumentException>(exception)
        assertEquals("clientId must be set and not empty.", exception.message)
    }

    @Test
    fun create_withEmptyScope_returnsFailure() {
        val result = DirectAuthenticationFlowBuilder.create(issuerUrl, clientId, emptyList())
        assertTrue(result.isFailure)

        val exception = result.exceptionOrNull()

        assertIs<IllegalArgumentException>(exception)
        assertEquals("scope must be set and not empty.", exception.message)
    }
}
