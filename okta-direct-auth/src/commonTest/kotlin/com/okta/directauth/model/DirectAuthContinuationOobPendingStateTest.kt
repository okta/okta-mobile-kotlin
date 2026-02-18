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

import com.okta.authfoundation.ChallengeGrantType
import com.okta.authfoundation.GrantType
import com.okta.authfoundation.api.http.KtorHttpExecutor
import com.okta.authfoundation.api.log.AuthFoundationLogger
import com.okta.authfoundation.api.log.LogLevel
import com.okta.directauth.AUTHORIZATION_PENDING_JSON
import com.okta.directauth.TOKEN_RESPONSE_JSON
import com.okta.directauth.contentType
import com.okta.directauth.http.EXCEPTION
import com.okta.directauth.malformedJsonOkMockEngine
import com.okta.directauth.model.DirectAuthenticationError.InternalError
import com.okta.directauth.model.DirectAuthenticationState.Authenticated
import com.okta.directauth.model.DirectAuthenticationState.AuthorizationPending
import com.okta.directauth.oAuth2ErrorMockEngine
import com.okta.directauth.tokenResponseMockEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DirectAuthContinuationOobPendingStateTest {
    private lateinit var bindingContext: BindingContext

    private fun createDirectAuthenticationContext(apiExecutor: KtorHttpExecutor): DirectAuthenticationContext =
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

    @BeforeTest
    fun setUp() {
        bindingContext =
            BindingContext(
                oobCode = "test_oob_code",
                expiresIn = 60, // 60 seconds
                interval = 5, // 5 seconds
                channel = OobChannel.PUSH,
                bindingMethod = BindingMethod.NONE,
                bindingCode = null,
                challengeType = null
            )
    }

    @Test
    fun `poll returns Authenticated on first attempt`() {
        val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(tokenResponseMockEngine)))
        val oobState = DirectAuthContinuation.OobPending(bindingContext, context)

        val result = runBlocking { oobState.proceed() }

        assertEquals(60, oobState.expirationInSeconds)
        assertIs<Authenticated>(result)
        assertEquals(result, context.authenticationStateFlow.value)
        assertEquals("Bearer", result.token.tokenType)
        assertEquals(3600, result.token.expiresIn)
        assertEquals("example_access_token", result.token.accessToken)
        assertEquals("openid email profile offline_access", result.token.scope)
        assertEquals("example_refresh_token", result.token.refreshToken)
        assertEquals("example_id_token", result.token.idToken)
    }

    @Test
    fun `poll returns Authenticated with a mfa context`() {
        val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(tokenResponseMockEngine)))
        val mfaContext = MfaContext(mfaToken = "test_mfa_token", supportedChallengeTypes = listOf(ChallengeGrantType.OobMfa))
        val oobState = DirectAuthContinuation.OobPending(bindingContext, context, mfaContext)

        val result = runBlocking { oobState.proceed() }

        assertIs<Authenticated>(result)
        assertEquals(result, context.authenticationStateFlow.value)
        assertEquals("Bearer", result.token.tokenType)
    }

    @Test
    fun `poll returns Authenticated after one pending response`() {
        val mockEngine = MockEngine.Queue()
        val collectedValues = mutableListOf<DirectAuthenticationState>()

        mockEngine.enqueue { respond(AUTHORIZATION_PENDING_JSON, HttpStatusCode.BadRequest, contentType) }
        mockEngine.enqueue { respond(TOKEN_RESPONSE_JSON, HttpStatusCode.OK, contentType) }
        val context = createDirectAuthenticationContext(apiExecutor = KtorHttpExecutor(HttpClient(mockEngine)))
        val oobState = DirectAuthContinuation.OobPending(bindingContext, context)

        val job =
            CoroutineScope(Dispatchers.Default).launch {
                context.authenticationStateFlow.collect { value ->
                    if (value !is DirectAuthenticationState.Idle) collectedValues.add(value)
                }
            }

        val result = runBlocking { oobState.proceed() }

        assertIs<Authenticated>(result)
        assertEquals(result, context.authenticationStateFlow.value)
        assertEquals(HttpStatusCode.BadRequest, mockEngine.responseHistory.first().statusCode)
        assertEquals(HttpStatusCode.OK, mockEngine.responseHistory.last().statusCode)
        assertIs<AuthorizationPending>(collectedValues.first())
        assertIs<Authenticated>(collectedValues.last())

        job.cancel()
    }

    @Test
    fun `poll returns InternalError on timeout`() {
        val mockEngine = MockEngine.Queue()
        mockEngine.enqueue { respond(AUTHORIZATION_PENDING_JSON, HttpStatusCode.BadRequest, contentType) }
        mockEngine.enqueue { respond(AUTHORIZATION_PENDING_JSON, HttpStatusCode.BadRequest, contentType) }

        val context = createDirectAuthenticationContext(apiExecutor = KtorHttpExecutor(HttpClient(mockEngine)))
        val oobState = DirectAuthContinuation.OobPending(bindingContext.copy(expiresIn = 1), context)

        val result = runBlocking { oobState.proceed() }

        assertIs<InternalError>(result)
        assertIs<TimeoutCancellationException>(result.throwable)
        assertEquals("Polling timed out after 1 seconds.", result.description)
        assertEquals(result, context.authenticationStateFlow.value)
    }

    @Test
    fun `poll returns Canceled when coroutine is canceled`() =
        runTest {
            val mockEngine = MockEngine.Queue()
            mockEngine.enqueue { respond(AUTHORIZATION_PENDING_JSON, HttpStatusCode.BadRequest, contentType) }
            mockEngine.enqueue { respond(AUTHORIZATION_PENDING_JSON, HttpStatusCode.BadRequest, contentType) }

            val context = createDirectAuthenticationContext(apiExecutor = KtorHttpExecutor(HttpClient(mockEngine)))
            val oobState = DirectAuthContinuation.OobPending(bindingContext.copy(expiresIn = 3_600), context)

            val pollJob =
                launch(Dispatchers.Default) {
                    assertIs<DirectAuthenticationState.Canceled>(oobState.proceed())
                }

            advanceUntilIdle()
            pollJob.cancel()
            advanceUntilIdle()

            pollJob.join()
            assertIs<DirectAuthenticationState.Canceled>(context.authenticationStateFlow.value)
        }

    @Test
    fun `poll returns HttpError on API error`() {
        val context = createDirectAuthenticationContext(apiExecutor = KtorHttpExecutor(HttpClient(oAuth2ErrorMockEngine)))
        val oobState = DirectAuthContinuation.OobPending(bindingContext, context)

        val result = runBlocking { oobState.proceed() }

        assertIs<DirectAuthenticationError.HttpError.Oauth2Error>(result)
        assertEquals("invalid_grant", result.error)
        assertEquals(result, context.authenticationStateFlow.value)
    }

    @Test
    fun `poll returns InternalError`() {
        val context = createDirectAuthenticationContext(apiExecutor = KtorHttpExecutor(HttpClient(malformedJsonOkMockEngine)))
        val oobState = DirectAuthContinuation.OobPending(bindingContext, context)

        val result = runBlocking { oobState.proceed() }

        assertIs<InternalError>(result)
        assertEquals(EXCEPTION, result.errorCode)
        assertTrue(result.description!!.contains("Unexpected JSON token at offset"))
        assertIs<SerializationException>(result.throwable)
    }

    @Test
    fun `poll returns InternalError on generic exception`() {
        val mockEngine = MockEngine { throw kotlinx.io.IOException("Simulated network failure") }
        val context = createDirectAuthenticationContext(apiExecutor = KtorHttpExecutor(HttpClient(mockEngine)))
        val oobState = DirectAuthContinuation.OobPending(bindingContext, context)

        val result = runBlocking { oobState.proceed() }

        assertIs<InternalError>(result)
        assertEquals(EXCEPTION, result.errorCode)
        assertEquals("Simulated network failure", result.description)
        assertIs<IOException>(result.throwable)
        assertEquals(result, context.authenticationStateFlow.value)
    }
}
