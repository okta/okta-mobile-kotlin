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

import com.okta.authfoundation.api.http.KtorHttpExecutor
import com.okta.directauth.model.DirectAuthContinuation
import com.okta.directauth.model.DirectAuthenticationError
import com.okta.directauth.model.DirectAuthenticationState
import com.okta.directauth.model.OobChannel
import com.okta.directauth.model.PrimaryFactor
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import kotlin.jvm.java

class DirectAuthenticationFlowImplTest {
    private val issuerUrl = "https://example.okta.com"
    private val clientId = "test_client_id"
    private val scope = listOf("openid", "email", "profile", "offline_access")

    @Test
    fun start_withPasswordFactor_returnsSuccessOnTokenResponse() =
        runTest {
            val flow =
                DirectAuthenticationFlowBuilder
                    .create(issuerUrl, clientId, scope) {
                        apiExecutor = KtorHttpExecutor(HttpClient(tokenResponseMockEngine))
                    }.getOrThrow()

            val state = flow.start("test_user", PrimaryFactor.Password("test_password"))

            assertThat(state, instanceOf(DirectAuthenticationState.Authenticated::class.java))
            val token = (state as DirectAuthenticationState.Authenticated).token
            assertThat(token.accessToken, equalTo("example_access_token"))
            assertThat(flow.authenticationState.value, equalTo(state))
        }

    @Test
    fun start_withOtpFactor_returnsSuccessOnTokenResponse() =
        runTest {
            val flow =
                DirectAuthenticationFlowBuilder
                    .create(issuerUrl, clientId, scope) {
                        apiExecutor = KtorHttpExecutor(HttpClient(tokenResponseMockEngine))
                    }.getOrThrow()

            val state = flow.start("test_user", PrimaryFactor.Otp("123456"))

            assertThat(state, instanceOf(DirectAuthenticationState.Authenticated::class.java))
            val token = (state as DirectAuthenticationState.Authenticated).token
            assertThat(token.accessToken, equalTo("example_access_token"))

            assertThat(flow.authenticationState.value, equalTo(state))
        }

    @Test
    fun start_returnsSuccessOnMfaRequiredResponse() =
        runTest {
            val flow =
                DirectAuthenticationFlowBuilder
                    .create(issuerUrl, clientId, scope) {
                        apiExecutor = KtorHttpExecutor(HttpClient(mfaRequiredMockEngine))
                    }.getOrThrow()

            val state = flow.start("test_user", PrimaryFactor.Password("test_password"))

            assertThat(state, instanceOf(DirectAuthenticationState.MfaRequired::class.java))
            assertThat((state as DirectAuthenticationState.MfaRequired).mfaContext.mfaToken, equalTo("example_mfa_token"))
            assertThat(flow.authenticationState.value, equalTo(state))
        }

    @Test
    fun start_returnsFailureWhenApiExecutorThrows() =
        runTest {
            val flow =
                DirectAuthenticationFlowBuilder
                    .create(issuerUrl, clientId, scope) {
                        apiExecutor = KtorHttpExecutor(HttpClient(malformedJsonOkMockEngine))
                    }.getOrThrow()

            val state = flow.start("test_user", PrimaryFactor.Password("test_password"))

            assertThat(state, instanceOf(DirectAuthenticationError.InternalError::class.java))
            assertThat((state as DirectAuthenticationError.InternalError).throwable, instanceOf(SerializationException::class.java))
            assertThat(flow.authenticationState.value, equalTo(state))
        }

    @Test
    fun start_withOobFactor_returnsContinuationPollState() =
        runTest {
            val flow =
                DirectAuthenticationFlowBuilder
                    .create(issuerUrl, clientId, scope) {
                        apiExecutor = KtorHttpExecutor(HttpClient(oobAuthenticatePushResponseMockEngine))
                    }.getOrThrow()

            val state = flow.start("test_user", PrimaryFactor.Oob(OobChannel.PUSH))

            assertThat(state, instanceOf(DirectAuthContinuation.OobPending::class.java))
            val oobState = state as DirectAuthContinuation.OobPending
            assertThat(oobState.bindingContext.oobCode, equalTo("example_oob_code"))
            assertThat(oobState.bindingContext.channel, equalTo(OobChannel.PUSH))
            assertThat(oobState.bindingContext.expiresIn, equalTo(120))
            assertThat(oobState.bindingContext.interval, equalTo(5))

            assertThat(flow.authenticationState.value, equalTo(state))
        }

    @Test
    fun reset_resetsStateToIdleFromAuthenticated() =
        runTest {
            val flow =
                DirectAuthenticationFlowBuilder
                    .create(issuerUrl, clientId, scope) {
                        apiExecutor = KtorHttpExecutor(HttpClient(tokenResponseMockEngine))
                    }.getOrThrow()
            flow.start("test_user", PrimaryFactor.Password("test_password"))
            assertThat(flow.authenticationState.value, instanceOf(DirectAuthenticationState.Authenticated::class.java))

            val state = flow.reset()

            assertThat(state, equalTo(DirectAuthenticationState.Idle))
            assertThat(flow.authenticationState.value, equalTo(DirectAuthenticationState.Idle))
        }

    @Test
    fun reset_resetsStateToIdleFromMfaRequired() =
        runTest {
            val flow =
                DirectAuthenticationFlowBuilder
                    .create(issuerUrl, clientId, scope) {
                        apiExecutor = KtorHttpExecutor(HttpClient(mfaRequiredMockEngine))
                    }.getOrThrow()
            flow.start("test_user", PrimaryFactor.Password("test_password"))
            assertThat(flow.authenticationState.value, instanceOf(DirectAuthenticationState.MfaRequired::class.java))

            val state = flow.reset()

            assertThat(state, equalTo(DirectAuthenticationState.Idle))
            assertThat(flow.authenticationState.value, equalTo(DirectAuthenticationState.Idle))
        }

    @Test
    fun reset_resetsStateToIdleFromError() =
        runTest {
            val flow =
                DirectAuthenticationFlowBuilder
                    .create(issuerUrl, clientId, scope) {
                        apiExecutor = KtorHttpExecutor(HttpClient(malformedJsonOkMockEngine))
                    }.getOrThrow()
            flow.start("test_user", PrimaryFactor.Password("test_password"))
            assertThat(flow.authenticationState.value, instanceOf(DirectAuthenticationError.InternalError::class.java))

            val state = flow.reset()

            assertThat(state, equalTo(DirectAuthenticationState.Idle))
            assertThat(flow.authenticationState.value, equalTo(DirectAuthenticationState.Idle))
        }
}
