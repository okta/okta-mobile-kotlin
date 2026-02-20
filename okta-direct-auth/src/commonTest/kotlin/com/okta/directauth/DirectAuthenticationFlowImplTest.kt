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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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

            assertIs<DirectAuthenticationState.Authenticated>(state)
            val token = state.token
            assertEquals("example_access_token", token.accessToken)
            assertEquals(state, flow.authenticationState.value)
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

            assertIs<DirectAuthenticationState.Authenticated>(state)
            val token = state.token
            assertEquals("example_access_token", token.accessToken)

            assertEquals(state, flow.authenticationState.value)
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

            assertIs<DirectAuthenticationState.MfaRequired>(state)
            assertEquals("example_mfa_token", state.mfaContext.mfaToken)
            assertEquals(state, flow.authenticationState.value)
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

            assertIs<DirectAuthenticationError.InternalError>(state)
            assertIs<SerializationException>(state.throwable)
            assertEquals(state, flow.authenticationState.value)
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

            assertIs<DirectAuthContinuation.OobPending>(state)
            assertEquals("example_oob_code", state.bindingContext.oobCode)
            assertEquals(OobChannel.PUSH, state.bindingContext.channel)
            assertEquals(120, state.bindingContext.expiresIn)
            assertEquals(5, state.bindingContext.interval)

            assertEquals(state, flow.authenticationState.value)
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
            assertIs<DirectAuthenticationState.Authenticated>(flow.authenticationState.value)

            val state = flow.reset()

            assertEquals(DirectAuthenticationState.Idle, state)
            assertEquals(DirectAuthenticationState.Idle, flow.authenticationState.value)
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
            assertIs<DirectAuthenticationState.MfaRequired>(flow.authenticationState.value)

            val state = flow.reset()

            assertEquals(DirectAuthenticationState.Idle, state)
            assertEquals(DirectAuthenticationState.Idle, flow.authenticationState.value)
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
            assertIs<DirectAuthenticationError.InternalError>(flow.authenticationState.value)

            val state = flow.reset()

            assertEquals(DirectAuthenticationState.Idle, state)
            assertEquals(DirectAuthenticationState.Idle, flow.authenticationState.value)
        }
}
