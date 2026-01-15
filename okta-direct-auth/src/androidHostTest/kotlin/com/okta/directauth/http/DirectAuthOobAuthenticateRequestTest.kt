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
import com.okta.authfoundation.api.http.log.AuthFoundationLogger
import com.okta.authfoundation.api.http.log.LogLevel
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
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test

class DirectAuthOobAuthenticateRequestTest {
    private lateinit var context: DirectAuthenticationContext

    @Before
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

        assertThat(request.url(), equalTo("https://example.okta.com/oauth2/v1/oob-authenticate"))
        assertThat(request.method(), equalTo(ApiRequestMethod.POST))
        assertThat(request.contentType(), equalTo("application/x-www-form-urlencoded"))
        assertThat(request.headers(), equalTo(mapOf("Accept" to listOf("application/json"))))
        assertThat(request.query(), nullValue())

        val formParameters = request.formParameters()
        assertThat(formParameters["client_id"], equalTo(listOf("test_client_id")))
        assertThat(formParameters["login_hint"], equalTo(listOf("test_user")))
        assertThat(formParameters["channel_hint"], equalTo(listOf("push")))
        assertThat(formParameters.containsKey("client_secret"), equalTo(false))
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

        assertThat(request.url(), equalTo("https://example.okta.com/oauth2/aus_test_id/v1/oob-authenticate"))
        assertThat(request.query(), equalTo(mapOf("custom" to "value")))

