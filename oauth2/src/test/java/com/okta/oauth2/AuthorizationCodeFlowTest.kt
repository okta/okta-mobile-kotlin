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
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.credential.Token
import com.okta.oauth2.AuthorizationCodeFlow.Companion.createAuthorizationCodeFlow
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

    @Test fun testStart(): Unit = runBlocking {
        val authorizationCodeFlow = oktaRule.createOidcClient().createAuthorizationCodeFlow()
        assertThat(oktaRule.eventHandler).isEmpty()
        val result = authorizationCodeFlow.start(
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            nonce = "12345689",
            scopes = oktaRule.configuration.defaultScopes,
        )

        val context = (result as OidcClientResult.Success<AuthorizationCodeFlow.Context>).result

        assertThat(context.codeVerifier).isEqualTo("LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI")
        assertThat(context.state).isEqualTo("25c1d684-8d30-42e3-acc0-b74b35fd47b4")
        assertThat(context.nonce).isEqualTo("12345689")
        val expectedUrlEnding =
            "/oauth2/default/v1/authorize?code_challenge=pqwOUSjNbCP5x5DMxgLy7BTlzI2pjfDq7Q0iwKFjX5s&code_challenge_method=S256&client_id=unit_test_client_id&scope=openid%20email%20profile%20offline_access&redirect_uri=unitTest%3A%2Flogin&response_type=code&state=25c1d684-8d30-42e3-acc0-b74b35fd47b4&nonce=12345689"
        assertThat(context.url.toString()).endsWith(expectedUrlEnding)

        assertThat(oktaRule.eventHandler).hasSize(1)
        assertThat(oktaRule.eventHandler[0]).isInstanceOf(CustomizeAuthorizationUrlEvent::class.java)
        assertThat((oktaRule.eventHandler[0] as CustomizeAuthorizationUrlEvent).httpUrlBuilder.build().toString()).endsWith(
            expectedUrlEnding
        )
    }

    @Test fun testResume(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
            body("redirect_uri=unitTest%3A%2Flogin&code_verifier=LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI&client_id=unit_test_client_id&grant_type=authorization_code&code=D13x1bzHhzG7Q1oxSmCcoQg5wjbJzopF4ua1f8UZiE4")
        ) { response ->
            response.testBodyFromFile("$mockPrefix/token.json")
        }
        val authorizationCodeFlow = oktaRule.createOidcClient().createAuthorizationCodeFlow()
        val flowContext = AuthorizationCodeFlow.Context(
            url = "https://example.okta.com/not_used".toHttpUrl(),
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            nonce = "12345689",
        )
        val result = authorizationCodeFlow.resume(
            uri = Uri.parse("unitTest:/login?state=${flowContext.state}&code=D13x1bzHhzG7Q1oxSmCcoQg5wjbJzopF4ua1f8UZiE4"),
            flowContext = flowContext,
        )
        val token = (result as OidcClientResult.Success<Token>).result
        assertThat(token.tokenType).isEqualTo("Bearer")
        assertThat(token.expiresIn).isEqualTo(3600)
        assertThat(token.accessToken).isEqualTo("exampleAccessToken")
        assertThat(token.scope).isEqualTo("offline_access profile openid email")
        assertThat(token.refreshToken).isEqualTo("exampleRefreshToken")
        assertThat(token.idToken).isEqualTo("eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6IjBvYThmdXAwbEFQWUZDNEkyNjk2IiwiaWF0IjoxNjQ0MzQ3MDY5LCJleHAiOjE2NDQzNTA2NjksImp0aSI6IklELjU1Y3hCdGRZbDhsNmFyS0lTUEJ3ZDB5T1QtOVVDVGFYYVFUWHQybGFSTHMiLCJhbXIiOlsicHdkIl0sImlkcCI6IjAwbzhmb3U3c1JhR0d3ZG40Njk2Iiwic2lkIjoiaWR4V3hrbHBfNGtTeHVDX25VMXBYRC1uQSIsInByZWZlcnJlZF91c2VybmFtZSI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwiYXV0aF90aW1lIjoxNjQ0MzQ3MDY4LCJhdF9oYXNoIjoiZ01jR1RiaEdUMUdfbGRzSG9Kc1B6USIsImRzX2hhc2giOiJEQWVMT0ZScWlmeXNiZ3NyYk9nYm9nIn0.z7LBgWT2O-DUZiOOUzr90qEgLoMiR5eHZsY1V2XPbhfOrjIv9ax9niHE7lPS5GYq02w4Cuf0DbdWjiNj96n4wTPmNU6N0x-XRluv4kved_wBBIvWNLGu_ZZZAFXaIFqmFGxPB6hIsYKvB3FmQCC0NvSXyDquadW9X7bBA7BO7VfX_jOKCkK_1MC1FZdU9n8rppu190Gk-z5dEWegHHtKy3vb12t4NR9CkA2uQgolnii8fNbie-3Z6zAdMXAZXkIcFu43Wn4TGwuzWK25IThcMNsPbLFFI4r0zo9E20IsH4gcJQiE_vFUzukzCsbppaiSAWBdSgES9K-QskWacZIWOg")
    }

    @Test fun testResumeNetworkFailure(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/oauth2/default/v1/token"),
        ) { response ->
            response.setResponseCode(503)
        }
        val authorizationCodeFlow = oktaRule.createOidcClient().createAuthorizationCodeFlow()
        val flowContext = AuthorizationCodeFlow.Context(
            url = "https://example.okta.com/not_used".toHttpUrl(),
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            nonce = "12345689",
        )
        val result = authorizationCodeFlow.resume(
            uri = Uri.parse("unitTest:/login?state=${flowContext.state}&code=D13x1bzHhzG7Q1oxSmCcoQg5wjbJzopF4ua1f8UZiE4"),
            flowContext = flowContext,
        )
        val errorResult = result as OidcClientResult.Error<Token>
        assertThat(errorResult.exception).isInstanceOf(IOException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("Request failed.")
    }

    @Test fun testResumeRedirectMismatch(): Unit = runBlocking {
        val authorizationCodeFlow = oktaRule.createOidcClient().createAuthorizationCodeFlow()
        val flowContext = AuthorizationCodeFlow.Context(
            url = "https://example.okta.com/not_used".toHttpUrl(),
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            nonce = "12345689",
        )
        val result = authorizationCodeFlow.resume(
            uri = Uri.parse("wrong:/login?state=${flowContext.state}&code=D13x1bzHhzG7Q1oxSmCcoQg5wjbJzopF4ua1f8UZiE4"),
            flowContext = flowContext,
        )
        val errorResult = result as OidcClientResult.Error<Token>
        assertThat(errorResult.exception).isInstanceOf(AuthorizationCodeFlow.RedirectSchemeMismatchException::class.java)
    }

    @Test fun testResumeStateMismatch(): Unit = runBlocking {
        val authorizationCodeFlow = oktaRule.createOidcClient().createAuthorizationCodeFlow()
        val flowContext = AuthorizationCodeFlow.Context(
            url = "https://example.okta.com/not_used".toHttpUrl(),
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            nonce = "12345689",
        )
        val result = authorizationCodeFlow.resume(
            uri = Uri.parse("unitTest:/login?state=MISMATCHED&code=D13x1bzHhzG7Q1oxSmCcoQg5wjbJzopF4ua1f8UZiE4"),
            flowContext = flowContext,
        )
        val errorResult = result as OidcClientResult.Error<Token>
        assertThat(errorResult.exception).isInstanceOf(AuthorizationCodeFlow.ResumeException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("Failed due to state mismatch.")
    }

    @Test fun testResumeError(): Unit = runBlocking {
        val authorizationCodeFlow = oktaRule.createOidcClient().createAuthorizationCodeFlow()
        val flowContext = AuthorizationCodeFlow.Context(
            url = "https://example.okta.com/not_used".toHttpUrl(),
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            nonce = "12345689",
        )
        val result = authorizationCodeFlow.resume(
            uri = Uri.parse("unitTest:/login?error=foo"),
            flowContext = flowContext,
        )
        val errorResult = result as OidcClientResult.Error<Token>
        assertThat(errorResult.exception).isInstanceOf(AuthorizationCodeFlow.ResumeException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("An error occurred.")
    }

    @Test fun testResumeErrorDescription(): Unit = runBlocking {
        val authorizationCodeFlow = oktaRule.createOidcClient().createAuthorizationCodeFlow()
        val flowContext = AuthorizationCodeFlow.Context(
            url = "https://example.okta.com/not_used".toHttpUrl(),
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            nonce = "12345689",
        )
        val result = authorizationCodeFlow.resume(
            uri = Uri.parse("unitTest:/login?error=foo&error_description=Invalid%20Username"),
            flowContext = flowContext,
        )
        val errorResult = result as OidcClientResult.Error<Token>
        assertThat(errorResult.exception).isInstanceOf(AuthorizationCodeFlow.ResumeException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("Invalid Username")
    }
}
