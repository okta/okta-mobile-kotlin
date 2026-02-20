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
import com.okta.directauth.http.handlers.OobStepHandler
import com.okta.directauth.model.BindingMethod
import com.okta.directauth.model.DirectAuthContinuation
import com.okta.directauth.model.DirectAuthenticationContext
import com.okta.directauth.model.DirectAuthenticationError
import com.okta.directauth.model.DirectAuthenticationIntent
import com.okta.directauth.model.OobChannel
import com.okta.directauth.notJsonMockEngine
import com.okta.directauth.oobAuthenticateEmailResponseMockEngine
import com.okta.directauth.oobAuthenticateInvalidBindingResponseMockEngine
import com.okta.directauth.oobAuthenticateOauth2ErrorMockEngine
import com.okta.directauth.oobAuthenticatePushResponseMockEngine
import com.okta.directauth.oobAuthenticateSmsResponseMockEngine
import com.okta.directauth.oobAuthenticateTransferNoBindingCodeResponseMockEngine
import com.okta.directauth.oobAuthenticateTransferResponseMockEngine
import com.okta.directauth.oobAuthenticateVoiceResponseMockEngine
import com.okta.directauth.serverErrorMockEngine
import com.okta.directauth.unknownJsonTypeMockEngine
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

class DirectAuthOobAuthenticateRequestTest {
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
    fun oobAuthenticateRequest_WithDefaultParameters() {
        val request =
            DirectAuthOobAuthenticateRequest(
                context = context,
                loginHint = "test_user",
                oobChannel = OobChannel.PUSH
            )

        assertEquals("https://example.okta.com/oauth2/v1/oob-authenticate", request.url())
        assertEquals(ApiRequestMethod.POST, request.method())
        assertEquals("application/x-www-form-urlencoded", request.contentType())
        assertTrue(request.headers().containsKey("Accept"))
        assertEquals(listOf("application/json"), request.headers()["Accept"])
        assertTrue(request.headers().containsKey("User-Agent"))
        assertNull(request.query())

        val formParameters = request.formParameters()
        assertEquals(listOf("test_client_id"), formParameters["client_id"])
        assertEquals(listOf("test_user"), formParameters["login_hint"])
        assertEquals(listOf("push"), formParameters["channel_hint"])
        assertFalse(formParameters.containsKey("client_secret"))
    }

    @Test
    fun oobAuthenticateRequest_WithCustomParameters() {
        val customContext =
            context.copy(
                authorizationServerId = "aus_test_id",
                clientSecret = "test_client_secret",
                additionalParameters = mapOf("custom" to "value")
            )

        val request =
            DirectAuthOobAuthenticateRequest(
                context = customContext,
                loginHint = "test_user",
                oobChannel = OobChannel.SMS
            )

        assertEquals("https://example.okta.com/oauth2/aus_test_id/v1/oob-authenticate", request.url())
        assertEquals(mapOf("custom" to "value"), request.query())

        val formParameters = request.formParameters()
        assertEquals(listOf("test_client_id"), formParameters["client_id"])
        assertEquals(listOf("test_user"), formParameters["login_hint"])
        assertEquals(listOf("sms"), formParameters["channel_hint"])
        assertEquals(listOf("test_client_secret"), formParameters["client_secret"])
    }

