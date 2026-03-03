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

import com.okta.authfoundation.GrantType
import com.okta.authfoundation.api.http.ApiRequestMethod
import com.okta.authfoundation.api.http.KtorHttpExecutor
import com.okta.authfoundation.api.log.AuthFoundationLogger
import com.okta.authfoundation.api.log.LogLevel
import com.okta.directauth.http.handlers.PrimaryAuthenticateStepHandler
import com.okta.directauth.model.DirectAuthContinuation
import com.okta.directauth.model.DirectAuthenticationContext
import com.okta.directauth.model.DirectAuthenticationError
import com.okta.directauth.model.DirectAuthenticationIntent
import com.okta.directauth.notJsonMockEngine
import com.okta.directauth.oAuth2ErrorMockEngine
import com.okta.directauth.primaryAuthenticateWebAuthnResponseMockEngine
import com.okta.directauth.serverErrorMockEngine
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DirectAuthPrimaryAuthenticateRequestTest {
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
                grantTypes = listOf(GrantType.Password, GrantType.WebAuthn),
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
                clock = { 1654041600 },
                additionalParameters = mapOf()
            )
    }

    @Test
    fun primaryAuthenticateRequest_withDefaultParameters() {
        val request = DirectAuthPrimaryAuthenticateRequest(context, "test_user")

        assertEquals("https://example.okta.com/oauth2/v1/primary-authenticate", request.url())
        assertEquals(ApiRequestMethod.POST, request.method())
        assertEquals("application/x-www-form-urlencoded", request.contentType())
        assertTrue(request.headers().containsKey("Accept"))
        assertEquals(listOf("application/json"), request.headers()["Accept"])
        assertEquals(listOf(userAgentValue()), request.headers()["User-Agent"])
        assertNull(request.query())

        val formParameters = request.formParameters()
        assertEquals(listOf("test_client_id"), formParameters["client_id"])
        assertEquals(listOf("test_user"), formParameters["login_hint"])
        assertEquals(listOf(GrantType.WebAuthn.value), formParameters["challenge_hint"])
        assertEquals(listOf("openid email profile offline_access"), formParameters["scope"])
        assertEquals(listOf("${GrantType.Password.value} ${GrantType.WebAuthn.value}"), formParameters["grant_types_supported"])
        assertFalse(formParameters.containsKey("client_secret"))
    }

    @Test
    fun primaryAuthenticateRequest_withCustomAuthServerId() {
        val customContext = context.copy(authorizationServerId = "aus_test_id", clientSecret = "test_secret", additionalParameters = mapOf("custom" to "value"))
        val request = DirectAuthPrimaryAuthenticateRequest(customContext, "test_user")

        assertEquals("https://example.okta.com/oauth2/aus_test_id/v1/primary-authenticate", request.url())
        assertEquals(mapOf("custom" to "value"), request.query())

        val formParameters = request.formParameters()
        assertEquals(listOf("test_secret"), formParameters["client_secret"])
        assertEquals(listOf("test_client_id"), formParameters["client_id"])
        assertEquals(listOf("test_user"), formParameters["login_hint"])
    }

    @Test
    fun primaryAuthenticateRequest_returnsWebAuthnContinuationOnSuccess() =
        runTest {
            val request = DirectAuthPrimaryAuthenticateRequest(context, "test_user")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(primaryAuthenticateWebAuthnResponseMockEngine)))

            val state = PrimaryAuthenticateStepHandler(request, testContext).process()

            assertIs<DirectAuthContinuation.WebAuthn>(state)
            assertTrue(state.challengeData().getOrThrow().contains("challenge"))
        }

    @Test
    fun primaryAuthenticateRequest_returnsOauth2ErrorOnApiError() =
        runTest {
            val request = DirectAuthPrimaryAuthenticateRequest(context, "test_user")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(oAuth2ErrorMockEngine)))

            val state = PrimaryAuthenticateStepHandler(request, testContext).process()

            assertIs<DirectAuthenticationError.HttpError.Oauth2Error>(state)
            assertEquals("invalid_grant", state.error)
        }

    @Test
    fun primaryAuthenticateRequest_returnsApiErrorOnServerError() =
        runTest {
            val request = DirectAuthPrimaryAuthenticateRequest(context, "test_user")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(serverErrorMockEngine)))

            val state = PrimaryAuthenticateStepHandler(request, testContext).process()

            assertIs<DirectAuthenticationError.HttpError.ApiError>(state)
            assertEquals("E00000", state.errorCode)
            assertEquals(HttpStatusCode.InternalServerError, state.httpStatusCode)
        }

    @Test
    fun primaryAuthenticateRequest_returnsInternalErrorOnUnsupportedContentType() =
        runTest {
            val request = DirectAuthPrimaryAuthenticateRequest(context, "test_user")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(notJsonMockEngine)))

            val state = PrimaryAuthenticateStepHandler(request, testContext).process()

            assertIs<DirectAuthenticationError.InternalError>(state)
            assertEquals(UNSUPPORTED_CONTENT_TYPE, state.errorCode)
        }
}
