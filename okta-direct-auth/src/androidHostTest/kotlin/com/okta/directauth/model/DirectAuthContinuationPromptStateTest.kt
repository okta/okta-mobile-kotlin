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
import com.okta.authfoundation.api.http.log.AuthFoundationLogger
import com.okta.authfoundation.api.http.log.LogLevel
import com.okta.directauth.http.KtorHttpExecutor
import com.okta.directauth.http.UNKNOWN_ERROR
import com.okta.directauth.malformedJsonOkMockEngine
import com.okta.directauth.model.DirectAuthenticationError.InternalError
import com.okta.directauth.model.DirectAuthenticationState.Authenticated
import com.okta.directauth.oAuth2ErrorMockEngine
import com.okta.directauth.tokenResponseMockEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import java.io.IOException

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

    @Before
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

        assertThat(promptState.expirationInSeconds, equalTo(60))
        assertThat(result, instanceOf(Authenticated::class.java))
        assertThat(context.authenticationStateFlow.value, equalTo(result))
        val authenticated = result as Authenticated
        assertThat(authenticated.token.tokenType, equalTo("Bearer"))
        assertThat(authenticated.token.expiresIn, equalTo(3600))
        assertThat(authenticated.token.accessToken, equalTo("example_access_token"))
        assertThat(authenticated.token.scope, equalTo("openid email profile offline_access"))
        assertThat(authenticated.token.refreshToken, equalTo("example_refresh_token"))
        assertThat(authenticated.token.idToken, equalTo("example_id_token"))
    }

    @Test
    fun `proceed returns Authenticated with a mfa context`() {
        val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(tokenResponseMockEngine)))
        val promptState = DirectAuthContinuation.Prompt(bindingContext, context, mfaContext)

        val result = runBlocking { promptState.proceed("123456") }

        assertThat(result, instanceOf(Authenticated::class.java))
        assertThat(context.authenticationStateFlow.value, equalTo(result))
        val authenticated = result as Authenticated
        assertThat(authenticated.token.tokenType, equalTo("Bearer"))
        assertThat(authenticated.token.expiresIn, equalTo(3600))
        assertThat(authenticated.token.accessToken, equalTo("example_access_token"))
        assertThat(authenticated.token.scope, equalTo("openid email profile offline_access"))
        assertThat(authenticated.token.refreshToken, equalTo("example_refresh_token"))
        assertThat(authenticated.token.idToken, equalTo("example_id_token"))
    }

    @Test
    fun `proceed returns HttpError on API error`() {
        val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(oAuth2ErrorMockEngine)))
        val promptState = DirectAuthContinuation.Prompt(bindingContext, context)

        val result = runBlocking { promptState.proceed("123456") }

        assertThat(result, instanceOf(DirectAuthenticationError.HttpError.Oauth2Error::class.java))
        val error = result as DirectAuthenticationError.HttpError.Oauth2Error
        assertThat(error.error, equalTo("invalid_grant"))
        assertThat(context.authenticationStateFlow.value, equalTo(result))
    }

    @Test
    fun `proceed returns InternalError on serialization error`() {
        val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(malformedJsonOkMockEngine)))
        val promptState = DirectAuthContinuation.Prompt(bindingContext, context)

        val result = runBlocking { promptState.proceed("123456") }

        assertThat(result, instanceOf(InternalError::class.java))
        val error = result as InternalError
        assertThat(error.errorCode, equalTo(UNKNOWN_ERROR))
        assertThat(error.description, containsString("Unexpected JSON token at offset"))
        assertThat(error.throwable, instanceOf(SerializationException::class.java))
    }

    @Test
    fun `proceed returns InternalError on generic exception`() {
        val mockEngine = MockEngine { throw IOException("Simulated network failure") }
        val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(mockEngine)))
        val promptState = DirectAuthContinuation.Prompt(bindingContext, context)

        val result = runBlocking { promptState.proceed("123456") }

        assertThat(result, instanceOf(InternalError::class.java))
        val error = result as InternalError
        assertThat(error.description, equalTo("Simulated network failure"))
        assertThat(error.throwable, instanceOf(IOException::class.java))
        assertThat(context.authenticationStateFlow.value, equalTo(result))
    }
}