    @Test
    fun oobAuthenticateRequest_returnsPollStateWhenUsingPushChannel() =
        runTest {
            val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.PUSH)
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(oobAuthenticatePushResponseMockEngine)))

            val state = OobStepHandler(request, testContext).process()

            assertIs<DirectAuthContinuation.OobPending>(state)
            assertEquals("example_oob_code", state.bindingContext.oobCode)
            assertEquals(OobChannel.PUSH, state.bindingContext.channel)
            assertEquals(120, state.bindingContext.expiresIn)
            assertEquals(5, state.bindingContext.interval)
            assertEquals(BindingMethod.NONE, state.bindingContext.bindingMethod)
            assertNull(state.bindingContext.bindingCode)
        }

    @Test
    fun oobAuthenticateRequest_returnsPromptStateWhenUsingSmsChannel() =
        runTest {
            val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.SMS)
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(oobAuthenticateSmsResponseMockEngine)))

            val state = OobStepHandler(request, testContext).process()

            assertIs<DirectAuthContinuation.Prompt>(state)
            assertEquals("example_oob_code", state.bindingContext.oobCode)
            assertEquals(OobChannel.SMS, state.bindingContext.channel)
            assertEquals(120, state.bindingContext.expiresIn)
            assertNull(state.bindingContext.interval)
            assertEquals(BindingMethod.PROMPT, state.bindingContext.bindingMethod)
            assertNull(state.bindingContext.bindingCode)
        }

    @Test
    fun oobAuthenticateRequest_returnsPromptStateWhenUsingVoiceChannel() =
        runTest {
            val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.VOICE)
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(oobAuthenticateVoiceResponseMockEngine)))

            val state = OobStepHandler(request, testContext).process()

            assertIs<DirectAuthContinuation.Prompt>(state)
            assertEquals("example_oob_code", state.bindingContext.oobCode)
            assertEquals(OobChannel.VOICE, state.bindingContext.channel)
            assertEquals(120, state.bindingContext.expiresIn)
            assertNull(state.bindingContext.interval)
            assertEquals(BindingMethod.PROMPT, state.bindingContext.bindingMethod)
            assertNull(state.bindingContext.bindingCode)
        }

    @Test
    fun oobAuthenticateRequest_returnsTransferStateWhenUsingPushWithNumberChallenge() =
        runTest {
            val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.PUSH)
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(oobAuthenticateTransferResponseMockEngine)))

            val state = OobStepHandler(request, testContext).process()

            assertIs<DirectAuthContinuation.Transfer>(state)
            assertEquals("example_oob_code", state.bindingContext.oobCode)
            assertEquals(OobChannel.PUSH, state.bindingContext.channel)
            assertEquals(120, state.bindingContext.expiresIn)
            assertEquals(5, state.bindingContext.interval)
            assertEquals(BindingMethod.TRANSFER, state.bindingContext.bindingMethod)
            assertEquals("95", state.bindingContext.bindingCode)
        }

    @Test
    fun oobAuthenticateRequest_returnsInternalErrorWhenBindingCodeIsMissing() =
        runTest {
            val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.PUSH)
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(oobAuthenticateTransferNoBindingCodeResponseMockEngine)))

            val state = OobStepHandler(request, testContext).process()

            assertIs<DirectAuthenticationError.InternalError>(state)
            assertEquals(EXCEPTION, state.errorCode)
            assertEquals("binding_method: transfer without binding_code", state.description)
        }

    @Test
    fun oobAuthenticateRequest_returnsInternalErrorStateWhenUnsupportedChannelReturned() =
        runTest {
            val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.SMS)
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(oobAuthenticateEmailResponseMockEngine)))

            val state = OobStepHandler(request, testContext).process()

            assertIs<DirectAuthenticationError.InternalError>(state)
            assertEquals(EXCEPTION, state.errorCode)
            assertEquals("Unknown OOB channel: email", state.description)
        }

    @Test
    fun oobAuthenticateRequest_returnsInternalErrorStateWhenUnsupportedBindingMethodReturned() =
        runTest {
            val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.SMS)
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(oobAuthenticateInvalidBindingResponseMockEngine)))

            val state = OobStepHandler(request, testContext).process()

            assertIs<DirectAuthenticationError.InternalError>(state)
            assertEquals(EXCEPTION, state.errorCode)
            assertEquals("Unknown binding method: bluetooth", state.description)
        }

    @Test
    fun oobAuthenticateRequest_returnsOauth2ErrorStateOnApiError() =
        runTest {
            val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.PUSH)
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(oobAuthenticateOauth2ErrorMockEngine)))

            val state = OobStepHandler(request, testContext).process()

            assertIs<DirectAuthenticationError.HttpError.Oauth2Error>(state)
            assertEquals("invalid_request", state.error)
            assertEquals("abc is not a valid channel hint", state.errorDescription)
        }

    @Test
    fun oobAuthenticateRequest_returnsApiErrorStateOnServerError() =
        runTest {
            val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.PUSH)
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(serverErrorMockEngine)))

            val state = OobStepHandler(request, testContext).process()

            assertIs<DirectAuthenticationError.HttpError.ApiError>(state)
            assertEquals("E00000", state.errorCode)
            assertEquals("Internal Server Error", state.errorSummary)
            assertEquals(HttpStatusCode.InternalServerError, state.httpStatusCode)
        }

    @Test
    fun oobAuthenticateRequest_returnsInternalErrorOnUnsupportedContentType() =
        runTest {
            val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.PUSH)
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(notJsonMockEngine)))

            val state = OobStepHandler(request, testContext).process()

            assertIs<DirectAuthenticationError.InternalError>(state)
            assertEquals(UNSUPPORTED_CONTENT_TYPE, state.errorCode)
            assertIs<IllegalStateException>(state.throwable)
            assertEquals("Unsupported content type: text/plain", state.throwable.message)
        }

    @Test
    fun oobAuthenticateRequest_unparseableClientErrorResponse() =
        runTest {
            val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.PUSH)
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(unknownJsonTypeMockEngine)))

            val state = OobStepHandler(request, testContext).process()

            assertIs<DirectAuthenticationError.InternalError>(state)
            assertEquals(INVALID_RESPONSE, state.errorCode)
            assertIs<IllegalStateException>(state.throwable)
            assertEquals("No parsable error response body: HTTP ${HttpStatusCode.BadRequest}", state.throwable.message)
        }
}
