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
import com.okta.authfoundation.api.http.log.AuthFoundationLogger
import com.okta.authfoundation.api.http.log.LogLevel
import com.okta.directauth.challengeOtpResponseMockEngine
import com.okta.directauth.challengeWebAuthnResponseMockEngine
import com.okta.directauth.http.KtorHttpExecutor
import com.okta.directauth.oobAuthenticatePushResponseMockEngine
import com.okta.directauth.oobAuthenticateTransferResponseMockEngine
import com.okta.directauth.tokenResponseMockEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import kotlin.jvm.java

class DirectAuthenticationStateMfaRequiredTest {
    private lateinit var mfaContext: MfaContext

    @Before
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

            assertThat(result, instanceOf(DirectAuthenticationState.Canceled::class.java))
        }

    @Test
    fun `challenge throws Exception returns InternalErrorState`() =
        runTest {
            val mockEngine = createThrowingMockEngine(IllegalStateException("Something went wrong"))
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(mockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.challenge(PrimaryFactor.Oob(OobChannel.PUSH))

            assertThat(result, instanceOf(DirectAuthenticationError.InternalError::class.java))
            val error = result as DirectAuthenticationError.InternalError
            assertThat(error.throwable, instanceOf(IllegalStateException::class.java))
        }

    @Test
    fun `challenge with Oob calls return OobPending`() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(oobAuthenticatePushResponseMockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.challenge(PrimaryFactor.Oob(OobChannel.PUSH))

            assertThat(result, instanceOf(DirectAuthContinuation.OobPending::class.java))
        }

    @Test
    fun `challenge with Oob calls return Transfer`() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(oobAuthenticateTransferResponseMockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.challenge(PrimaryFactor.Oob(OobChannel.PUSH))

            assertThat(result, instanceOf(DirectAuthContinuation.Transfer::class.java))
        }

    @Test
    fun `challenge with Otp calls returns Prompt`() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(challengeOtpResponseMockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.challenge(PrimaryFactor.Otp("passCode"))

            assertThat(result, instanceOf(DirectAuthContinuation.Prompt::class.java))
        }

    @Test
    @Ignore("Web authn not yet implemented")
    fun `challenge with WebAuthn calls returns WebAuthn state`() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(challengeWebAuthnResponseMockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.resume(PrimaryFactor.WebAuthn)

            assertThat(result, instanceOf(DirectAuthContinuation.WebAuthn::class.java))
        }

    @Test
    fun `resume with Otp throws CancellationException returns CanceledState`() =
        runTest {
            val mockEngine = createThrowingMockEngine(CancellationException("Co-routine canceled"))
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(mockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.resume(PrimaryFactor.Otp("123456"))

            assertThat(result, instanceOf(DirectAuthenticationState.Canceled::class.java))
        }

    @Test
    fun `resume with Otp throws Exception returns InternalErrorState`() =
        runTest {
            val mockEngine = createThrowingMockEngine(IllegalStateException("Something went wrong"))
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(mockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.resume(PrimaryFactor.Otp("123456"))

            assertThat(result, instanceOf(DirectAuthenticationError.InternalError::class.java))
            val error = result as DirectAuthenticationError.InternalError
            assertThat(error.throwable, instanceOf(IllegalStateException::class.java))
        }

    @Test
    fun `resume with Oob calls return OobPending`() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(oobAuthenticatePushResponseMockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.resume(PrimaryFactor.Oob(OobChannel.PUSH))

            assertThat(result, instanceOf(DirectAuthContinuation.OobPending::class.java))
        }

    @Test
    fun `resume with Oob calls return Transfer`() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(oobAuthenticateTransferResponseMockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.resume(PrimaryFactor.Oob(OobChannel.PUSH))

            assertThat(result, instanceOf(DirectAuthContinuation.Transfer::class.java))
        }

    @Test
    fun `resume with Otp calls returns Authenticated`() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(tokenResponseMockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.resume(PrimaryFactor.Otp("passCode"))

            assertThat(result, instanceOf(DirectAuthenticationState.Authenticated::class.java))
        }

    @Test
    @Ignore("Web authn not yet implemented")
    fun `resume with WebAuthn calls returns WebAuthn state`() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(challengeWebAuthnResponseMockEngine)))
            val mfaRequired = DirectAuthenticationState.MfaRequired(context, mfaContext)

            val result = mfaRequired.resume(PrimaryFactor.WebAuthn)

            assertThat(result, instanceOf(DirectAuthContinuation.WebAuthn::class.java))
        }
}
