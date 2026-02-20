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
import com.okta.directauth.http.EXCEPTION
import com.okta.directauth.malformedJsonOkMockEngine
import com.okta.directauth.model.DirectAuthenticationError.InternalError
import com.okta.directauth.model.DirectAuthenticationState.Authenticated
import com.okta.directauth.oAuth2ErrorMockEngine
import com.okta.directauth.tokenResponseMockEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import java.io.IOException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DirectAuthContinuationPromptStateTest {
    private lateinit var bindingContext: BindingContext
    private lateinit var mfaContext: MfaContext

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
        mfaContext =
            MfaContext(
                mfaToken = "test_mfa_token",
                supportedChallengeTypes = listOf(ChallengeGrantType.OobMfa)
            )
    }

    @Test
    fun `proceed returns Authenticated`() {
        val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(tokenResponseMockEngine)))
        val promptState = DirectAuthContinuation.Prompt(bindingContext, context)

        val result = runBlocking { promptState.proceed("123456") }

        assertEquals(60, promptState.expirationInSeconds)
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
    fun `proceed returns Authenticated with a mfa context`() {
        val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(tokenResponseMockEngine)))
        val promptState = DirectAuthContinuation.Prompt(bindingContext, context, mfaContext)

        val result = runBlocking { promptState.proceed("123456") }

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
    fun `proceed returns HttpError on API error`() {
        val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(oAuth2ErrorMockEngine)))
        val promptState = DirectAuthContinuation.Prompt(bindingContext, context)

        val result = runBlocking { promptState.proceed("123456") }

        assertIs<DirectAuthenticationError.HttpError.Oauth2Error>(result)
        assertEquals("invalid_grant", result.error)
        assertEquals(result, context.authenticationStateFlow.value)
    }

    @Test
    fun `proceed returns InternalError on serialization error`() {
        val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(malformedJsonOkMockEngine)))
        val promptState = DirectAuthContinuation.Prompt(bindingContext, context)

        val result = runBlocking { promptState.proceed("123456") }

        assertIs<InternalError>(result)
        assertEquals(EXCEPTION, result.errorCode)
        assertTrue(result.description!!.contains("Unexpected JSON token at offset"))
        assertIs<SerializationException>(result.throwable)
    }

    @Test
    fun `proceed returns InternalError on generic exception`() {
        val mockEngine = MockEngine { throw IOException("Simulated network failure") }
        val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(mockEngine)))
        val promptState = DirectAuthContinuation.Prompt(bindingContext, context)

        val result = runBlocking { promptState.proceed("123456") }

        assertIs<InternalError>(result)
        assertEquals("Simulated network failure", result.description)
        assertIs<IOException>(result.throwable)
        assertEquals(result, context.authenticationStateFlow.value)
    }
}
