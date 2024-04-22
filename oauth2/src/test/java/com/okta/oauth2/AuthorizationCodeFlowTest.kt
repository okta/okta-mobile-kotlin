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
import com.okta.authfoundation.client.IdTokenValidator
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.credential.Token
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuthorizationCodeFlowTest {
    private val mockPrefix = "test_responses"
    private val idTokenValidator = mock<IdTokenValidator>()

    @get:Rule val oktaRule = OktaRule(idTokenValidator = idTokenValidator)

    @Test fun testStart(): Unit = runBlocking {
        val authorizationCodeFlow = AuthorizationCodeFlow()
        assertThat(oktaRule.eventHandler).isEmpty()
        val result = authorizationCodeFlow.start(
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            nonce = "12345689",
            extraRequestParameters = emptyMap(),
            scope = oktaRule.configuration.defaultScope,
            redirectUrl = "unitTest:/login",
        )

        val context = (result as OAuth2ClientResult.Success<AuthorizationCodeFlow.Context>).result

        assertThat(context.codeVerifier).isEqualTo("LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI")
        assertThat(context.state).isEqualTo("25c1d684-8d30-42e3-acc0-b74b35fd47b4")
        assertThat(context.nonce).isEqualTo("12345689")
        assertThat(context.maxAge).isNull()
        val expectedUrlEnding =
            "/oauth2/default/v1/authorize?code_challenge=pqwOUSjNbCP5x5DMxgLy7BTlzI2pjfDq7Q0iwKFjX5s&code_challenge_method=S256&client_id=unit_test_client_id&scope=openid%20email%20profile%20offline_access&redirect_uri=unitTest%3A%2Flogin&response_type=code&state=25c1d684-8d30-42e3-acc0-b74b35fd47b4&nonce=12345689"
        assertThat(context.url.toString()).endsWith(expectedUrlEnding)
    }

    @Test fun testStartWithNoEndpoints(): Unit = runBlocking {
        oktaRule.enqueue(path("/.well-known/openid-configuration")) { response ->
            response.setResponseCode(503)
        }
        val authorizationCodeFlow = AuthorizationCodeFlow(oktaRule.configuration)
        assertThat(oktaRule.eventHandler).isEmpty()
        val result = authorizationCodeFlow.start(
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            nonce = "12345689",
            extraRequestParameters = emptyMap(),
            scope = oktaRule.configuration.defaultScope,
            redirectUrl = "unitTest:/login",
        )

        val context = (result as OAuth2ClientResult.Error<AuthorizationCodeFlow.Context>)

        assertThat(context.exception).isInstanceOf(OAuth2ClientResult.Error.OidcEndpointsNotAvailableException::class.java)
        assertThat(context.exception).hasMessageThat().isEqualTo("OIDC Endpoints not available.")
    }

    @Test fun testStartWithMaxAge(): Unit = runBlocking {
        val requestParameters = mutableMapOf<String, String>()
        requestParameters["max_age"] = "300"
        val authorizationCodeFlow = AuthorizationCodeFlow()
        assertThat(oktaRule.eventHandler).isEmpty()
        val result = authorizationCodeFlow.start(
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            nonce = "12345689",
            extraRequestParameters = requestParameters,
            scope = oktaRule.configuration.defaultScope,
            redirectUrl = "unitTest:/login",
        )

        val context = (result as OAuth2ClientResult.Success<AuthorizationCodeFlow.Context>).result

        assertThat(context.codeVerifier).isEqualTo("LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI")
        assertThat(context.state).isEqualTo("25c1d684-8d30-42e3-acc0-b74b35fd47b4")
        assertThat(context.nonce).isEqualTo("12345689")
        assertThat(context.maxAge).isEqualTo(300)
        val expectedUrlEnding =
            "/oauth2/default/v1/authorize?max_age=300&code_challenge=pqwOUSjNbCP5x5DMxgLy7BTlzI2pjfDq7Q0iwKFjX5s&code_challenge_method=S256&client_id=unit_test_client_id&scope=openid%20email%20profile%20offline_access&redirect_uri=unitTest%3A%2Flogin&response_type=code&state=25c1d684-8d30-42e3-acc0-b74b35fd47b4&nonce=12345689"
        assertThat(context.url.toString()).endsWith(expectedUrlEnding)
    }

    @Test fun testStartWithMalformedMaxAge(): Unit = runBlocking {
        val requestParameters = mutableMapOf<String, String>()
        requestParameters["max_age"] = "a"
        val authorizationCodeFlow = AuthorizationCodeFlow()
        assertThat(oktaRule.eventHandler).isEmpty()
        val result = authorizationCodeFlow.start(
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            nonce = "12345689",
            extraRequestParameters = requestParameters,
            scope = oktaRule.configuration.defaultScope,
            redirectUrl = "unitTest:/login",
        )

        val context = (result as OAuth2ClientResult.Success<AuthorizationCodeFlow.Context>).result

        assertThat(context.codeVerifier).isEqualTo("LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI")
        assertThat(context.state).isEqualTo("25c1d684-8d30-42e3-acc0-b74b35fd47b4")
        assertThat(context.nonce).isEqualTo("12345689")
        assertThat(context.maxAge).isNull()
        val expectedUrlEnding =
            "/oauth2/default/v1/authorize?max_age=a&code_challenge=pqwOUSjNbCP5x5DMxgLy7BTlzI2pjfDq7Q0iwKFjX5s&code_challenge_method=S256&client_id=unit_test_client_id&scope=openid%20email%20profile%20offline_access&redirect_uri=unitTest%3A%2Flogin&response_type=code&state=25c1d684-8d30-42e3-acc0-b74b35fd47b4&nonce=12345689"
        assertThat(context.url.toString()).endsWith(expectedUrlEnding)
    }

    @Test fun testStartWithExtraRequestParameters(): Unit = runBlocking {
        val authorizationCodeFlow = AuthorizationCodeFlow()
        assertThat(oktaRule.eventHandler).isEmpty()
        val extraRequestParameters = mutableMapOf<String, String>()
        extraRequestParameters["prompt"] = "login"
        extraRequestParameters["login_hint"] = "jaynewstrom@example.com"
        val result = authorizationCodeFlow.start(
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            nonce = "12345689",
            extraRequestParameters = extraRequestParameters,
            scope = oktaRule.configuration.defaultScope,
            redirectUrl = "unitTest:/login",
        )

        val context = (result as OAuth2ClientResult.Success<AuthorizationCodeFlow.Context>).result

        assertThat(context.codeVerifier).isEqualTo("LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI")
        assertThat(context.state).isEqualTo("25c1d684-8d30-42e3-acc0-b74b35fd47b4")
        assertThat(context.nonce).isEqualTo("12345689")
        val expectedUrlEnding =
            "/oauth2/default/v1/authorize?prompt=login&login_hint=jaynewstrom%40example.com&code_challenge=pqwOUSjNbCP5x5DMxgLy7BTlzI2pjfDq7Q0iwKFjX5s&code_challenge_method=S256&client_id=unit_test_client_id&scope=openid%20email%20profile%20offline_access&redirect_uri=unitTest%3A%2Flogin&response_type=code&state=25c1d684-8d30-42e3-acc0-b74b35fd47b4&nonce=12345689"
        assertThat(context.url.toString()).endsWith(expectedUrlEnding)
    }

    @Test fun testResume(): Unit = runBlocking {
        reset(idTokenValidator)
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
            body("redirect_uri=unitTest%3A%2Flogin&code_verifier=LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI&client_id=unit_test_client_id&grant_type=authorization_code&code=D13x1bzHhzG7Q1oxSmCcoQg5wjbJzopF4ua1f8UZiE4")
        ) { response ->
            response.testBodyFromFile("$mockPrefix/token.json")
        }
        val authorizationCodeFlow = AuthorizationCodeFlow()
        val flowContext = AuthorizationCodeFlow.Context(
            url = "https://example.okta.com/not_used".toHttpUrl(),
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            nonce = "12345689",
            maxAge = null,
            redirectUrl = "unitTest:/login",
        )
        val result = authorizationCodeFlow.resume(
            uri = Uri.parse("unitTest:/login?state=${flowContext.state}&code=D13x1bzHhzG7Q1oxSmCcoQg5wjbJzopF4ua1f8UZiE4"),
            flowContext = flowContext,
        )

        val captor = argumentCaptor<IdTokenValidator.Parameters>()
        verify(idTokenValidator).validate(any(), any(), captor.capture())
        assertThat(captor.allValues).hasSize(1)
        assertThat(captor.firstValue.nonce).isEqualTo("12345689")
        assertThat(captor.firstValue.maxAge).isNull()

        val token = (result as OAuth2ClientResult.Success<Token>).result
        assertThat(token.tokenType).isEqualTo("Bearer")
        assertThat(token.expiresIn).isEqualTo(3600)
        assertThat(token.accessToken).isEqualTo("exampleAccessToken")
        assertThat(token.scope).isEqualTo("offline_access profile openid email")
        assertThat(token.refreshToken).isEqualTo("exampleRefreshToken")
        assertThat(token.idToken).isEqualTo("eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6IjBvYThmdXAwbEFQWUZDNEkyNjk2IiwiaWF0IjoxNjQ0MzQ3MDY5LCJleHAiOjE2NDQzNTA2NjksImp0aSI6IklELjU1Y3hCdGRZbDhsNmFyS0lTUEJ3ZDB5T1QtOVVDVGFYYVFUWHQybGFSTHMiLCJhbXIiOlsicHdkIl0sImlkcCI6IjAwbzhmb3U3c1JhR0d3ZG40Njk2Iiwic2lkIjoiaWR4V3hrbHBfNGtTeHVDX25VMXBYRC1uQSIsInByZWZlcnJlZF91c2VybmFtZSI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwiYXV0aF90aW1lIjoxNjQ0MzQ3MDY4LCJhdF9oYXNoIjoiZ01jR1RiaEdUMUdfbGRzSG9Kc1B6USIsImRzX2hhc2giOiJEQWVMT0ZScWlmeXNiZ3NyYk9nYm9nIn0.z7LBgWT2O-DUZiOOUzr90qEgLoMiR5eHZsY1V2XPbhfOrjIv9ax9niHE7lPS5GYq02w4Cuf0DbdWjiNj96n4wTPmNU6N0x-XRluv4kved_wBBIvWNLGu_ZZZAFXaIFqmFGxPB6hIsYKvB3FmQCC0NvSXyDquadW9X7bBA7BO7VfX_jOKCkK_1MC1FZdU9n8rppu190Gk-z5dEWegHHtKy3vb12t4NR9CkA2uQgolnii8fNbie-3Z6zAdMXAZXkIcFu43Wn4TGwuzWK25IThcMNsPbLFFI4r0zo9E20IsH4gcJQiE_vFUzukzCsbppaiSAWBdSgES9K-QskWacZIWOg")
    }

    @Test fun testResumeWithMaxAge(): Unit = runBlocking {
        reset(idTokenValidator)
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
            body("redirect_uri=unitTest%3A%2Flogin&code_verifier=LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI&client_id=unit_test_client_id&grant_type=authorization_code&code=D13x1bzHhzG7Q1oxSmCcoQg5wjbJzopF4ua1f8UZiE4")
        ) { response ->
            response.testBodyFromFile("$mockPrefix/token.json")
        }
        val authorizationCodeFlow = AuthorizationCodeFlow()
        val flowContext = AuthorizationCodeFlow.Context(
            url = "https://example.okta.com/not_used".toHttpUrl(),
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            nonce = "12345689",
            maxAge = 300,
            redirectUrl = "unitTest:/login",
        )
        val result = authorizationCodeFlow.resume(
            uri = Uri.parse("unitTest:/login?state=${flowContext.state}&code=D13x1bzHhzG7Q1oxSmCcoQg5wjbJzopF4ua1f8UZiE4"),
            flowContext = flowContext,
        )

        val captor = argumentCaptor<IdTokenValidator.Parameters>()
        verify(idTokenValidator).validate(any(), any(), captor.capture())
        assertThat(captor.allValues).hasSize(1)
        assertThat(captor.firstValue.nonce).isEqualTo("12345689")
        assertThat(captor.firstValue.maxAge).isEqualTo(300)

        val token = (result as OAuth2ClientResult.Success<Token>).result
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
        val authorizationCodeFlow = AuthorizationCodeFlow()
        val flowContext = AuthorizationCodeFlow.Context(
            url = "https://example.okta.com/not_used".toHttpUrl(),
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            nonce = "12345689",
            maxAge = null,
            redirectUrl = "unitTest:/login",
        )
        val result = authorizationCodeFlow.resume(
            uri = Uri.parse("unitTest:/login?state=${flowContext.state}&code=D13x1bzHhzG7Q1oxSmCcoQg5wjbJzopF4ua1f8UZiE4"),
            flowContext = flowContext,
        )
        val errorResult = result as OAuth2ClientResult.Error<Token>
        assertThat(errorResult.exception).isInstanceOf(OAuth2ClientResult.Error.HttpResponseException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("HTTP Error: status code - 503")
    }

    @Test fun testResumeRedirectMismatch(): Unit = runBlocking {
        val authorizationCodeFlow = AuthorizationCodeFlow()
        val flowContext = AuthorizationCodeFlow.Context(
            url = "https://example.okta.com/not_used".toHttpUrl(),
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            nonce = "12345689",
            maxAge = null,
            redirectUrl = "unitTest:/login",
        )
        val result = authorizationCodeFlow.resume(
            uri = Uri.parse("wrong:/login?state=${flowContext.state}&code=D13x1bzHhzG7Q1oxSmCcoQg5wjbJzopF4ua1f8UZiE4"),
            flowContext = flowContext,
        )
        val errorResult = result as OAuth2ClientResult.Error<Token>
        assertThat(errorResult.exception).isInstanceOf(AuthorizationCodeFlow.RedirectSchemeMismatchException::class.java)
    }

    @Test fun testResumeStateMismatch(): Unit = runBlocking {
        val authorizationCodeFlow = AuthorizationCodeFlow()
        val flowContext = AuthorizationCodeFlow.Context(
            url = "https://example.okta.com/not_used".toHttpUrl(),
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            nonce = "12345689",
            maxAge = null,
            redirectUrl = "unitTest:/login",
        )
        val result = authorizationCodeFlow.resume(
            uri = Uri.parse("unitTest:/login?state=MISMATCHED&code=D13x1bzHhzG7Q1oxSmCcoQg5wjbJzopF4ua1f8UZiE4"),
            flowContext = flowContext,
        )
        val errorResult = result as OAuth2ClientResult.Error<Token>
        assertThat(errorResult.exception).isInstanceOf(AuthorizationCodeFlow.ResumeException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("Failed due to state mismatch.")
        val resumeException = errorResult.exception as AuthorizationCodeFlow.ResumeException
        assertThat(resumeException.errorId).isEqualTo("state_mismatch")
    }

    @Test fun testResumeError(): Unit = runBlocking {
        val authorizationCodeFlow = AuthorizationCodeFlow()
        val flowContext = AuthorizationCodeFlow.Context(
            url = "https://example.okta.com/not_used".toHttpUrl(),
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            nonce = "12345689",
            maxAge = null,
            redirectUrl = "unitTest:/login",
        )
        val result = authorizationCodeFlow.resume(
            uri = Uri.parse("unitTest:/login?error=foo"),
            flowContext = flowContext,
        )
        val errorResult = result as OAuth2ClientResult.Error<Token>
        assertThat(errorResult.exception).isInstanceOf(AuthorizationCodeFlow.ResumeException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("An error occurred.")
    }

    @Test fun testResumeErrorDescription(): Unit = runBlocking {
        val authorizationCodeFlow = AuthorizationCodeFlow()
        val flowContext = AuthorizationCodeFlow.Context(
            url = "https://example.okta.com/not_used".toHttpUrl(),
            codeVerifier = "LEadFL0UCCWDlD0cdIiuv7TQfbxOP8OUep0U_xo_3oI",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            nonce = "12345689",
            maxAge = null,
            redirectUrl = "unitTest:/login",
        )
        val result = authorizationCodeFlow.resume(
            uri = Uri.parse("unitTest:/login?error=foo&error_description=Invalid%20Username"),
            flowContext = flowContext,
        )
        val errorResult = result as OAuth2ClientResult.Error<Token>
        assertThat(errorResult.exception).isInstanceOf(AuthorizationCodeFlow.ResumeException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("Invalid Username")
        val resumeException = errorResult.exception as AuthorizationCodeFlow.ResumeException
        assertThat(resumeException.errorId).isEqualTo("foo")
    }
}
