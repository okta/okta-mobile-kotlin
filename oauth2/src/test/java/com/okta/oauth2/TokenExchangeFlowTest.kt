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
import com.okta.testhelpers.RequestMatchers.body
import com.okta.testhelpers.RequestMatchers.method
import com.okta.testhelpers.RequestMatchers.path
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class TokenExchangeFlowTest {
    @get:Rule val oktaRule = OktaRule()

    @Test fun testNoEndpoints(): Unit = runBlocking {
        oktaRule.enqueue(path("/.well-known/openid-configuration")) { response ->
            response.setResponseCode(503)
        }
        val flow = TokenExchangeFlow(oktaRule.configuration)
        val result = flow.start("foo", "bar")
        assertThat(result).isInstanceOf(OAuth2ClientResult.Error::class.java)
        val errorResult = result as OAuth2ClientResult.Error<Token>
        assertThat(errorResult.exception).isInstanceOf(OAuth2ClientResult.Error.OidcEndpointsNotAvailableException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("OIDC Endpoints not available.")
    }

    @Test fun testNetworkFailure(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
            body("audience=api%3A%2F%2Fdefault&subject_token_type=urn%3Aietf%3Aparams%3Aoauth%3Atoken-type%3Aid_token&subject_token=foo&actor_token_type=urn%3Ax-oath%3Aparams%3Aoauth%3Atoken-type%3Adevice-secret&actor_token=bar&client_id=unit_test_client_id&grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Atoken-exchange&scope=openid%20email%20profile%20offline_access"),
        ) { response ->
            response.setResponseCode(503)
        }

        val flow = TokenExchangeFlow()
        val result = flow.start("foo", "bar") as OAuth2ClientResult.Error<Token>
        assertThat(result.exception).isInstanceOf(OAuth2ClientResult.Error.HttpResponseException::class.java)
        assertThat(result.exception).hasMessageThat().isEqualTo("HTTP Error: status code - 503")
    }

    @Test fun testReturnsToken(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
            body("audience=api%3A%2F%2Fdefault&subject_token_type=urn%3Aietf%3Aparams%3Aoauth%3Atoken-type%3Aid_token&subject_token=foo&actor_token_type=urn%3Ax-oath%3Aparams%3Aoauth%3Atoken-type%3Adevice-secret&actor_token=bar&client_id=unit_test_client_id&grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Atoken-exchange&scope=openid%20email%20profile%20offline_access"),
        ) { response ->
            val body = """
            {
                "token_type": "Bearer",
                "expires_in": 3600,
                "access_token": "exampleAccessToken",
                "scope": "offline_access openid profile email",
                "refresh_token": "exampleRefreshToken",
                "id_token": "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6IjBvYThmdXAwbEFQWUZDNEkyNjk2IiwiaWF0IjoxNjQ0MzQ3MDY5LCJleHAiOjE2NDQzNTA2NjksImp0aSI6IklELjU1Y3hCdGRZbDhsNmFyS0lTUEJ3ZDB5T1QtOVVDVGFYYVFUWHQybGFSTHMiLCJhbXIiOlsicHdkIl0sImlkcCI6IjAwbzhmb3U3c1JhR0d3ZG40Njk2Iiwic2lkIjoiaWR4V3hrbHBfNGtTeHVDX25VMXBYRC1uQSIsInByZWZlcnJlZF91c2VybmFtZSI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwiYXV0aF90aW1lIjoxNjQ0MzQ3MDY4LCJhdF9oYXNoIjoiZ01jR1RiaEdUMUdfbGRzSG9Kc1B6USIsImRzX2hhc2giOiJEQWVMT0ZScWlmeXNiZ3NyYk9nYm9nIn0.z7LBgWT2O-DUZiOOUzr90qEgLoMiR5eHZsY1V2XPbhfOrjIv9ax9niHE7lPS5GYq02w4Cuf0DbdWjiNj96n4wTPmNU6N0x-XRluv4kved_wBBIvWNLGu_ZZZAFXaIFqmFGxPB6hIsYKvB3FmQCC0NvSXyDquadW9X7bBA7BO7VfX_jOKCkK_1MC1FZdU9n8rppu190Gk-z5dEWegHHtKy3vb12t4NR9CkA2uQgolnii8fNbie-3Z6zAdMXAZXkIcFu43Wn4TGwuzWK25IThcMNsPbLFFI4r0zo9E20IsH4gcJQiE_vFUzukzCsbppaiSAWBdSgES9K-QskWacZIWOg",
                "issued_token_type": "urn:ietf:params:oauth:token-type:access_token"
            }
            """.trimIndent()
            response.setBody(body)
        }

        val flow = TokenExchangeFlow()
        val result = flow.start("foo", "bar") as OAuth2ClientResult.Success<Token>
        assertThat(result.result.accessToken).isEqualTo("exampleAccessToken")
        assertThat(result.result.issuedTokenType).isEqualTo("urn:ietf:params:oauth:token-type:access_token")
    }

    @Test fun testNonDefaultParameters(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
            body("audience=non_default_audience&subject_token_type=urn%3Aietf%3Aparams%3Aoauth%3Atoken-type%3Aid_token&subject_token=foo&actor_token_type=urn%3Ax-oath%3Aparams%3Aoauth%3Atoken-type%3Adevice-secret&actor_token=bar&client_id=unit_test_client_id&grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Atoken-exchange&scope=openid%20profile%20custom_for_test"),
        ) { response ->
            val body = """
            {
                "token_type": "Bearer",
                "expires_in": 3600,
                "access_token": "exampleAccessToken",
                "scope": "offline_access openid profile email",
                "refresh_token": "exampleRefreshToken",
                "id_token": "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6IjBvYThmdXAwbEFQWUZDNEkyNjk2IiwiaWF0IjoxNjQ0MzQ3MDY5LCJleHAiOjE2NDQzNTA2NjksImp0aSI6IklELjU1Y3hCdGRZbDhsNmFyS0lTUEJ3ZDB5T1QtOVVDVGFYYVFUWHQybGFSTHMiLCJhbXIiOlsicHdkIl0sImlkcCI6IjAwbzhmb3U3c1JhR0d3ZG40Njk2Iiwic2lkIjoiaWR4V3hrbHBfNGtTeHVDX25VMXBYRC1uQSIsInByZWZlcnJlZF91c2VybmFtZSI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwiYXV0aF90aW1lIjoxNjQ0MzQ3MDY4LCJhdF9oYXNoIjoiZ01jR1RiaEdUMUdfbGRzSG9Kc1B6USIsImRzX2hhc2giOiJEQWVMT0ZScWlmeXNiZ3NyYk9nYm9nIn0.z7LBgWT2O-DUZiOOUzr90qEgLoMiR5eHZsY1V2XPbhfOrjIv9ax9niHE7lPS5GYq02w4Cuf0DbdWjiNj96n4wTPmNU6N0x-XRluv4kved_wBBIvWNLGu_ZZZAFXaIFqmFGxPB6hIsYKvB3FmQCC0NvSXyDquadW9X7bBA7BO7VfX_jOKCkK_1MC1FZdU9n8rppu190Gk-z5dEWegHHtKy3vb12t4NR9CkA2uQgolnii8fNbie-3Z6zAdMXAZXkIcFu43Wn4TGwuzWK25IThcMNsPbLFFI4r0zo9E20IsH4gcJQiE_vFUzukzCsbppaiSAWBdSgES9K-QskWacZIWOg",
                "issued_token_type": "urn:ietf:params:oauth:token-type:access_token"
            }
            """.trimIndent()
            response.setBody(body)
        }

        val flow = TokenExchangeFlow()
        val result = flow.start("foo", "bar", "non_default_audience", scope = "openid profile custom_for_test") as OAuth2ClientResult.Success<Token>
        assertThat(result.result.accessToken).isEqualTo("exampleAccessToken")
        assertThat(result.result.issuedTokenType).isEqualTo("urn:ietf:params:oauth:token-type:access_token")
    }
}
