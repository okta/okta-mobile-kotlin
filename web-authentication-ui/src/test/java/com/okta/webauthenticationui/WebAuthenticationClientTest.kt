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
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.credential.Token
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.RequestMatchers.method
import com.okta.testhelpers.RequestMatchers.path
import com.okta.testhelpers.testBodyFromFile
import com.okta.webauthenticationui.WebAuthenticationClient.Companion.createWebAuthenticationClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class WebAuthenticationClientTest {
    private val mockPrefix = "test_responses"

    @get:Rule val oktaRule = OktaRule()

    @Test fun testLogin(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/token.json")
        }

        val webAuthenticationProvider = mock<WebAuthenticationProvider>()
        val webAuthenticationClient = oktaRule.createOidcClient().createWebAuthenticationClient(webAuthenticationProvider)
        val context = mock<Context>()

        val redirectCoordinator = DefaultRedirectCoordinator()
        webAuthenticationClient.redirectCoordinator = redirectCoordinator

        val initializeCountDownLatch = CountDownLatch(1)
        redirectCoordinator.initializerContinuationListeningCallback = {
            initializeCountDownLatch.countDown()
        }
        val redirectCountDownLatch = CountDownLatch(1)
        redirectCoordinator.redirectContinuationListeningCallback = {
            redirectCountDownLatch.countDown()
        }
        val loginResultDeferred = async(Dispatchers.IO) {
            webAuthenticationClient.login(context, "unitTest:/login")
        }
        assertThat(initializeCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        val initializationResult = redirectCoordinator.runInitializationFunction() as RedirectInitializationResult.Success<*>

        assertThat(redirectCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        val state = initializationResult.url.queryParameter("state")
        val uri = Uri.parse("unitTest:/login?state=$state&code=ExampleCode")
        redirectCoordinator.emit(uri)

        val token = (loginResultDeferred.await() as OidcClientResult.Success<Token>).result
        assertThat(token.tokenType).isEqualTo("Bearer")
        assertThat(token.expiresIn).isEqualTo(3600)
        assertThat(token.accessToken).isEqualTo("exampleAccessToken")
        assertThat(token.scope).isEqualTo("offline_access profile openid email")
        assertThat(token.refreshToken).isEqualTo("exampleRefreshToken")
        assertThat(token.idToken).isEqualTo(
            "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6IjBvYThmdXAwbEFQWUZDNEkyNjk2IiwiaWF0IjoxNjQ0MzQ3MDY5LCJleHAiOjE2NDQzNTA2NjksImp0aSI6IklELjU1Y3hCdGRZbDhsNmFyS0lTUEJ3ZDB5T1QtOVVDVGFYYVFUWHQybGFSTHMiLCJhbXIiOlsicHdkIl0sImlkcCI6IjAwbzhmb3U3c1JhR0d3ZG40Njk2Iiwic2lkIjoiaWR4V3hrbHBfNGtTeHVDX25VMXBYRC1uQSIsInByZWZlcnJlZF91c2VybmFtZSI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwiYXV0aF90aW1lIjoxNjQ0MzQ3MDY4LCJhdF9oYXNoIjoiZ01jR1RiaEdUMUdfbGRzSG9Kc1B6USIsImRzX2hhc2giOiJEQWVMT0ZScWlmeXNiZ3NyYk9nYm9nIn0.z7LBgWT2O-DUZiOOUzr90qEgLoMiR5eHZsY1V2XPbhfOrjIv9ax9niHE7lPS5GYq02w4Cuf0DbdWjiNj96n4wTPmNU6N0x-XRluv4kved_wBBIvWNLGu_ZZZAFXaIFqmFGxPB6hIsYKvB3FmQCC0NvSXyDquadW9X7bBA7BO7VfX_jOKCkK_1MC1FZdU9n8rppu190Gk-z5dEWegHHtKy3vb12t4NR9CkA2uQgolnii8fNbie-3Z6zAdMXAZXkIcFu43Wn4TGwuzWK25IThcMNsPbLFFI4r0zo9E20IsH4gcJQiE_vFUzukzCsbppaiSAWBdSgES9K-QskWacZIWOg"
        )
    }

    @Test fun testLoginInitializationCancellation(): Unit = runBlocking {
        val webAuthenticationProvider = mock<WebAuthenticationProvider>()
        val webAuthenticationClient = oktaRule.createOidcClient().createWebAuthenticationClient(webAuthenticationProvider)
        val context = mock<Context>()

        val redirectCoordinator = DefaultRedirectCoordinator()
        webAuthenticationClient.redirectCoordinator = redirectCoordinator

        val initializeCountDownLatch = CountDownLatch(1)
        redirectCoordinator.initializerContinuationListeningCallback = {
            initializeCountDownLatch.countDown()
        }
        val loginResultDeferred = async(Dispatchers.IO) {
            webAuthenticationClient.login(context, "unitTest:/login")
        }
        assertThat(initializeCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        redirectCoordinator.emit(null)

        val exception = (loginResultDeferred.await() as OidcClientResult.Error<Token>).exception
        assertThat(exception).isInstanceOf(WebAuthenticationClient.FlowCancelledException::class.java)
    }

    @Test fun testLoginRedirectCancellation(): Unit = runBlocking {
        val webAuthenticationProvider = mock<WebAuthenticationProvider>()
        val webAuthenticationClient = oktaRule.createOidcClient().createWebAuthenticationClient(webAuthenticationProvider)
        val context = mock<Context>()

        val redirectCoordinator = DefaultRedirectCoordinator()
        webAuthenticationClient.redirectCoordinator = redirectCoordinator

        val initializeCountDownLatch = CountDownLatch(1)
        redirectCoordinator.initializerContinuationListeningCallback = {
            initializeCountDownLatch.countDown()
        }
        val redirectCountDownLatch = CountDownLatch(1)
        redirectCoordinator.redirectContinuationListeningCallback = {
            redirectCountDownLatch.countDown()
        }
        val loginResultDeferred = async(Dispatchers.IO) {
            webAuthenticationClient.login(context, "unitTest:/login")
        }
        assertThat(initializeCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        redirectCoordinator.runInitializationFunction()

        assertThat(redirectCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        redirectCoordinator.emit(null)

        val exception = (loginResultDeferred.await() as OidcClientResult.Error<Token>).exception
        assertThat(exception).isInstanceOf(WebAuthenticationClient.FlowCancelledException::class.java)
    }

    @Test fun testLoginAuthorizationCodeFlowError(): Unit = runBlocking {
        oktaRule.enqueue(path("/.well-known/openid-configuration")) { response ->
            response.setResponseCode(503)
        }
        val client = OidcClient.createFromDiscoveryUrl(
            oktaRule.configuration,
            oktaRule.baseUrl.newBuilder().encodedPath("/.well-known/openid-configuration").build()
        )
        val webAuthenticationProvider = mock<WebAuthenticationProvider>()
        val webAuthenticationClient = client.createWebAuthenticationClient(webAuthenticationProvider)
        val context = mock<Context>()

        val redirectCoordinator = DefaultRedirectCoordinator()
        webAuthenticationClient.redirectCoordinator = redirectCoordinator

        val initializeCountDownLatch = CountDownLatch(1)
        redirectCoordinator.initializerContinuationListeningCallback = {
            initializeCountDownLatch.countDown()
        }
        val loginResultDeferred = async(Dispatchers.IO) {
            webAuthenticationClient.login(context, "unitTest:/login")
        }
        assertThat(initializeCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        redirectCoordinator.runInitializationFunction()

        val exception = (loginResultDeferred.await() as OidcClientResult.Error<Token>).exception
        assertThat(exception).isInstanceOf(OidcClientResult.Error.OidcEndpointsNotAvailableException::class.java)
    }

    @Test fun testLogout(): Unit = runBlocking {
        val webAuthenticationProvider = mock<WebAuthenticationProvider>()
        val webAuthenticationClient = oktaRule.createOidcClient().createWebAuthenticationClient(webAuthenticationProvider)
        val context = mock<Context>()

        val redirectCoordinator = DefaultRedirectCoordinator()
        webAuthenticationClient.redirectCoordinator = redirectCoordinator

        val initializeCountDownLatch = CountDownLatch(1)
        redirectCoordinator.initializerContinuationListeningCallback = {
            initializeCountDownLatch.countDown()
        }
        val redirectCountDownLatch = CountDownLatch(1)
        redirectCoordinator.redirectContinuationListeningCallback = {
            redirectCountDownLatch.countDown()
        }
        val logoutResultDeferred = async(Dispatchers.IO) {
            webAuthenticationClient.logoutOfBrowser(context, "unitTest:/logout", "ExampleIdToken")
        }
        assertThat(initializeCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        val initializationResult = redirectCoordinator.runInitializationFunction() as RedirectInitializationResult.Success<*>

        assertThat(redirectCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        val state = initializationResult.url.queryParameter("state")
        val uri = Uri.parse("unitTest:/logout?state=$state")
        redirectCoordinator.emit(uri)

        assertThat(logoutResultDeferred.await()).isInstanceOf(OidcClientResult.Success::class.java)
    }

    @Test fun testLogoutInitializerCancellation(): Unit = runBlocking {
        val webAuthenticationProvider = mock<WebAuthenticationProvider>()
        val webAuthenticationClient = oktaRule.createOidcClient().createWebAuthenticationClient(webAuthenticationProvider)
        val context = mock<Context>()

        val redirectCoordinator = DefaultRedirectCoordinator()
        webAuthenticationClient.redirectCoordinator = redirectCoordinator

        val initializeCountDownLatch = CountDownLatch(1)
        redirectCoordinator.initializerContinuationListeningCallback = {
            initializeCountDownLatch.countDown()
        }
        val logoutResultDeferred = async(Dispatchers.IO) {
            webAuthenticationClient.logoutOfBrowser(context, "unitTest:/logout", "ExampleIdToken")
        }
        assertThat(initializeCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        redirectCoordinator.emit(null)

        val exception = (logoutResultDeferred.await() as OidcClientResult.Error<Unit>).exception
        assertThat(exception).isInstanceOf(WebAuthenticationClient.FlowCancelledException::class.java)
    }

    @Test fun testLogoutRedirectCancellation(): Unit = runBlocking {
        val webAuthenticationProvider = mock<WebAuthenticationProvider>()
        val webAuthenticationClient = oktaRule.createOidcClient().createWebAuthenticationClient(webAuthenticationProvider)
        val context = mock<Context>()

        val redirectCoordinator = DefaultRedirectCoordinator()
        webAuthenticationClient.redirectCoordinator = redirectCoordinator

        val initializeCountDownLatch = CountDownLatch(1)
        redirectCoordinator.initializerContinuationListeningCallback = {
            initializeCountDownLatch.countDown()
        }
        val redirectCountDownLatch = CountDownLatch(1)
        redirectCoordinator.redirectContinuationListeningCallback = {
            redirectCountDownLatch.countDown()
        }
        val logoutResultDeferred = async(Dispatchers.IO) {
            webAuthenticationClient.logoutOfBrowser(context, "unitTest:/logout", "ExampleIdToken")
        }
        assertThat(initializeCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        redirectCoordinator.runInitializationFunction()

        assertThat(redirectCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        redirectCoordinator.emit(null)

        val exception = (logoutResultDeferred.await() as OidcClientResult.Error<Unit>).exception
        assertThat(exception).isInstanceOf(WebAuthenticationClient.FlowCancelledException::class.java)
    }

    @Test fun testLogoutEndSessionRedirectFlowError(): Unit = runBlocking {
        oktaRule.enqueue(path("/.well-known/openid-configuration")) { response ->
            response.setResponseCode(503)
        }
        val client = OidcClient.createFromDiscoveryUrl(
            oktaRule.configuration,
            oktaRule.baseUrl.newBuilder().encodedPath("/.well-known/openid-configuration").build()
        )
        val webAuthenticationProvider = mock<WebAuthenticationProvider>()
        val webAuthenticationClient = client.createWebAuthenticationClient(webAuthenticationProvider)
        val context = mock<Context>()

        val redirectCoordinator = DefaultRedirectCoordinator()
        webAuthenticationClient.redirectCoordinator = redirectCoordinator

        val initializeCountDownLatch = CountDownLatch(1)
        redirectCoordinator.initializerContinuationListeningCallback = {
            initializeCountDownLatch.countDown()
        }
        val logoutResultDeferred = async(Dispatchers.IO) {
            webAuthenticationClient.logoutOfBrowser(context, "unitTest:/logout", "ExampleIdToken")
        }
        assertThat(initializeCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        redirectCoordinator.runInitializationFunction()

        val exception = (logoutResultDeferred.await() as OidcClientResult.Error<Unit>).exception
        assertThat(exception).isInstanceOf(OidcClientResult.Error.OidcEndpointsNotAvailableException::class.java)
    }
}
