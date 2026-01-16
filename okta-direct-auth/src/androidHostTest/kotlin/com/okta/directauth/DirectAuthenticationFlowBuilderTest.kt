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
import com.okta.authfoundation.api.http.log.AuthFoundationLogger
import com.okta.authfoundation.api.http.log.LogLevel
import com.okta.authfoundation.client.OidcClock
import com.okta.directauth.model.DirectAuthenticationIntent
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class DirectAuthenticationFlowBuilderTest {
    private val issuerUrl = "https://example.okta.com"
    private val clientId = "test_client_id"
    private val scope = listOf("openid", "email", "profile")

    @Test
    fun create_withRequiredParameters_usesDefaultValues() {
        val result = DirectAuthenticationFlowBuilder.create(issuerUrl, clientId, scope)
        assertThat(result.isSuccess, equalTo(true))

        val flow = result.getOrThrow() as DirectAuthenticationFlowImpl
        val context = flow.context

        assertThat(context.issuerUrl, equalTo(issuerUrl))
        assertThat(context.clientId, equalTo(clientId))
        assertThat(context.scope, equalTo(scope))
        assertThat(context.authorizationServerId, equalTo(""))
        assertThat(context.clientSecret, equalTo(""))
        assertThat(context.directAuthenticationIntent, equalTo(DirectAuthenticationIntent.SIGN_IN))
        assertThat(
            context.grantTypes,
            equalTo(listOf(GrantType.Password, GrantType.Oob, GrantType.Otp, ChallengeGrantType.OobMfa, ChallengeGrantType.OtpMfa, GrantType.WebAuthn, ChallengeGrantType.WebAuthnMfa))
        )
        assertThat(context.acrValues, equalTo(emptyList()))
        assertThat(context.additionalParameters, equalTo(emptyMap()))
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

        assertThat(result.isSuccess, equalTo(true))

        val flow = result.getOrThrow() as DirectAuthenticationFlowImpl
        val context = flow.context

        assertThat(context.authorizationServerId, equalTo(customAuthServerId))
        assertThat(context.clientSecret, equalTo(customClientSecret))
        assertThat(context.directAuthenticationIntent, equalTo(customIntent))
        assertThat(context.grantTypes, equalTo(customGrantTypes))
        assertThat(context.acrValues, equalTo(customAcrValues))
        assertThat(context.apiExecutor, equalTo(customApiExecutor))
        assertThat(context.logger, equalTo(customLogger))
        assertThat(context.clock, equalTo(customClock))
        assertThat(context.additionalParameters, equalTo(customParams))
    }

    @Test
    fun create_withInvalidIssuerUrl_returnsFailure() {
        val result = DirectAuthenticationFlowBuilder.create("http://not-https.com", clientId, scope)
        assertThat(result.isFailure, equalTo(true))

        val exception = result.exceptionOrNull()

        assertThat(exception, instanceOf(IllegalArgumentException::class.java))
        assertThat(exception?.message, equalTo("issuerUrl must be a valid https URL."))
    }

    @Test
    fun create_withBlankIssuerUrl_returnsFailure() {
        val result = DirectAuthenticationFlowBuilder.create(" ", clientId, scope)
        assertThat(result.isFailure, equalTo(true))

        val exception = result.exceptionOrNull()

        assertThat(exception, instanceOf(IllegalArgumentException::class.java))
        assertThat(exception?.message, equalTo("issuerUrl must be a valid https URL."))
    }

    @Test
    fun create_withBlankClientId_returnsFailure() {
        val result = DirectAuthenticationFlowBuilder.create(issuerUrl, " ", scope)
        assertThat(result.isFailure, equalTo(true))

        val exception = result.exceptionOrNull()

        assertThat(exception, instanceOf(IllegalArgumentException::class.java))
        assertThat(exception?.message, equalTo("clientId must be set and not empty."))
    }

    @Test
    fun create_withEmptyScope_returnsFailure() {
        val result = DirectAuthenticationFlowBuilder.create(issuerUrl, clientId, emptyList())
        assertThat(result.isFailure, equalTo(true))

        val exception = result.exceptionOrNull()

        assertThat(exception, instanceOf(IllegalArgumentException::class.java))
        assertThat(exception?.message, equalTo("scope must be set and not empty."))
    }
}