        val formParameters = request.formParameters()
        assertThat(formParameters["client_id"], equalTo(listOf("test_client_id")))
        assertThat(formParameters["login_hint"], equalTo(listOf("test_user")))
        assertThat(formParameters["channel_hint"], equalTo(listOf("sms")))
        assertThat(formParameters["client_secret"], equalTo(listOf("test_client_secret")))
    }

    @Test
    fun oobAuthenticateRequest_returnsPollStateWhenUsingPushChannel() = runTest {
        val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.PUSH)
        val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(oobAuthenticatePushResponseMockEngine)))

        val state = OobStepHandler(request, testContext).process()

        assertThat(state, instanceOf(DirectAuthContinuation.OobPending::class.java))
        val oobState = state as DirectAuthContinuation.OobPending
        assertThat(oobState.bindingContext.oobCode, equalTo("example_oob_code"))
        assertThat(oobState.bindingContext.channel, equalTo(OobChannel.PUSH))
        assertThat(oobState.bindingContext.expiresIn, equalTo(120))
        assertThat(oobState.bindingContext.interval, equalTo(5))
        assertThat(oobState.bindingContext.bindingMethod, equalTo(BindingMethod.NONE))
        assertThat(oobState.bindingContext.bindingCode, nullValue())
    }

    @Test
    fun oobAuthenticateRequest_returnsPromptStateWhenUsingSmsChannel() = runTest {
        val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.SMS)
        val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(oobAuthenticateSmsResponseMockEngine)))

        val state = OobStepHandler(request, testContext).process()

        assertThat(state, instanceOf(DirectAuthContinuation.Prompt::class.java))
        val oobState = state as DirectAuthContinuation.Prompt
        assertThat(oobState.bindingContext.oobCode, equalTo("example_oob_code"))
        assertThat(oobState.bindingContext.channel, equalTo(OobChannel.SMS))
        assertThat(oobState.bindingContext.expiresIn, equalTo(120))
        assertThat(oobState.bindingContext.interval, nullValue())
        assertThat(oobState.bindingContext.bindingMethod, equalTo(BindingMethod.PROMPT))
        assertThat(oobState.bindingContext.bindingCode, nullValue())
    }

    @Test
    fun oobAuthenticateRequest_returnsPromptStateWhenUsingVoiceChannel() = runTest {
        val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.VOICE)
        val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(oobAuthenticateVoiceResponseMockEngine)))

        val state = OobStepHandler(request, testContext).process()

        assertThat(state, instanceOf(DirectAuthContinuation.Prompt::class.java))
        val oobState = state as DirectAuthContinuation.Prompt
        assertThat(oobState.bindingContext.oobCode, equalTo("example_oob_code"))
        assertThat(oobState.bindingContext.channel, equalTo(OobChannel.VOICE))
        assertThat(oobState.bindingContext.expiresIn, equalTo(120))
        assertThat(oobState.bindingContext.interval, nullValue())
        assertThat(oobState.bindingContext.bindingMethod, equalTo(BindingMethod.PROMPT))
        assertThat(oobState.bindingContext.bindingCode, nullValue())
    }

    @Test
    fun oobAuthenticateRequest_returnsTransferStateWhenUsingPushWithNumberChallenge() = runTest {
        val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.PUSH)
        val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(oobAuthenticateTransferResponseMockEngine)))

        val state = OobStepHandler(request, testContext).process()

        assertThat(state, instanceOf(DirectAuthContinuation.Transfer::class.java))
        val oobState = state as DirectAuthContinuation.Transfer
        assertThat(oobState.bindingContext.oobCode, equalTo("example_oob_code"))
        assertThat(oobState.bindingContext.channel, equalTo(OobChannel.PUSH))
        assertThat(oobState.bindingContext.expiresIn, equalTo(120))
        assertThat(oobState.bindingContext.interval, equalTo(5))
        assertThat(oobState.bindingContext.bindingMethod, equalTo(BindingMethod.TRANSFER))
        assertThat(oobState.bindingContext.bindingCode, equalTo("95"))
    }

    @Test
    fun oobAuthenticateRequest_returnsInternalErrorWhenBindingCodeIsMissing() = runTest {
        val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.PUSH)
        val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(oobAuthenticateTransferNoBindingCodeResponseMockEngine)))

        val state = OobStepHandler(request, testContext).process()

        assertThat(state, instanceOf(DirectAuthenticationError.InternalError::class.java))
        val errorState = state as DirectAuthenticationError.InternalError
        assertThat(errorState.errorCode, equalTo(EXCEPTION))
        assertThat(errorState.description, equalTo("binding_method: transfer without binding_code"))
    }

    @Test
    fun oobAuthenticateRequest_returnsInternalErrorStateWhenUnsupportedChannelReturned() = runTest {
        val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.SMS)
        val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(oobAuthenticateEmailResponseMockEngine)))

        val state = OobStepHandler(request, testContext).process()

        assertThat(state, instanceOf(DirectAuthenticationError.InternalError::class.java))
        val oobState = state as DirectAuthenticationError.InternalError
        assertThat(oobState.errorCode, equalTo(EXCEPTION))
        assertThat(oobState.description, equalTo("Unknown OOB channel: email"))
    }

    @Test
    fun oobAuthenticateRequest_returnsInternalErrorStateWhenUnsupportedBindingMethodReturned() = runTest {
        val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.SMS)
        val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(oobAuthenticateInvalidBindingResponseMockEngine)))

        val state = OobStepHandler(request, testContext).process()

        assertThat(state, instanceOf(DirectAuthenticationError.InternalError::class.java))
        val oobState = state as DirectAuthenticationError.InternalError
        assertThat(oobState.errorCode, equalTo(EXCEPTION))
        assertThat(oobState.description, equalTo("Unknown binding method: bluetooth"))
    }

    @Test
    fun oobAuthenticateRequest_returnsOauth2ErrorStateOnApiError() = runTest {
        val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.PUSH)
        val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(oobAuthenticateOauth2ErrorMockEngine)))

        val state = OobStepHandler(request, testContext).process()

        assertThat(state, instanceOf(DirectAuthenticationError.HttpError.Oauth2Error::class.java))
        val errorState = state as DirectAuthenticationError.HttpError.Oauth2Error
        assertThat(errorState.error, equalTo("invalid_request"))
        assertThat(errorState.errorDescription, equalTo("abc is not a valid channel hint"))
    }

    @Test
    fun oobAuthenticateRequest_returnsApiErrorStateOnServerError() = runTest {
        val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.PUSH)
        val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(serverErrorMockEngine)))

        val state = OobStepHandler(request, testContext).process()

        assertThat(state, instanceOf(DirectAuthenticationError.HttpError.ApiError::class.java))
        val errorState = state as DirectAuthenticationError.HttpError.ApiError
        assertThat(errorState.errorCode, equalTo("E00000"))
        assertThat(errorState.errorSummary, equalTo("Internal Server Error"))
        assertThat(errorState.httpStatusCode, equalTo(HttpStatusCode.InternalServerError))
    }

    @Test
    fun oobAuthenticateRequest_returnsInternalErrorOnUnsupportedContentType() = runTest {
        val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.PUSH)
        val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(notJsonMockEngine)))

        val state = OobStepHandler(request, testContext).process()

        assertThat(state, instanceOf(DirectAuthenticationError.InternalError::class.java))
        val error = state as DirectAuthenticationError.InternalError
        assertThat(error.errorCode, equalTo(UNSUPPORTED_CONTENT_TYPE))
        assertThat(error.throwable, instanceOf(IllegalStateException::class.java))
        assertThat(error.throwable.message, equalTo("Unsupported content type: text/plain"))
    }

    @Test
    fun oobAuthenticateRequest_unparseableClientErrorResponse() = runTest {
        val request = DirectAuthOobAuthenticateRequest(context, "test_user", OobChannel.PUSH)
        val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(unknownJsonTypeMockEngine)))

        val state = OobStepHandler(request, testContext).process()

        assertThat(state, instanceOf(DirectAuthenticationError.InternalError::class.java))
        val error = state as DirectAuthenticationError.InternalError
        assertThat(error.errorCode, equalTo(INVALID_RESPONSE))
        assertThat(error.throwable, instanceOf(IllegalStateException::class.java))
        assertThat(error.throwable.message, equalTo("No parsable error response body: HTTP ${HttpStatusCode.BadRequest}"))
    }
}
