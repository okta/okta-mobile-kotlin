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
package com.okta.oauth2

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.okta.oauth2.AuthorizationCodeFlow.Companion.authorizationCodeFlow
import com.okta.oauth2.events.CustomizeAuthorizationUrlEvent
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.RequestMatchers.body
import com.okta.testhelpers.RequestMatchers.method
import com.okta.testhelpers.RequestMatchers.path
import com.okta.testhelpers.testBodyFromFile
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class AuthorizationCodeFlowTest {
    private val mockPrefix = "test_responses"

    @get:Rule val oktaRule = OktaRule()

    @Test fun testStart() {
        val authorizationCodeFlow = oktaRule.createOidcClient().authorizationCodeFlow()
        assertThat(oktaRule.eventHandler).isEmpty()
        val context = authorizationCodeFlow.start(
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
        )

        assertThat(context.codeVerifier).isEqualTo("LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI")
        assertThat(context.state).isEqualTo("25c1d684-8d30-42e3-acc0-b74b35fd47b4")
        val expectedUrlEnding = "/oauth2/default/v1/authorize?code_challenge=pqwOUSjNbCP5x5DMxgLy7BTlzI2pjfDq7Q0iwKFjX5s&code_challenge_method=S256&client_id=unit_test_client_id&scope=openid%20email%20profile%20offline_access&redirect_uri=unitTest%3A%2Flogin&response_type=code&state=25c1d684-8d30-42e3-acc0-b74b35fd47b4"
        assertThat(context.url.toString()).endsWith(expectedUrlEnding)

        assertThat(oktaRule.eventHandler).hasSize(1)
        assertThat(oktaRule.eventHandler[0]).isInstanceOf(CustomizeAuthorizationUrlEvent::class.java)
        assertThat((oktaRule.eventHandler[0] as CustomizeAuthorizationUrlEvent).httpUrlBuilder.build().toString()).endsWith(expectedUrlEnding)
    }

    @Test fun testResume(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
            body("redirect_uri=unitTest%3A%2Flogin&code_verifier=LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI&client_id=unit_test_client_id&grant_type=authorization_code&code=D13x1bzHhzG7Q1oxSmCcoQg5wjbJzopF4ua1f8UZiE4")
        ) { response ->
            response.testBodyFromFile("$mockPrefix/token.json")
        }
        val authorizationCodeFlow = oktaRule.createOidcClient().authorizationCodeFlow()
        val flowContext = AuthorizationCodeFlow.Context(
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            url = "https://example.okta.com/not_used".toHttpUrl(),
        )
        val result = authorizationCodeFlow.resume(
            uri = Uri.parse("unitTest:/login?state=${flowContext.state}&code=D13x1bzHhzG7Q1oxSmCcoQg5wjbJzopF4ua1f8UZiE4"),
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

    @Test fun testResumeNetworkFailure(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/oauth2/default/v1/token"),
        ) { response ->
            response.setResponseCode(503)
        }
        val authorizationCodeFlow = oktaRule.createOidcClient().authorizationCodeFlow()
        val flowContext = AuthorizationCodeFlow.Context(
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            url = "https://example.okta.com/not_used".toHttpUrl(),
        )
        val result = authorizationCodeFlow.resume(
            uri = Uri.parse("unitTest:/login?state=${flowContext.state}&code=D13x1bzHhzG7Q1oxSmCcoQg5wjbJzopF4ua1f8UZiE4"),
            flowContext = flowContext,
        )
        val errorResult = result as AuthorizationCodeFlow.Result.Error
        assertThat(errorResult.exception).isInstanceOf(IOException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("Request failed.")
        assertThat(errorResult.message).isEqualTo("Token request failed.")
    }

    @Test fun testResumeRedirectMismatch(): Unit = runBlocking {
        val authorizationCodeFlow = oktaRule.createOidcClient().authorizationCodeFlow()
        val flowContext = AuthorizationCodeFlow.Context(
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            url = "https://example.okta.com/not_used".toHttpUrl(),
        )
        val result = authorizationCodeFlow.resume(
            uri = Uri.parse("wrong:/login?state=${flowContext.state}&code=D13x1bzHhzG7Q1oxSmCcoQg5wjbJzopF4ua1f8UZiE4"),
            flowContext = flowContext,
        )
        assertThat(result).isInstanceOf(AuthorizationCodeFlow.Result.RedirectSchemeMismatch::class.java)
    }

    @Test fun testResumeStateMismatch(): Unit = runBlocking {
        val authorizationCodeFlow = oktaRule.createOidcClient().authorizationCodeFlow()
        val flowContext = AuthorizationCodeFlow.Context(
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            url = "https://example.okta.com/not_used".toHttpUrl(),
        )
        val result = authorizationCodeFlow.resume(
            uri = Uri.parse("unitTest:/login?state=MISMATCHED&code=D13x1bzHhzG7Q1oxSmCcoQg5wjbJzopF4ua1f8UZiE4"),
            flowContext = flowContext,
        )
        val errorResult = result as AuthorizationCodeFlow.Result.Error
        assertThat(errorResult.message).isEqualTo("Failed due to state mismatch.")
    }

    @Test fun testResumeError(): Unit = runBlocking {
        val authorizationCodeFlow = oktaRule.createOidcClient().authorizationCodeFlow()
        val flowContext = AuthorizationCodeFlow.Context(
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            url = "https://example.okta.com/not_used".toHttpUrl(),
        )
        val result = authorizationCodeFlow.resume(
            uri = Uri.parse("unitTest:/login?error=foo"),
            flowContext = flowContext,
        )
        val errorResult = result as AuthorizationCodeFlow.Result.Error
        assertThat(errorResult.message).isEqualTo("An error occurred.")
    }

    @Test fun testResumeErrorDescription(): Unit = runBlocking {
        val authorizationCodeFlow = oktaRule.createOidcClient().authorizationCodeFlow()
        val flowContext = AuthorizationCodeFlow.Context(
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            url = "https://example.okta.com/not_used".toHttpUrl(),
        )
        val result = authorizationCodeFlow.resume(
            uri = Uri.parse("unitTest:/login?error=foo&error_description=Invalid%20Username"),
            flowContext = flowContext,
        )
        val errorResult = result as AuthorizationCodeFlow.Result.Error
        assertThat(errorResult.message).isEqualTo("Invalid Username")
    }
}
