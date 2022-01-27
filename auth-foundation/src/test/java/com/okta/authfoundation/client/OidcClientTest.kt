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
package com.okta.authfoundation.client

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.dto.OidcIntrospectInfo
import com.okta.authfoundation.dto.OidcTokenType
import com.okta.authfoundation.dto.OidcTokens
import com.okta.authfoundation.dto.OidcUserInfo
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.RequestMatchers.body
import com.okta.testhelpers.RequestMatchers.header
import com.okta.testhelpers.RequestMatchers.method
import com.okta.testhelpers.RequestMatchers.path
import com.okta.testhelpers.testBodyFromFile
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class OidcClientTest {
    private val mockPrefix = "client_test_responses"

    @get:Rule val oktaRule = OktaRule()

    @Test fun testCreate(): Unit = runBlocking {
        oktaRule.enqueue(
            method("GET"),
            path("/.well-known/openid-configuration"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/endpoints.json")
        }
        val result = OidcClient.create(
            oktaRule.configuration,
            oktaRule.baseUrl.newBuilder().encodedPath("/.well-known/openid-configuration").build()
        )
        val endpoints = (result as OidcClientResult.Success<OidcClient>).result.endpoints
        assertThat(endpoints.issuer).isEqualTo("https://example.okta.com/oauth2/default".toHttpUrl())
        assertThat(endpoints.authorizationEndpoint).isEqualTo("https://example.okta.com/oauth2/default/v1/authorize".toHttpUrl())
        assertThat(endpoints.tokenEndpoint).isEqualTo("https://example.okta.com/oauth2/default/v1/token".toHttpUrl())
        assertThat(endpoints.userInfoEndpoint).isEqualTo("https://example.okta.com/oauth2/default/v1/userinfo".toHttpUrl())
        assertThat(endpoints.jwksUri).isEqualTo("https://example.okta.com/oauth2/default/v1/keys".toHttpUrl())
        assertThat(endpoints.registrationEndpoint).isEqualTo("https://example.okta.com/oauth2/v1/clients".toHttpUrl())
        assertThat(endpoints.introspectionEndpoint).isEqualTo("https://example.okta.com/oauth2/default/v1/introspect".toHttpUrl())
        assertThat(endpoints.revocationEndpoint).isEqualTo("https://example.okta.com/oauth2/default/v1/revoke".toHttpUrl())
        assertThat(endpoints.endSessionEndpoint).isEqualTo("https://example.okta.com/oauth2/default/v1/logout".toHttpUrl())
    }

    @Test fun testCreateNetworkFailure(): Unit = runBlocking {
        oktaRule.enqueue(path("/.well-known/openid-configuration")) { response ->
            response.setResponseCode(503)
        }
        val result = OidcClient.create(
            oktaRule.configuration,
            oktaRule.baseUrl.newBuilder().encodedPath("/.well-known/openid-configuration").build()
        )
        val errorResult = (result as OidcClientResult.Error<OidcClient>)
        assertThat(errorResult.exception).isInstanceOf(IOException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("Request failed.")
    }

    @Test fun testGetUserInfo(): Unit = runBlocking {
        oktaRule.enqueue(
            method("GET"),
            path("/oauth2/default/v1/userinfo"),
            header("authorization", "Bearer ExampleToken!"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/userinfo.json")
        }
        val result = oktaRule.createOidcClient().getUserInfo("ExampleToken!")
        val userInfo = (result as OidcClientResult.Success<OidcUserInfo>).result
        assertThat(userInfo.getString("sub")).isEqualTo("00ub41z7mgzNqryMv696")
    }

    @Test fun testGetUserInfoFailure(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/oauth2/default/v1/userinfo"),
        ) { response ->
            response.setResponseCode(503)
        }
        val result = oktaRule.createOidcClient().getUserInfo("ExampleToken!")
        val errorResult = (result as OidcClientResult.Error<OidcUserInfo>)
        assertThat(errorResult.exception).isInstanceOf(IOException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("Request failed.")
    }

    @Test fun testRefreshToken(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            header("content-type", "application/x-www-form-urlencoded"),
            path("/oauth2/default/v1/token"),
            body("client_id=unit_test_client_id&grant_type=refresh_token&refresh_token=ExampleRefreshToken&scope=openid%20email%20profile%20offline_access"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/token.json")
        }
        val result = oktaRule.createOidcClient().refreshToken("ExampleRefreshToken")
        val tokens = (result as OidcClientResult.Success<OidcTokens>).result
        assertThat(tokens.tokenType).isEqualTo("Bearer")
        assertThat(tokens.expiresIn).isEqualTo(3600)
        assertThat(tokens.accessToken).isEqualTo("exampleAccessToken")
        assertThat(tokens.scope).isEqualTo("offline_access profile openid email")
        assertThat(tokens.refreshToken).isEqualTo("exampleRefreshToken")
        assertThat(tokens.idToken).isEqualTo("exampleIdToken")
    }

    @Test fun testRefreshTokenFailure(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/oauth2/default/v1/token"),
        ) { response ->
            response.setResponseCode(503)
        }
        val result = oktaRule.createOidcClient().refreshToken("ExampleToken!")
        val errorResult = (result as OidcClientResult.Error<OidcTokens>)
        assertThat(errorResult.exception).isInstanceOf(IOException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("Request failed.")
    }

    @Test fun testRevokeToken(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            header("content-type", "application/x-www-form-urlencoded"),
            path("/oauth2/default/v1/revoke"),
            body("client_id=unit_test_client_id&token=ExampleRefreshToken"),
        ) { response ->
            response.setResponseCode(200)
        }
        val result = oktaRule.createOidcClient().revokeToken("ExampleRefreshToken")
        assertThat(result).isInstanceOf(OidcClientResult.Success::class.java)
    }

    @Test fun testRevokeTokenFailure(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/oauth2/default/v1/revoke"),
        ) { response ->
            response.setResponseCode(503)
        }
        val result = oktaRule.createOidcClient().revokeToken("ExampleRefreshToken")
        val errorResult = (result as OidcClientResult.Error<Unit>)
        assertThat(errorResult.exception).isInstanceOf(IOException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("Request failed.")
    }

    @Test fun testIntrospectActiveAccessToken(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            header("content-type", "application/x-www-form-urlencoded"),
            path("/oauth2/default/v1/introspect"),
            body("client_id=unit_test_client_id&token=ExampleAccessToken&token_type_hint=access_token"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/introspectActive.json")
        }
        val result = oktaRule.createOidcClient().introspectToken(OidcTokenType.ACCESS_TOKEN, "ExampleAccessToken")
        val tokens = (result as OidcClientResult.Success<OidcIntrospectInfo>).result
        assertThat(tokens.active).isEqualTo(true)
        assertThat(tokens.asMap()["username"]).isEqualTo("example@gmail.com")
    }

    @Test fun testIntrospectInactiveAccessToken(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            header("content-type", "application/x-www-form-urlencoded"),
            path("/oauth2/default/v1/introspect"),
            body("client_id=unit_test_client_id&token=ExampleAccessToken&token_type_hint=access_token"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/introspectInactive.json")
        }
        val result = oktaRule.createOidcClient().introspectToken(OidcTokenType.ACCESS_TOKEN, "ExampleAccessToken")
        val tokens = (result as OidcClientResult.Success<OidcIntrospectInfo>).result
        assertThat(tokens.active).isEqualTo(false)
        assertThat(tokens.asMap().size).isEqualTo(1)
        assertThat(tokens.asMap()["active"]).isEqualTo("false")
    }

    @Test fun testIntrospectActiveRefreshToken(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            header("content-type", "application/x-www-form-urlencoded"),
            path("/oauth2/default/v1/introspect"),
            body("client_id=unit_test_client_id&token=ExampleRefreshToken&token_type_hint=refresh_token"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/introspectActive.json")
        }
        val result = oktaRule.createOidcClient().introspectToken(OidcTokenType.REFRESH_TOKEN, "ExampleRefreshToken")
        val tokens = (result as OidcClientResult.Success<OidcIntrospectInfo>).result
        assertThat(tokens.active).isEqualTo(true)
        assertThat(tokens.asMap()["username"]).isEqualTo("example@gmail.com")
    }

    @Test fun testIntrospectActiveIdToken(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            header("content-type", "application/x-www-form-urlencoded"),
            path("/oauth2/default/v1/introspect"),
            body("client_id=unit_test_client_id&token=ExampleIdToken&token_type_hint=id_token"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/introspectActive.json")
        }
        val result = oktaRule.createOidcClient().introspectToken(OidcTokenType.ID_TOKEN, "ExampleIdToken")
        val tokens = (result as OidcClientResult.Success<OidcIntrospectInfo>).result
        assertThat(tokens.active).isEqualTo(true)
        assertThat(tokens.asMap()["username"]).isEqualTo("example@gmail.com")
    }

    @Test fun testIntrospectFailure(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/oauth2/default/v1/introspect"),
        ) { response ->
            response.setResponseCode(503)
        }
        val result = oktaRule.createOidcClient().introspectToken(OidcTokenType.ID_TOKEN, "ExampleIdToken")
        val errorResult = (result as OidcClientResult.Error<OidcIntrospectInfo>)
        assertThat(errorResult.exception).isInstanceOf(IOException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("Request failed.")
    }
}
