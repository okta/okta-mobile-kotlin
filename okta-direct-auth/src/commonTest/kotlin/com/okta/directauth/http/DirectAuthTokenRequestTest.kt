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
import com.okta.directauth.apiErrorClientMockEngine
import com.okta.directauth.authorizationPendingMockEngine
import com.okta.directauth.emptyResponseMockEngine
import com.okta.directauth.emptyResponseOkMockEngine
import com.okta.directauth.http.handlers.TokenStepHandler
import com.okta.directauth.internalServerErrorMockEngine
import com.okta.directauth.invalidMfaRequiredMockEngine
import com.okta.directauth.malformedJsonClientMockEngine
import com.okta.directauth.malformedJsonErrorCodeMockEngine
import com.okta.directauth.malformedJsonErrorMockEngine
import com.okta.directauth.malformedJsonOkMockEngine
import com.okta.directauth.mfaRequiredMockEngine
import com.okta.directauth.model.DirectAuthenticationContext
import com.okta.directauth.model.DirectAuthenticationError.HttpError
import com.okta.directauth.model.DirectAuthenticationError.InternalError
import com.okta.directauth.model.DirectAuthenticationIntent
import com.okta.directauth.model.DirectAuthenticationState
import com.okta.directauth.model.MfaContext
import com.okta.directauth.notJsonMockEngine
import com.okta.directauth.oAuth2ErrorMockEngine
import com.okta.directauth.serverErrorMockEngine
import com.okta.directauth.tokenResponseMockEngine
import com.okta.directauth.unexpectedStatusCodeMockEngine
import com.okta.directauth.unknownJsonTypeMockEngine
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DirectAuthTokenRequestTest {
    private lateinit var context: DirectAuthenticationContext

    private val supportedGrantTypes = listOf(GrantType.Password, GrantType.Oob, GrantType.Otp, ChallengeGrantType.OobMfa, ChallengeGrantType.OtpMfa, GrantType.WebAuthn, ChallengeGrantType.WebAuthnMfa)

    @BeforeTest
    fun setUp() {
        context =
            DirectAuthenticationContext(
                issuerUrl = "https://example.okta.com",
                clientId = "test_client_id",
                scope = listOf("openid", "email", "profile", "offline_access"),
                authorizationServerId = "",
                clientSecret = "test_client_secret",
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
                additionalParameters = mapOf("custom_param" to "custom_value")
            )
    }

    private fun assertCommonProperties(request: DirectAuthTokenRequest) {
        assertEquals("https://example.okta.com/oauth2/v1/token", request.url())
        assertEquals(ApiRequestMethod.POST, request.method())
        assertEquals("application/x-www-form-urlencoded", request.contentType())
        assertTrue(request.headers().containsKey("Accept"))
        assertEquals(listOf("application/json"), request.headers()["Accept"])
        assertEquals(listOf(userAgentValue()), request.headers()["User-Agent"])
        assertEquals(mapOf("custom_param" to "custom_value"), request.query())
    }

    @Test
    fun passwordRequest_buildsRequestWithCorrectParameters() {
        val request =
            DirectAuthTokenRequest.Password(
                context = context,
                username = "test_user",
                password = "test_password"
            )

        assertCommonProperties(request)

        val formParameters = request.formParameters()
        assertEquals(listOf("test_client_id"), formParameters["client_id"])
        assertEquals(listOf("test_client_secret"), formParameters["client_secret"])
        assertEquals(listOf("openid email profile offline_access"), formParameters["scope"])
        assertEquals(listOf("${GrantType.Password.value} ${GrantType.Otp.value}"), formParameters["grant_types_supported"])
        assertEquals(listOf(GrantType.Password.value), formParameters["grant_type"])
        assertEquals(listOf("test_user"), formParameters["username"])
        assertEquals(listOf("test_password"), formParameters["password"])
    }

    @Test
    fun passwordRequest_buildsRequestWithRecoveryIntent() {
        val recoveryContext = context.copy(directAuthenticationIntent = DirectAuthenticationIntent.RECOVERY, scope = listOf("okta.myAccount.password.manage"))
        val request =
            DirectAuthTokenRequest.Password(
                context = recoveryContext,
                username = "test_user",
                password = "test_password"
            )

        assertCommonProperties(request)

        val formParameters = request.formParameters()
        assertEquals(listOf("test_client_id"), formParameters["client_id"])
        assertEquals(listOf("test_client_secret"), formParameters["client_secret"])
        assertEquals(listOf("okta.myAccount.password.manage"), formParameters["scope"])
        assertEquals(listOf("recover_authenticator"), formParameters["prompt"])
        assertEquals(listOf("${GrantType.Password.value} ${GrantType.Otp.value}"), formParameters["grant_types_supported"])
        assertEquals(listOf(GrantType.Password.value), formParameters["grant_type"])
        assertEquals(listOf("test_user"), formParameters["username"])
        assertEquals(listOf("test_password"), formParameters["password"])
    }

    @Test
    fun otpRequest_buildsRequestWithCorrectParameters() {
        val request =
            DirectAuthTokenRequest.Otp(
                context = context,
                loginHint = "test_user",
                otp = "123456"
            )

        assertCommonProperties(request)

        val formParameters = request.formParameters()
        assertEquals(listOf(GrantType.Otp.value), formParameters["grant_type"])
        assertEquals(listOf("test_user"), formParameters["login_hint"])
        assertEquals(listOf("123456"), formParameters["otp"])
    }

    @Test
    fun mfaOtpRequest_buildsRequestWithCorrectParameters() {
        val mfaContext = MfaContext(supportedGrantTypes, "test_mfa_token")
        val request =
            DirectAuthTokenRequest.MfaOtp(
                context = context,
                otp = "123456",
                mfaContext = mfaContext
            )

        assertCommonProperties(request)

        val formParameters = request.formParameters()
        assertEquals(listOf(ChallengeGrantType.OtpMfa.value), formParameters["grant_type"])
        assertEquals(listOf("test_mfa_token"), formParameters["mfa_token"])
        assertEquals(listOf("123456"), formParameters["otp"])
    }

    @Test
    fun oobRequest_buildsRequestWithCorrectParameters() {
        val request =
            DirectAuthTokenRequest.Oob(
                context = context,
                oobCode = "test_oob_code",
                bindingCode = "95"
            )

        assertCommonProperties(request)

        val formParameters = request.formParameters()
        assertEquals(listOf(GrantType.Oob.value), formParameters["grant_type"])
        assertEquals(listOf("test_oob_code"), formParameters["oob_code"])
        assertEquals(listOf("95"), formParameters["binding_code"])
    }

    @Test
    fun oobMfaRequest_buildsRequestWithCorrectParameters() {
        val mfaContext = MfaContext(supportedGrantTypes, "test_mfa_token")
        val request =
            DirectAuthTokenRequest.OobMfa(
                context = context,
                oobCode = "test_oob_code",
                mfaContext = mfaContext,
                bindingCode = "test_binding_code"
            )

        assertCommonProperties(request)

        val formParameters = request.formParameters()
        assertEquals(listOf(ChallengeGrantType.OobMfa.value), formParameters["grant_type"])
        assertEquals(listOf("test_mfa_token"), formParameters["mfa_token"])
        assertEquals(listOf("test_oob_code"), formParameters["oob_code"])
        assertEquals(listOf("test_binding_code"), formParameters["binding_code"])
    }

    @Test
    fun webAuthnRequest_buildsRequestWithCorrectParameters() {
        val request =
            DirectAuthTokenRequest.WebAuthn(
                context = context,
                authenticatorData = "test_authenticator_data",
                clientDataJson = "test_client_data_json",
                signature = "test_signature"
            )

        assertCommonProperties(request)

        val formParameters = request.formParameters()
        assertEquals(listOf(GrantType.WebAuthn.value), formParameters["grant_type"])
        assertEquals(listOf("test_authenticator_data"), formParameters["authenticatorData"])
        assertEquals(listOf("test_client_data_json"), formParameters["clientDataJSON"])
        assertEquals(listOf("test_signature"), formParameters["signature"])
    }

    @Test
    fun webAuthnMfaRequest_buildsRequestWithCorrectParameters() {
        val mfaContext = MfaContext(supportedGrantTypes, "test_mfa_token")
        val request =
            DirectAuthTokenRequest.WebAuthnMfa(
                context = context,
                mfaContext = mfaContext,
                authenticatorData = "test_authenticator_data",
                clientDataJson = "test_client_data_json",
                signature = "test_signature"
            )

        assertCommonProperties(request)

        val formParameters = request.formParameters()
        assertEquals(listOf(ChallengeGrantType.WebAuthnMfa.value), formParameters["grant_type"])
        assertEquals(listOf("test_mfa_token"), formParameters["mfa_token"])
        assertEquals(listOf("test_authenticator_data"), formParameters["authenticatorData"])
        assertEquals(listOf("test_client_data_json"), formParameters["clientDataJSON"])
        assertEquals(listOf("test_signature"), formParameters["signature"])
    }

    @Test
    fun request_usesAuthorizationServerIdInUrlPath() {
        val customAuthServerContext =
            DirectAuthenticationContext(
                issuerUrl = "https://example.okta.com",
                clientId = "test_client_id",
                scope = listOf("openid", "email", "profile", "offline_access"),
                authorizationServerId = "default", // Custom Authorization Server ID
                clientSecret = "test_client_secret",
                grantTypes = listOf(GrantType.Password, GrantType.Otp),
                acrValues = emptyList(),
                directAuthenticationIntent = DirectAuthenticationIntent.SIGN_IN,
                apiExecutor = KtorHttpExecutor(),
                logger = context.logger,
                clock = { 1654041600 },
                additionalParameters = emptyMap()
            )

        val request =
            DirectAuthTokenRequest.Password(
                context = customAuthServerContext,
                username = "test_user",
                password = "test_password"
            )

        assertEquals("https://example.okta.com/oauth2/default/v1/token", request.url())
    }

    @Test
    fun request_parsesTokenResponse() =
        runTest {
            val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(tokenResponseMockEngine)))

            val directAuthState = TokenStepHandler(request, testContext).process()

            assertEquals("https://example.okta.com/oauth2/v1/token", request.url())
            assertIs<DirectAuthenticationState.Authenticated>(directAuthState)
            val token = directAuthState.token
            assertEquals("example_access_token", token.accessToken)
        }

    @Test
    fun request_parsesOAuth2ErrorResponse() =
        runTest {
            val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(oAuth2ErrorMockEngine)))

            val directAuthState = TokenStepHandler(request, testContext).process()

            assertIs<HttpError.Oauth2Error>(directAuthState)
            assertEquals("invalid_grant", directAuthState.error)
            assertEquals("The password was invalid.", directAuthState.errorDescription)
            assertEquals(HttpStatusCode.BadRequest, directAuthState.httpStatusCode)
        }

    @Test
    fun request_parsesApiErrorFromClientErrorResponse() =
        runTest {
            val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(apiErrorClientMockEngine)))

            val directAuthState = TokenStepHandler(request, testContext).process()

            assertIs<HttpError.ApiError>(directAuthState)
            assertEquals("E0000011", directAuthState.errorCode)
            assertEquals("Invalid token provided", directAuthState.errorSummary)
            assertEquals("E0000011", directAuthState.errorLink)
            assertEquals("test_error_id", directAuthState.errorId)
            assertEquals(listOf("Invalid token: token is expired"), directAuthState.errorCauses)
            assertEquals(HttpStatusCode.BadRequest, directAuthState.httpStatusCode)
        }

    @Test
    fun request_handlesServerError() =
        runTest {
            val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(serverErrorMockEngine)))

            val directAuthState = TokenStepHandler(request, testContext).process()

            assertIs<HttpError.ApiError>(directAuthState)
            assertEquals("E00000", directAuthState.errorCode)
            assertEquals("Internal Server Error", directAuthState.errorSummary)
            assertEquals(HttpStatusCode.InternalServerError, directAuthState.httpStatusCode)
        }

    @Test
    fun request_parsesMfaRequiredResponse() =
        runTest {
            val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(mfaRequiredMockEngine)))

            val directAuthState = TokenStepHandler(request, testContext).process()

            assertIs<DirectAuthenticationState.MfaRequired>(directAuthState)
            assertEquals("example_mfa_token", directAuthState.mfaContext.mfaToken)
        }

    @Test
    fun request_parsesMfaRequiredResponseWithoutMfaToken() =
        runTest {
            val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(invalidMfaRequiredMockEngine)))

            val directAuthState = TokenStepHandler(request, testContext).process()

            assertIs<InternalError>(directAuthState)
            assertEquals(INVALID_RESPONSE, directAuthState.errorCode)
            assertIs<IllegalStateException>(directAuthState.throwable)
            assertEquals("No mfa_token found in body: HTTP ${HttpStatusCode.BadRequest}", directAuthState.throwable.message)
        }

    @Test
    fun request_parsesAuthorizationPendingResponse() =
        runTest {
            val request = DirectAuthTokenRequest.Oob(context, "test_oob_code")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(authorizationPendingMockEngine)))

            val directAuthState = TokenStepHandler(request, testContext).process()

            assertIs<DirectAuthenticationState.AuthorizationPending>(directAuthState)
        }

    @Test
    fun request_handlesUnsupportedContentType() =
        runTest {
            val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(notJsonMockEngine)))

            val directAuthState = TokenStepHandler(request, testContext).process()

            assertIs<InternalError>(directAuthState)
            assertEquals(UNSUPPORTED_CONTENT_TYPE, directAuthState.errorCode)
            assertIs<IllegalStateException>(directAuthState.throwable)
            assertEquals("Unsupported content type: text/plain", directAuthState.throwable.message)
        }

    @Test
    fun request_handlesUnexpectedStatusCode() =
        runTest {
            val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(unexpectedStatusCodeMockEngine)))

            val directAuthState = TokenStepHandler(request, testContext).process()

            assertIs<InternalError>(directAuthState)
            assertEquals(UNEXPECTED_HTTP_STATUS, directAuthState.errorCode)
            assertIs<IllegalStateException>(directAuthState.throwable)
            assertEquals("Unexpected HTTP Status Code: ${HttpStatusCode.Found}", directAuthState.throwable.message)
        }

    @Test
    fun request_unparseableClientErrorResponse() =
        runTest {
            val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(unknownJsonTypeMockEngine)))

            val directAuthState = TokenStepHandler(request, testContext).process()

            assertIs<InternalError>(directAuthState)
            assertEquals(INVALID_RESPONSE, directAuthState.errorCode)
            assertIs<IllegalStateException>(directAuthState.throwable)
            assertEquals("No parsable error response body: HTTP ${HttpStatusCode.BadRequest}", directAuthState.throwable.message)
        }

    @Test
    fun request_unparseableServerErrorResponse() =
        runTest {
            val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(internalServerErrorMockEngine)))

            val directAuthState = TokenStepHandler(request, testContext).process()

            assertIs<InternalError>(directAuthState)
            assertEquals(INVALID_RESPONSE, directAuthState.errorCode)
            assertIs<IllegalStateException>(directAuthState.throwable)
            assertEquals("No parsable error response body: HTTP ${HttpStatusCode.InternalServerError}", directAuthState.throwable.message)
        }

    @Test
    fun request_emptyErrorResponse() =
        runTest {
            val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(emptyResponseMockEngine)))

            val directAuthState = TokenStepHandler(request, testContext).process()

            assertIs<InternalError>(directAuthState)
            assertEquals(INVALID_RESPONSE, directAuthState.errorCode)
            assertIs<IllegalStateException>(directAuthState.throwable)
            assertEquals("No parsable error response body: HTTP ${HttpStatusCode.BadRequest}", directAuthState.throwable.message)
        }

    @Test
    fun request_emptySuccessResponse() =
        runTest {
            val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(emptyResponseOkMockEngine)))

            val directAuthState = TokenStepHandler(request, testContext).process()

            assertIs<InternalError>(directAuthState)
            assertEquals(INVALID_RESPONSE, directAuthState.errorCode)
            assertIs<IllegalStateException>(directAuthState.throwable)
            assertEquals("Empty response body: HTTP ${HttpStatusCode.OK}", directAuthState.throwable.message)
        }

    @Test
    fun request_malformedJsonInHttpOkStatus() =
        runTest {
            val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(malformedJsonOkMockEngine)))

            val directAuthState = TokenStepHandler(request, testContext).process()

            assertIs<InternalError>(directAuthState)
            assertEquals(EXCEPTION, directAuthState.errorCode)
            assertTrue(directAuthState.description!!.contains("Unexpected JSON token at offset"))
            assertIs<SerializationException>(directAuthState.throwable)
        }

    @Test
    fun request_malformedJsonInClientErrorStatus() =
        runTest {
            val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(malformedJsonClientMockEngine)))

            val directAuthState = TokenStepHandler(request, testContext).process()

            assertIs<InternalError>(directAuthState)
            assertEquals(EXCEPTION, directAuthState.errorCode)
            assertTrue(directAuthState.description!!.contains("Unexpected JSON token at offset"))
            assertIs<SerializationException>(directAuthState.throwable)
        }

    @Test
    fun request_malformedJsonInDirectAuthenticationErrorResponse() =
        runTest {
            val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(malformedJsonErrorMockEngine)))

            val directAuthState = TokenStepHandler(request, testContext).process()

            assertIs<InternalError>(directAuthState)
            assertEquals(EXCEPTION, directAuthState.errorCode)
            assertTrue(directAuthState.description!!.contains("Unexpected JSON token at offset"))
            assertIs<SerializationException>(directAuthState.throwable)
        }

    @Test
    fun request_malFormedJsonInErrorResponse() =
        runTest {
            val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
            val testContext = context.copy(apiExecutor = KtorHttpExecutor(HttpClient(malformedJsonErrorCodeMockEngine)))

            val directAuthState = TokenStepHandler(request, testContext).process()

            assertIs<InternalError>(directAuthState)
            assertEquals(EXCEPTION, directAuthState.errorCode)
            assertTrue(directAuthState.description!!.contains("Unexpected JSON token at offset"))
            assertIs<SerializationException>(directAuthState.throwable)
        }
}
