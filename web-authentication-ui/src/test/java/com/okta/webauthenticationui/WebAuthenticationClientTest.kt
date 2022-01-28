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
import com.okta.oauth2.AuthorizationCodeFlow
import com.okta.oauth2.RedirectEndSessionFlow
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.RequestMatchers.method
import com.okta.testhelpers.RequestMatchers.path
import com.okta.testhelpers.testBodyFromFile
import com.okta.webauthenticationui.WebAuthenticationClient.Companion.webAuthenticationClient
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebAuthenticationClientTest {
    private val mockPrefix = "test_responses"

    @get:Rule val oktaRule = OktaRule()

    @Test fun testLogin(): Unit = runBlocking {
        // Test Login.
        val webAuthenticationProvider = mock<WebAuthenticationProvider>()
        val webAuthenticationClient = oktaRule.createOidcClient().webAuthenticationClient(webAuthenticationProvider)
        val context = mock<Context>()
        val flowContext = webAuthenticationClient.login(context)

        verify(webAuthenticationProvider).launch(eq(context), any())
        assertThat(flowContext.url.toString()).contains("/oauth2/default/v1/authorize?code_challenge=")

        // Test Resume.
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/token.json")
        }
        val state = flowContext.url.queryParameter("state")
        val result = webAuthenticationClient.resume(
            uri = Uri.parse("unitTest:/login?state=$state&code=D13x1bzHhzG7Q1oxSmCcoQg5wjbJzopF4ua1f8UZiE4"),
            flowContext = flowContext,
        )

        val tokens = (result as AuthorizationCodeFlow.Result.Tokens).tokens
        assertThat(tokens.tokenType).isEqualTo("Bearer")
        assertThat(tokens.expiresIn).isEqualTo(3600)
        assertThat(tokens.accessToken).isEqualTo("exampleAccessToken")
        assertThat(tokens.scope).isEqualTo("offline_access profile openid email")
        assertThat(tokens.refreshToken).isEqualTo("exampleRefreshToken")
        assertThat(tokens.idToken).isEqualTo("exampleIdToken")
    }

    @Test fun testLogout() {
        // Test Logout.
        val webAuthenticationProvider = mock<WebAuthenticationProvider>()
        val webAuthenticationClient = oktaRule.createOidcClient().webAuthenticationClient(webAuthenticationProvider)
        val context = mock<Context>()
        val flowContext = webAuthenticationClient.logout(context, "exampleIdToken")

        verify(webAuthenticationProvider).launch(eq(context), any())
        assertThat(flowContext.url.toString()).contains("/oauth2/default/v1/logout?id_token_hint=exampleIdToken")

        // Test Resume.
        val state = flowContext.url.queryParameter("state")
        val result = webAuthenticationClient.resume(
            uri = Uri.parse("unitTest:/logout?state=$state"),
            flowContext = flowContext,
        )

        assertThat(result).isInstanceOf(RedirectEndSessionFlow.Result.Success::class.java)
    }
}
