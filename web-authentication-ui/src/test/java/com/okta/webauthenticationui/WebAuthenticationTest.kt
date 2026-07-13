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
package com.okta.webauthenticationui

import android.content.Context
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.credential.Token
import com.okta.oauth2.kmp.AuthorizationCodeFlow
import com.okta.oauth2.kmp.AuthorizationCodeFlowContext
import com.okta.oauth2.kmp.RedirectEndSessionFlow
import com.okta.oauth2.kmp.RedirectEndSessionFlowContext
import com.okta.testhelpers.OktaRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class WebAuthenticationTest {
    @get:Rule val oktaRule = OktaRule()

    private val loginFlowContext =
        AuthorizationCodeFlowContext(
            url = "http://example.com/authorize?state=test-login-state",
            redirectUrl = "unitTest:/login",
            codeVerifier = "test-code-verifier",
            state = "test-login-state",
            nonce = "test-nonce",
            maxAge = null
        )

    private val loginTokenInfo: TokenInfo =
        object : TokenInfo {
            override val id = "test-token-id"
            override val clientId = "test-client-id"
            override val issuerUrl = "https://example-test.okta.com/oauth2/default"
            override val tokenType = "Bearer"
            override val expiresIn = 3600
            override val accessToken = "exampleAccessToken"
            override val scope = "offline_access profile openid email"
            override val refreshToken = "exampleRefreshToken"
            override val idToken =
                "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ" +
                    ".eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5l" +
                    "d3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9v" +
                    "YXV0aDIvZGVmYXVsdCIsImF1ZCI6IjBvYThmdXAwbEFQWUZDNEkyNjk2IiwiaWF0IjoxNjQ0MzQ3MDY5LCJleHAi" +
                    "OjE2NDQzNTA2NjksImp0aSI6IklELjU1Y3hCdGRZbDhsNmFyS0lTUEJ3ZDB5T1QtOVVDVGFYYVFUWHQybGFSTHMi" +
                    "LCJhbXIiOlsicHdkIl0sImlkcCI6IjAwbzhmb3U3c1JhR0d3ZG40Njk2Iiwic2lkIjoiaWR4V3hrbHBfNGtTeHVD" +
                    "X25VMXBYRC1uQSIsInByZWZlcnJlZF91c2VybmFtZSI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwiYXV0aF90" +
                    "aW1lIjoxNjQ0MzQ3MDY4LCJhdF9oYXNoIjoiZ01jR1RiaEdUMUdfbGRzSG9Kc1B6USIsImRzX2hhc2giOiJEQWVM" +
                    "T0ZScWlmeXNiZ3NyYk9nYm9nIn0.z7LBgWT2O-DUZiOOUzr90qEgLoMiR5eHZsY1V2XPbhfOrjIv9ax9niHE7lPS" +
                    "5GYq02w4Cuf0DbdWjiNj96n4wTPmNU6N0x-XRluv4kved_wBBIvWNLGu_ZZZAFXaIFqmFGxPB6hIsYKvB3FmQCC0N" +
                    "vSXyDquadW9X7bBA7BO7VfX_jOKCkK_1MC1FZdU9n8rppu190Gk-z5dEWegHHtKy3vb12t4NR9CkA2uQgolnii8fN" +
                    "bie-3Z6zAdMXAZXkIcFu43Wn4TGwuzWK25IThcMNsPbLFFI4r0zo9E20IsH4gcJQiE_vFUzukzCsbppaiSAWBdSgE" +
                    "S9K-QskWacZIWOg"
            override val deviceSecret = "exampleDeviceSecret"
            override val issuedTokenType = "urn:ietf:params:oauth:token-type:access_token"
        }

    private val logoutFlowContext =
        RedirectEndSessionFlowContext(
            url = "http://example.com/logout?state=test-logout-state",
            redirectUrl = "unitTest:/logout",
            state = "test-logout-state"
        )

    private fun authorizationCodeFlowStub(
        startResult: Result<AuthorizationCodeFlowContext> = Result.success(loginFlowContext),
        resumeResult: Result<TokenInfo> = Result.success(loginTokenInfo),
    ): AuthorizationCodeFlow =
        object : AuthorizationCodeFlow {
            override suspend fun start(
                redirectUrl: String,
                extraRequestParameters: Map<String, String>,
                scope: String?,
            ): Result<AuthorizationCodeFlowContext> = startResult

            override suspend fun resume(
                uri: String,
                flowContext: AuthorizationCodeFlowContext,
            ): Result<TokenInfo> = resumeResult
        }

    private fun redirectEndSessionFlowStub(
        startResult: Result<RedirectEndSessionFlowContext> = Result.success(logoutFlowContext),
        resumeResult: Result<Unit> = Result.success(Unit),
    ): RedirectEndSessionFlow =
        object : RedirectEndSessionFlow {
            override suspend fun start(
                idToken: String,
                redirectUrl: String,
                extraRequestParameters: Map<String, String>,
            ): Result<RedirectEndSessionFlowContext> = startResult

            override fun resume(
                uri: String,
                flowContext: RedirectEndSessionFlowContext,
            ): Result<Unit> = resumeResult
        }

    @Test fun testLogin(): Unit =
        runTest {
            val webAuthenticationProvider = mock<WebAuthenticationProvider>()
            val webAuthentication = WebAuthentication(webAuthenticationProvider)
            val context = mock<Context>()

            val redirectCoordinator = DefaultRedirectCoordinator(this)
            webAuthentication.redirectCoordinator = redirectCoordinator
            webAuthentication.authorizationCodeFlow = authorizationCodeFlowStub()

            val initializeCountDownLatch = CountDownLatch(1)
            redirectCoordinator.initializerContinuationListeningCallback = {
                initializeCountDownLatch.countDown()
            }
            val redirectCountDownLatch = CountDownLatch(1)
            redirectCoordinator.redirectContinuationListeningCallback = {
                redirectCountDownLatch.countDown()
            }
            val loginResultDeferred =
                async(Dispatchers.IO) {
                    webAuthentication.login(context, "unitTest:/login")
                }
            assertThat(initializeCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            val initializationResult = redirectCoordinator.runInitializationFunction() as RedirectInitializationResult.Success<*>

            assertThat(redirectCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            val state = initializationResult.url.queryParameter("state")
            val uri = Uri.parse("unitTest:/login?state=$state&code=ExampleCode")
            redirectCoordinator.emit(uri)

            val token = (loginResultDeferred.await() as OAuth2ClientResult.Success<Token>).result
            assertThat(token.tokenType).isEqualTo("Bearer")
            assertThat(token.expiresIn).isEqualTo(3600)
            assertThat(token.accessToken).isEqualTo("exampleAccessToken")
            assertThat(token.scope).isEqualTo("offline_access profile openid email")
            assertThat(token.refreshToken).isEqualTo("exampleRefreshToken")
            assertThat(token.idToken).isEqualTo(loginTokenInfo.idToken)
            assertThat(token.deviceSecret).isEqualTo("exampleDeviceSecret")
            assertThat(token.issuedTokenType).isEqualTo("urn:ietf:params:oauth:token-type:access_token")
            assertThat(token.oidcConfiguration).isSameInstanceAs(oktaRule.configuration)
        }

    @Test fun testLoginResumeError(): Unit =
        runTest {
            val webAuthenticationProvider = mock<WebAuthenticationProvider>()
            val webAuthentication = WebAuthentication(oktaRule.configuration, webAuthenticationProvider)
            val context = mock<Context>()

            val redirectCoordinator = DefaultRedirectCoordinator(this)
            webAuthentication.redirectCoordinator = redirectCoordinator
            webAuthentication.authorizationCodeFlow =
                authorizationCodeFlowStub(
                    resumeResult = Result.failure(AuthorizationCodeFlow.ResumeException("state mismatch", "state_mismatch"))
                )

            val initializeCountDownLatch = CountDownLatch(1)
            redirectCoordinator.initializerContinuationListeningCallback = {
                initializeCountDownLatch.countDown()
            }
            val redirectCountDownLatch = CountDownLatch(1)
            redirectCoordinator.redirectContinuationListeningCallback = {
                redirectCountDownLatch.countDown()
            }
            val loginResultDeferred =
                async(Dispatchers.IO) {
                    webAuthentication.login(context, "unitTest:/login")
                }
            assertThat(initializeCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            redirectCoordinator.runInitializationFunction()

            assertThat(redirectCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            redirectCoordinator.emit(Uri.parse("unitTest:/login?state=bad-state&code=ExampleCode"))

            val exception = (loginResultDeferred.await() as OAuth2ClientResult.Error<Token>).exception
            assertThat(exception).isInstanceOf(AuthorizationCodeFlow.ResumeException::class.java)
        }

    @Test fun testLogoutResumeError(): Unit =
        runTest {
            val webAuthenticationProvider = mock<WebAuthenticationProvider>()
            val webAuthentication = WebAuthentication(oktaRule.configuration, webAuthenticationProvider)
            val context = mock<Context>()

            val redirectCoordinator = DefaultRedirectCoordinator(this)
            webAuthentication.redirectCoordinator = redirectCoordinator
            webAuthentication.redirectEndSessionFlow =
                redirectEndSessionFlowStub(
                    resumeResult = Result.failure(RuntimeException("state mismatch"))
                )

            val initializeCountDownLatch = CountDownLatch(1)
            redirectCoordinator.initializerContinuationListeningCallback = {
                initializeCountDownLatch.countDown()
            }
            val redirectCountDownLatch = CountDownLatch(1)
            redirectCoordinator.redirectContinuationListeningCallback = {
                redirectCountDownLatch.countDown()
            }
            val logoutResultDeferred =
                async(Dispatchers.IO) {
                    webAuthentication.logoutOfBrowser(context, "unitTest:/logout", "ExampleIdToken")
                }
            assertThat(initializeCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            redirectCoordinator.runInitializationFunction()

            assertThat(redirectCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            redirectCoordinator.emit(Uri.parse("unitTest:/logout?state=bad-state"))

            val exception = (logoutResultDeferred.await() as OAuth2ClientResult.Error<Unit>).exception
            assertThat(exception).isInstanceOf(RuntimeException::class.java)
        }

    @Test fun testLoginMalformedUrl_ReturnsError(): Unit =
        runTest {
            val webAuthenticationProvider = mock<WebAuthenticationProvider>()
            val webAuthentication = WebAuthentication(webAuthenticationProvider)
            val context = mock<Context>()

            val redirectCoordinator = DefaultRedirectCoordinator(this)
            webAuthentication.redirectCoordinator = redirectCoordinator
            webAuthentication.authorizationCodeFlow =
                authorizationCodeFlowStub(
                    startResult =
                        Result.success(
                            AuthorizationCodeFlowContext(
                                url = "not a valid url",
                                redirectUrl = "unitTest:/login",
                                codeVerifier = "cv",
                                state = "s",
                                nonce = "n",
                                maxAge = null
                            )
                        )
                )

            val initializeCountDownLatch = CountDownLatch(1)
            redirectCoordinator.initializerContinuationListeningCallback = {
                initializeCountDownLatch.countDown()
            }
            val loginResultDeferred =
                async(Dispatchers.IO) {
                    webAuthentication.login(context, "unitTest:/login")
                }
            assertThat(initializeCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            redirectCoordinator.runInitializationFunction()

            val exception = (loginResultDeferred.await() as OAuth2ClientResult.Error<Token>).exception
            assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
        }

    @Test fun testLogoutMalformedUrl_ReturnsError(): Unit =
        runTest {
            val webAuthenticationProvider = mock<WebAuthenticationProvider>()
            val webAuthentication = WebAuthentication(webAuthenticationProvider)
            val context = mock<Context>()

            val redirectCoordinator = DefaultRedirectCoordinator(this)
            webAuthentication.redirectCoordinator = redirectCoordinator
            webAuthentication.redirectEndSessionFlow =
                redirectEndSessionFlowStub(
                    startResult =
                        Result.success(
                            RedirectEndSessionFlowContext(
                                url = "not a valid url",
                                redirectUrl = "unitTest:/logout",
                                state = "s"
                            )
                        )
                )

            val initializeCountDownLatch = CountDownLatch(1)
            redirectCoordinator.initializerContinuationListeningCallback = {
                initializeCountDownLatch.countDown()
            }
            val logoutResultDeferred =
                async(Dispatchers.IO) {
                    webAuthentication.logoutOfBrowser(context, "unitTest:/logout", "ExampleIdToken")
                }
            assertThat(initializeCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            redirectCoordinator.runInitializationFunction()

            val exception = (logoutResultDeferred.await() as OAuth2ClientResult.Error<Unit>).exception
            assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
        }

    @Test fun testLoginInitializationCancellation(): Unit =
        runTest {
            val webAuthenticationProvider = mock<WebAuthenticationProvider>()
            val webAuthentication = WebAuthentication(webAuthenticationProvider)
            val context = mock<Context>()

            val redirectCoordinator = DefaultRedirectCoordinator(this)
            webAuthentication.redirectCoordinator = redirectCoordinator
            webAuthentication.authorizationCodeFlow = authorizationCodeFlowStub()

            val initializeCountDownLatch = CountDownLatch(1)
            redirectCoordinator.initializerContinuationListeningCallback = {
                initializeCountDownLatch.countDown()
            }
            val loginResultDeferred =
                async(Dispatchers.IO) {
                    webAuthentication.login(context, "unitTest:/login")
                }
            assertThat(initializeCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            redirectCoordinator.emit(null)

            val exception = (loginResultDeferred.await() as OAuth2ClientResult.Error<Token>).exception
            assertThat(exception).isInstanceOf(WebAuthentication.FlowCancelledException::class.java)
        }

    @Test fun testLoginRedirectCancellation(): Unit =
        runTest {
            val webAuthenticationProvider = mock<WebAuthenticationProvider>()
            val webAuthentication = WebAuthentication(webAuthenticationProvider)
            val context = mock<Context>()

            val redirectCoordinator = DefaultRedirectCoordinator(this)
            webAuthentication.redirectCoordinator = redirectCoordinator
            webAuthentication.authorizationCodeFlow = authorizationCodeFlowStub()

            val initializeCountDownLatch = CountDownLatch(1)
            redirectCoordinator.initializerContinuationListeningCallback = {
                initializeCountDownLatch.countDown()
            }
            val redirectCountDownLatch = CountDownLatch(1)
            redirectCoordinator.redirectContinuationListeningCallback = {
                redirectCountDownLatch.countDown()
            }
            val loginResultDeferred =
                async(Dispatchers.IO) {
                    webAuthentication.login(context, "unitTest:/login")
                }
            assertThat(initializeCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            redirectCoordinator.runInitializationFunction()

            assertThat(redirectCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            redirectCoordinator.emit(null)

            val exception = (loginResultDeferred.await() as OAuth2ClientResult.Error<Token>).exception
            assertThat(exception).isInstanceOf(WebAuthentication.FlowCancelledException::class.java)
        }

    @Test fun testLoginAuthorizationCodeFlowError(): Unit =
        runTest {
            val webAuthenticationProvider = mock<WebAuthenticationProvider>()
            val webAuthentication = WebAuthentication(oktaRule.configuration, webAuthenticationProvider)
            val context = mock<Context>()

            val redirectCoordinator = DefaultRedirectCoordinator(this)
            webAuthentication.redirectCoordinator = redirectCoordinator
            webAuthentication.authorizationCodeFlow =
                authorizationCodeFlowStub(
                    startResult = Result.failure(IllegalStateException("OIDC Endpoints not available."))
                )

            val initializeCountDownLatch = CountDownLatch(1)
            redirectCoordinator.initializerContinuationListeningCallback = {
                initializeCountDownLatch.countDown()
            }
            val loginResultDeferred =
                async(Dispatchers.IO) {
                    webAuthentication.login(context, "unitTest:/login")
                }
            assertThat(initializeCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            redirectCoordinator.runInitializationFunction()

            val exception = (loginResultDeferred.await() as OAuth2ClientResult.Error<Token>).exception
            assertThat(exception).isInstanceOf(IllegalStateException::class.java)
        }

    @Test fun testLogout(): Unit =
        runTest {
            val webAuthenticationProvider = mock<WebAuthenticationProvider>()
            val webAuthentication = WebAuthentication(webAuthenticationProvider)
            val context = mock<Context>()

            val redirectCoordinator = DefaultRedirectCoordinator(this)
            webAuthentication.redirectCoordinator = redirectCoordinator
            webAuthentication.redirectEndSessionFlow = redirectEndSessionFlowStub()

            val initializeCountDownLatch = CountDownLatch(1)
            redirectCoordinator.initializerContinuationListeningCallback = {
                initializeCountDownLatch.countDown()
            }
            val redirectCountDownLatch = CountDownLatch(1)
            redirectCoordinator.redirectContinuationListeningCallback = {
                redirectCountDownLatch.countDown()
            }
            val logoutResultDeferred =
                async(Dispatchers.IO) {
                    webAuthentication.logoutOfBrowser(context, "unitTest:/logout", "ExampleIdToken")
                }
            assertThat(initializeCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            val initializationResult = redirectCoordinator.runInitializationFunction() as RedirectInitializationResult.Success<*>

            assertThat(redirectCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            val state = initializationResult.url.queryParameter("state")
            val uri = Uri.parse("unitTest:/logout?state=$state")
            redirectCoordinator.emit(uri)

            assertThat(logoutResultDeferred.await()).isInstanceOf(OAuth2ClientResult.Success::class.java)
        }

    @Test fun testLogoutInitializerCancellation(): Unit =
        runTest {
            val webAuthenticationProvider = mock<WebAuthenticationProvider>()
            val webAuthentication = WebAuthentication(webAuthenticationProvider)
            val context = mock<Context>()

            val redirectCoordinator = DefaultRedirectCoordinator(this)
            webAuthentication.redirectCoordinator = redirectCoordinator
            webAuthentication.redirectEndSessionFlow = redirectEndSessionFlowStub()

            val initializeCountDownLatch = CountDownLatch(1)
            redirectCoordinator.initializerContinuationListeningCallback = {
                initializeCountDownLatch.countDown()
            }
            val logoutResultDeferred =
                async(Dispatchers.IO) {
                    webAuthentication.logoutOfBrowser(context, "unitTest:/logout", "ExampleIdToken")
                }
            assertThat(initializeCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            redirectCoordinator.emit(null)

            val exception = (logoutResultDeferred.await() as OAuth2ClientResult.Error<Unit>).exception
            assertThat(exception).isInstanceOf(WebAuthentication.FlowCancelledException::class.java)
        }

    @Test fun testLogoutRedirectCancellation(): Unit =
        runTest {
            val webAuthenticationProvider = mock<WebAuthenticationProvider>()
            val webAuthentication = WebAuthentication(webAuthenticationProvider)
            val context = mock<Context>()

            val redirectCoordinator = DefaultRedirectCoordinator(this)
            webAuthentication.redirectCoordinator = redirectCoordinator
            webAuthentication.redirectEndSessionFlow = redirectEndSessionFlowStub()

            val initializeCountDownLatch = CountDownLatch(1)
            redirectCoordinator.initializerContinuationListeningCallback = {
                initializeCountDownLatch.countDown()
            }
            val redirectCountDownLatch = CountDownLatch(1)
            redirectCoordinator.redirectContinuationListeningCallback = {
                redirectCountDownLatch.countDown()
            }
            val logoutResultDeferred =
                async(Dispatchers.IO) {
                    webAuthentication.logoutOfBrowser(context, "unitTest:/logout", "ExampleIdToken")
                }
            assertThat(initializeCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            redirectCoordinator.runInitializationFunction()

            assertThat(redirectCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            redirectCoordinator.emit(null)

            val exception = (logoutResultDeferred.await() as OAuth2ClientResult.Error<Unit>).exception
            assertThat(exception).isInstanceOf(WebAuthentication.FlowCancelledException::class.java)
        }

    @Test fun testLogoutEndSessionRedirectFlowError(): Unit =
        runTest {
            val webAuthenticationProvider = mock<WebAuthenticationProvider>()
            val webAuthentication = WebAuthentication(oktaRule.configuration, webAuthenticationProvider)
            val context = mock<Context>()

            val redirectCoordinator = DefaultRedirectCoordinator(this)
            webAuthentication.redirectCoordinator = redirectCoordinator
            webAuthentication.redirectEndSessionFlow =
                redirectEndSessionFlowStub(
                    startResult = Result.failure(IllegalStateException("OIDC Endpoints not available."))
                )

            val initializeCountDownLatch = CountDownLatch(1)
            redirectCoordinator.initializerContinuationListeningCallback = {
                initializeCountDownLatch.countDown()
            }
            val logoutResultDeferred =
                async(Dispatchers.IO) {
                    webAuthentication.logoutOfBrowser(context, "unitTest:/logout", "ExampleIdToken")
                }
            assertThat(initializeCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            redirectCoordinator.runInitializationFunction()

            val exception = (logoutResultDeferred.await() as OAuth2ClientResult.Error<Unit>).exception
            assertThat(exception).isInstanceOf(IllegalStateException::class.java)
        }
}
