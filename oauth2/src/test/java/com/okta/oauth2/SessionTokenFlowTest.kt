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

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.credential.Token
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.RequestMatchers.bodyPart
import com.okta.testhelpers.RequestMatchers.method
import com.okta.testhelpers.RequestMatchers.path
import com.okta.testhelpers.RequestMatchers.query
import com.okta.testhelpers.testBodyFromFile
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.ConnectException

@RunWith(RobolectricTestRunner::class)
internal class SessionTokenFlowTest {
    private val mockPrefix = "test_responses"

    @get:Rule val oktaRule = OktaRule()

    @Test fun testStart(): Unit = runBlocking {
        oktaRule.enqueue(
            method("GET"),
            path("/oauth2/default/v1/authorize"),
            query("sessionToken", "exampleSessionToken"),
            query("response_type", "code"),
            query("client_id", "unit_test_client_id"),
            query("scope", "openid%20email%20profile%20offline_access"),
            query("redirect_uri", "exampleRedirect%3A%2Fcallback"),
        ) { request, response ->
            val state = request.queryParameterValues("state").firstOrNull() ?: ""

            response.setBody("")
            response.setResponseCode(302)
            response.setHeader("location", "exampleRedirect:/callback?state=$state&code=exampleCode")
        }
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
            bodyPart("code", "exampleCode"),
            bodyPart("redirect_uri", "exampleRedirect%3A%2Fcallback"),
            bodyPart("client_id", "unit_test_client_id"),
            bodyPart("grant_type", "authorization_code"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/token.json")
        }
        val sessionTokenFlow = SessionTokenFlow()
        val result = sessionTokenFlow.start("exampleSessionToken", "exampleRedirect:/callback")
        val token = (result as OAuth2ClientResult.Success<Token>).result
        assertThat(token.tokenType).isEqualTo("Bearer")
        assertThat(token.expiresIn).isEqualTo(3600)
        assertThat(token.accessToken).isEqualTo("exampleAccessToken")
        assertThat(token.scope).isEqualTo("offline_access profile openid email")
        assertThat(token.refreshToken).isEqualTo("exampleRefreshToken")
        assertThat(token.idToken).isEqualTo(
            "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6IjBvYThmdXAwbEFQWUZDNEkyNjk2IiwiaWF0IjoxNjQ0MzQ3MDY5LCJleHAiOjE2NDQzNTA2NjksImp0aSI6IklELjU1Y3hCdGRZbDhsNmFyS0lTUEJ3ZDB5T1QtOVVDVGFYYVFUWHQybGFSTHMiLCJhbXIiOlsicHdkIl0sImlkcCI6IjAwbzhmb3U3c1JhR0d3ZG40Njk2Iiwic2lkIjoiaWR4V3hrbHBfNGtTeHVDX25VMXBYRC1uQSIsInByZWZlcnJlZF91c2VybmFtZSI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwiYXV0aF90aW1lIjoxNjQ0MzQ3MDY4LCJhdF9oYXNoIjoiZ01jR1RiaEdUMUdfbGRzSG9Kc1B6USIsImRzX2hhc2giOiJEQWVMT0ZScWlmeXNiZ3NyYk9nYm9nIn0.z7LBgWT2O-DUZiOOUzr90qEgLoMiR5eHZsY1V2XPbhfOrjIv9ax9niHE7lPS5GYq02w4Cuf0DbdWjiNj96n4wTPmNU6N0x-XRluv4kved_wBBIvWNLGu_ZZZAFXaIFqmFGxPB6hIsYKvB3FmQCC0NvSXyDquadW9X7bBA7BO7VfX_jOKCkK_1MC1FZdU9n8rppu190Gk-z5dEWegHHtKy3vb12t4NR9CkA2uQgolnii8fNbie-3Z6zAdMXAZXkIcFu43Wn4TGwuzWK25IThcMNsPbLFFI4r0zo9E20IsH4gcJQiE_vFUzukzCsbppaiSAWBdSgES9K-QskWacZIWOg"
        )
    }

    @Test fun testStartWithNoEndpoints(): Unit = runBlocking {
        oktaRule.enqueue(path("/.well-known/openid-configuration")) { response ->
            response.setResponseCode(503)
        }
        val sessionTokenFlow = SessionTokenFlow(oktaRule.configuration)
        val result = sessionTokenFlow.start("exampleSessionToken", "exampleRedirect:/callback")
        assertThat(result).isInstanceOf(OAuth2ClientResult.Error::class.java)
        val errorResult = result as OAuth2ClientResult.Error<Token>
        assertThat(errorResult.exception).isInstanceOf(OAuth2ClientResult.Error.OidcEndpointsNotAvailableException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("OIDC Endpoints not available.")
    }

    @Test fun testAuthorizationRequestFailure(): Unit = runBlocking {
        oktaRule.enqueue(
            method("GET"),
            path("/oauth2/default/v1/authorize"),
        ) { response ->
            response.socketPolicy = SocketPolicy.DISCONNECT_AFTER_REQUEST
        }
        val sessionTokenFlow = SessionTokenFlow()
        val result = sessionTokenFlow.start("exampleSessionToken", "exampleRedirect:/callback")
        val exception = (result as OAuth2ClientResult.Error<Token>).exception
        assertThat(exception).isInstanceOf(ConnectException::class.java)
        assertThat(exception).hasMessageThat().startsWith("Failed to connect to")
    }

    @Test fun testTokenRequestFailure(): Unit = runBlocking {
        oktaRule.enqueue(
            method("GET"),
            path("/oauth2/default/v1/authorize"),
        ) { request, response ->
            val state = request.queryParameterValues("state").firstOrNull() ?: ""

            response.setBody("")
            response.setResponseCode(302)
            response.setHeader("location", "exampleRedirect:/callback?state=$state&code=exampleCode")
        }
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
        ) { response ->
            response.setResponseCode(403)
        }
        val sessionTokenFlow = SessionTokenFlow()
        val result = sessionTokenFlow.start("exampleSessionToken", "exampleRedirect:/callback")
        val exception = (result as OAuth2ClientResult.Error<Token>).exception
        assertThat(exception).isInstanceOf(OAuth2ClientResult.Error.HttpResponseException::class.java)
    }

    @Test fun testStartWithExtraParameters(): Unit = runBlocking {
        oktaRule.enqueue(
            method("GET"),
            path("/oauth2/default/v1/authorize"),
            query("sessionToken", "exampleSessionToken"),
            query("response_type", "code"),
            query("client_id", "unit_test_client_id"),
            query("scope", "openid%20email%20profile%20offline_access%20custom%3Aread"),
            query("redirect_uri", "exampleRedirect%3A%2Fcallback"),
            query("extraOne", "bar"),
        ) { request, response ->
            val state = request.queryParameterValues("state").firstOrNull() ?: ""

            response.setBody("")
            response.setResponseCode(302)
            response.setHeader("location", "exampleRedirect:/callback?state=$state&code=exampleCode")
        }
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/token.json")
        }
        val sessionTokenFlow = SessionTokenFlow()
        val result = sessionTokenFlow.start(
            sessionToken = "exampleSessionToken",
            redirectUrl = "exampleRedirect:/callback",
            extraRequestParameters = mapOf(Pair("extraOne", "bar")),
            scope = "openid email profile offline_access custom:read"
        )
        val token = (result as OAuth2ClientResult.Success<Token>).result
        assertThat(token.accessToken).isEqualTo("exampleAccessToken")
    }
}
