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
package com.okta.directauth.model

import com.okta.authfoundation.GrantType
import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.KtorHttpExecutor
import com.okta.authfoundation.api.log.AuthFoundationLogger
import com.okta.authfoundation.api.log.LogLevel
import com.okta.directauth.challengeOtpResponseMockEngine
import com.okta.directauth.challengeWebAuthnResponseMockEngine
import com.okta.directauth.notJsonMockEngine
import com.okta.directauth.oAuth2ErrorMockEngine
import com.okta.directauth.oobAuthenticatePushResponseMockEngine
import com.okta.directauth.oobAuthenticateTransferResponseMockEngine
import com.okta.directauth.serverErrorMockEngine
import com.okta.directauth.tokenResponseMockEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DirectAuthenticationStateMfaRequiredTest {
    private lateinit var mfaContext: MfaContext

    @BeforeTest
    fun setUp() {
        mfaContext = MfaContext(listOf(), "mfa_token")
    }

    private fun createDirectAuthenticationContext(apiExecutor: ApiExecutor): DirectAuthenticationContext =
        DirectAuthenticationContext(
            issuerUrl = "https://example.okta.com",
            clientId = "test_client_id",
            scope = listOf("openid", "email", "profile", "offline_access"),
            authorizationServerId = "",
            clientSecret = "test_client_secret",
            grantTypes = listOf(GrantType.Password, GrantType.Otp),
            acrValues = emptyList(),
            directAuthenticationIntent = DirectAuthenticationIntent.SIGN_IN,
            apiExecutor = apiExecutor,
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

    private fun createThrowingMockEngine(throwable: Throwable): MockEngine =
        MockEngine {
            throw throwable
        }

    @Test
    fun `challenge throws CancellationException returns CanceledState`() =
        runTest {
            val mockEngine = createThrowingMockEngine(CancellationException("Co-routine canceled"))
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(mockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.challenge(PrimaryFactor.Oob(OobChannel.PUSH))

            assertIs<DirectAuthenticationState.Canceled>(result)
        }

    @Test
    fun `challenge throws Exception returns InternalErrorState`() =
        runTest {
            val mockEngine = createThrowingMockEngine(IllegalStateException("Something went wrong"))
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(mockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.challenge(PrimaryFactor.Oob(OobChannel.PUSH))

            assertIs<DirectAuthenticationError.InternalError>(result)
            assertIs<IllegalStateException>(result.throwable)
        }

    @Test
    fun `challenge returns InternalError on unsupported content type`() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(notJsonMockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.challenge(PrimaryFactor.Oob(OobChannel.PUSH))

            assertIs<DirectAuthenticationError.InternalError>(result)
            assertIs<IllegalStateException>(result.throwable)
            assertEquals("Unsupported content type: text/plain", result.throwable.message)
        }

    @Test
    fun `challenge returns Oauth2Error on API error`() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(oAuth2ErrorMockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.challenge(PrimaryFactor.Oob(OobChannel.PUSH))

            assertIs<DirectAuthenticationError.HttpError.Oauth2Error>(result)
            assertEquals("invalid_grant", result.error)
        }

    @Test
    fun `challenge returns ApiError on server error`() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(serverErrorMockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.challenge(PrimaryFactor.Oob(OobChannel.PUSH))

            assertIs<DirectAuthenticationError.HttpError.ApiError>(result)
            assertEquals("E00000", result.errorCode)
            assertEquals("Internal Server Error", result.errorSummary)
        }

    @Test
    fun `challenge with Oob calls return OobPending`() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(oobAuthenticatePushResponseMockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.challenge(PrimaryFactor.Oob(OobChannel.PUSH))

            assertIs<DirectAuthContinuation.OobPending>(result)
        }

    @Test
    fun `challenge with Oob calls return Transfer`() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(oobAuthenticateTransferResponseMockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.challenge(PrimaryFactor.Oob(OobChannel.PUSH))

            assertIs<DirectAuthContinuation.Transfer>(result)
        }

    @Test
    fun `challenge with Otp calls returns Prompt`() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(challengeOtpResponseMockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.challenge(PrimaryFactor.Otp("passCode"))

            assertIs<DirectAuthContinuation.Prompt>(result)
        }

    @Test
    @Ignore("WebAuthn is not yet supported")
    fun `challenge with WebAuthn calls returns WebAuthn state`() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(challengeWebAuthnResponseMockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.resume(PrimaryFactor.WebAuthn)

            assertIs<DirectAuthContinuation.WebAuthn>(result)
        }

    @Test
    fun `resume with Otp throws CancellationException returns CanceledState`() =
        runTest {
            val mockEngine = createThrowingMockEngine(CancellationException("Co-routine canceled"))
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(mockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.resume(PrimaryFactor.Otp("123456"))

            assertIs<DirectAuthenticationState.Canceled>(result)
        }

    @Test
    fun `resume with Otp throws Exception returns InternalErrorState`() =
        runTest {
            val mockEngine = createThrowingMockEngine(IllegalStateException("Something went wrong"))
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(mockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.resume(PrimaryFactor.Otp("123456"))

            assertIs<DirectAuthenticationError.InternalError>(result)
            assertIs<IllegalStateException>(result.throwable)
        }

    @Test
    fun `resume with Oob calls return OobPending`() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(oobAuthenticatePushResponseMockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.resume(PrimaryFactor.Oob(OobChannel.PUSH))

            assertIs<DirectAuthContinuation.OobPending>(result)
        }

    @Test
    fun `resume with Oob calls return Transfer`() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(oobAuthenticateTransferResponseMockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.resume(PrimaryFactor.Oob(OobChannel.PUSH))

            assertIs<DirectAuthContinuation.Transfer>(result)
        }

    @Test
    fun `resume with Otp calls returns Authenticated`() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(tokenResponseMockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.resume(PrimaryFactor.Otp("passCode"))

            assertIs<DirectAuthenticationState.Authenticated>(result)
        }

    @Test
    @Ignore("WebAuthn is not yet supported")
    fun `resume with WebAuthn calls returns WebAuthn state`() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(challengeWebAuthnResponseMockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.resume(PrimaryFactor.WebAuthn)

            assertIs<DirectAuthContinuation.WebAuthn>(result)
        }
}
