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
import com.okta.directauth.api.WebAuthnCeremonyHandler
import com.okta.directauth.http.EXCEPTION
import com.okta.directauth.http.model.PublicKeyCredentialDescriptor
import com.okta.directauth.http.model.PublicKeyCredentialRequestOptions
import com.okta.directauth.http.model.PublicKeyCredentialType
import com.okta.directauth.http.model.UserVerificationRequirement
import com.okta.directauth.http.model.WebAuthnChallengeResponse
import com.okta.directauth.oAuth2ErrorMockEngine
import com.okta.directauth.tokenResponseMockEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DirectAuthContinuationWebAuthnTest {
    private lateinit var mfaContext: MfaContext

    private fun createDirectAuthenticationContext(apiExecutor: KtorHttpExecutor): DirectAuthenticationContext =
        DirectAuthenticationContext(
            issuerUrl = "https://example.okta.com",
            clientId = "test_client_id",
            scope = listOf("openid", "email", "profile", "offline_access"),
            authorizationServerId = "",
            clientSecret = "test_client_secret",
            grantTypes = listOf(GrantType.Password, GrantType.WebAuthn),
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
            clock = { 1654041600 },
            additionalParameters = mapOf()
        )

    private fun createWebAuthnChallengeResponse(
        challenge: String = "dGVzdC1jaGFsbGVuZ2U",
        rpId: String = "example.okta.com",
    ): WebAuthnChallengeResponse =
        WebAuthnChallengeResponse(
            publicKey =
                PublicKeyCredentialRequestOptions(
                    challenge = challenge,
                    rpId = rpId,
                    allowCredentials =
                        listOf(
                            PublicKeyCredentialDescriptor(id = "Y3JlZC0x", type = PublicKeyCredentialType.PUBLIC_KEY)
                        ),
                    timeout = 60000,
                    userVerification = UserVerificationRequirement.PREFERRED
                )
        )

    @BeforeTest
    fun setUp() {
        mfaContext = MfaContext(listOf(ChallengeGrantType.WebAuthnMfa), "test_mfa_token")
    }

    @Test
    fun proceed_withAssertionResponse_returnsAuthenticated() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(tokenResponseMockEngine)))
            val webAuthn = DirectAuthContinuation.WebAuthn(createWebAuthnChallengeResponse(), context)
            val assertionResponse = WebAuthnAssertionResponse("clientDataJSON", "authenticatorData", "signature", "userHandle")

            val result = webAuthn.proceed(assertionResponse)

            assertIs<DirectAuthenticationState.Authenticated>(result)
            assertEquals("example_access_token", result.token.accessToken)
            assertEquals(result, context.authenticationStateFlow.value)
        }

    @Test
    fun proceed_withAssertionResponseAndMfaContext_returnsAuthenticated() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(tokenResponseMockEngine)))
            val webAuthn = DirectAuthContinuation.WebAuthn(createWebAuthnChallengeResponse(), context, mfaContext)
            val assertionResponse = WebAuthnAssertionResponse("clientDataJSON", "authenticatorData", "signature", "userHandle")

            val result = webAuthn.proceed(assertionResponse)

            assertIs<DirectAuthenticationState.Authenticated>(result)
            assertEquals("example_access_token", result.token.accessToken)
            assertEquals(result, context.authenticationStateFlow.value)
        }

    @Test
    fun proceed_withAssertionResponse_returnsOauth2ErrorOnTokenError() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(oAuth2ErrorMockEngine)))
            val webAuthn = DirectAuthContinuation.WebAuthn(createWebAuthnChallengeResponse(), context)
            val assertionResponse = WebAuthnAssertionResponse("clientDataJSON", "authenticatorData", "signature")

            val result = webAuthn.proceed(assertionResponse)

            assertIs<DirectAuthenticationError.HttpError.Oauth2Error>(result)
            assertEquals("invalid_grant", result.error)
            assertEquals(result, context.authenticationStateFlow.value)
        }

    @Test
    fun proceed_withAssertionResponse_returnsInternalErrorOnException() =
        runTest {
            val mockEngine = MockEngine { throw IllegalStateException("Network failure") }
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(mockEngine)))
            val webAuthn = DirectAuthContinuation.WebAuthn(createWebAuthnChallengeResponse(), context)
            val assertionResponse = WebAuthnAssertionResponse("clientDataJSON", "authenticatorData", "signature")

            val result = webAuthn.proceed(assertionResponse)

            assertIs<DirectAuthenticationError.InternalError>(result)
            assertIs<IllegalStateException>(result.throwable)
            assertEquals(result, context.authenticationStateFlow.value)
        }

    @Test
    fun proceed_withHandler_returnsAuthenticated() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(tokenResponseMockEngine)))
            val webAuthn = DirectAuthContinuation.WebAuthn(createWebAuthnChallengeResponse(), context)
            val handler =
                object : WebAuthnCeremonyHandler {
                    override suspend fun performAssertion(challengeData: String): Result<WebAuthnAssertionResponse> =
                        Result.success(WebAuthnAssertionResponse("clientDataJSON", "authenticatorData", "signature", "userHandle"))
                }

            val result = webAuthn.proceed(handler)

            assertIs<DirectAuthenticationState.Authenticated>(result)
            assertEquals("example_access_token", result.token.accessToken)
            assertEquals(result, context.authenticationStateFlow.value)
        }

    @Test
    fun proceed_withHandler_passesChallengeDataToHandler() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(tokenResponseMockEngine)))
            val challengeResponse = createWebAuthnChallengeResponse()
            val webAuthn = DirectAuthContinuation.WebAuthn(challengeResponse, context)
            var receivedChallengeData: String? = null
            val handler =
                object : WebAuthnCeremonyHandler {
                    override suspend fun performAssertion(challengeData: String): Result<WebAuthnAssertionResponse> {
                        receivedChallengeData = challengeData
                        return Result.success(WebAuthnAssertionResponse("clientDataJSON", "authenticatorData", "signature"))
                    }
                }

            webAuthn.proceed(handler)

            assertEquals(webAuthn.challengeData().getOrThrow(), receivedChallengeData)
        }

    @Test
    fun proceed_withFailedHandler_returnsInternalError() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(tokenResponseMockEngine)))
            val webAuthn = DirectAuthContinuation.WebAuthn(createWebAuthnChallengeResponse(), context)
            val handler =
                object : WebAuthnCeremonyHandler {
                    override suspend fun performAssertion(challengeData: String): Result<WebAuthnAssertionResponse> = Result.failure(IllegalStateException("User cancelled"))
                }

            val result = webAuthn.proceed(handler)

            assertIs<DirectAuthenticationError.InternalError>(result)
            assertEquals(EXCEPTION, result.errorCode)
            assertEquals("WebAuthn ceremony failed: User cancelled", result.description)
            assertIs<IllegalStateException>(result.throwable)
            assertEquals(result, context.authenticationStateFlow.value)
        }

    @Test
    fun proceed_withAssertionResponseWithoutUserHandle_returnsAuthenticated() =
        runTest {
            val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(tokenResponseMockEngine)))
            val webAuthn = DirectAuthContinuation.WebAuthn(createWebAuthnChallengeResponse(), context)
            val assertionResponse = WebAuthnAssertionResponse("clientDataJSON", "authenticatorData", "signature")

            val result = webAuthn.proceed(assertionResponse)

            assertIs<DirectAuthenticationState.Authenticated>(result)
        }
}
