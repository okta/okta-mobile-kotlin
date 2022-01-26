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
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.client.OidcEndpoints
import com.okta.authfoundation.events.EventCoordinator
import com.okta.oauth2.ResourceOwnerFlow.Companion.resourceOwnerFlow
import com.okta.testnetworking.NetworkRule
import com.okta.testnetworking.RequestMatchers.body
import com.okta.testnetworking.RequestMatchers.method
import com.okta.testnetworking.RequestMatchers.path
import com.okta.testnetworking.testBodyFromFile
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class ResourceOwnerFlowTest {
    private val mockPrefix = "test_responses"

    @get:Rule val networkRule = NetworkRule()

    private val eventHandler = RecordingEventHandler()

    private val configuration: OidcConfiguration = OidcConfiguration(
        clientId = "unit_test_client_id",
        scopes = setOf("openid", "email", "profile", "offline_access"),
        signInRedirectUri = "unitTest:/login",
        signOutRedirectUri = "unitTest:/logout",
        okHttpCallFactory = networkRule.okHttpClient,
        eventCoordinator = EventCoordinator(eventHandler)
    )

    private fun createOidcClient(): OidcClient {
        val endpoints = OidcEndpoints(
            issuer = networkRule.baseUrl.newBuilder().encodedPath("/oauth2/default").build(),
            authorizationEndpoint = networkRule.baseUrl.newBuilder().encodedPath("/oauth2/default/v1/authorize").build(),
            tokenEndpoint = networkRule.baseUrl.newBuilder().encodedPath("/oauth2/default/v1/token").build(),
            userInfoEndpoint = networkRule.baseUrl.newBuilder().encodedPath("/oauth2/default/v1/userinfo").build(),
            jwksUri = networkRule.baseUrl.newBuilder().encodedPath("/oauth2/default/v1/keys").build(),
            registrationEndpoint = networkRule.baseUrl.newBuilder().encodedPath("/oauth2/v1/clients").build(),
            introspectionEndpoint = networkRule.baseUrl.newBuilder().encodedPath("/oauth2/default/v1/introspect").build(),
            revocationEndpoint = networkRule.baseUrl.newBuilder().encodedPath("/oauth2/default/v1/revoke").build(),
            endSessionEndpoint = networkRule.baseUrl.newBuilder().encodedPath("/oauth2/default/v1/logout").build(),
        )
        return OidcClient(configuration, endpoints)
    }

    @Test fun testStart(): Unit = runBlocking {
        networkRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
            body("username=foo&password=bar&client_id=unit_test_client_id&grant_type=password&scope=openid%20email%20profile%20offline_access")
        ) { response ->
            response.testBodyFromFile("$mockPrefix/token.json")
        }
        val resourceOwnerFlow = createOidcClient().resourceOwnerFlow()
        val result = resourceOwnerFlow.start("foo", "bar")
        val tokens = (result as ResourceOwnerFlow.Result.Tokens).tokens
        assertThat(tokens.tokenType).isEqualTo("Bearer")
        assertThat(tokens.expiresIn).isEqualTo(3600)
        assertThat(tokens.accessToken).isEqualTo("exampleAccessToken")
        assertThat(tokens.scope).isEqualTo("offline_access profile openid email")
        assertThat(tokens.refreshToken).isEqualTo("exampleRefreshToken")
        assertThat(tokens.idToken).isEqualTo("exampleIdToken")
    }

    @Test fun testStartFailure(): Unit = runBlocking {
        networkRule.enqueue(
            path("/oauth2/default/v1/token"),
        ) { response ->
            response.setResponseCode(503)
        }
        val resourceOwnerFlow = createOidcClient().resourceOwnerFlow()
        val result = resourceOwnerFlow.start("foo", "bar")
        assertThat(result).isInstanceOf(ResourceOwnerFlow.Result.Error::class.java)
        val errorResult = result as ResourceOwnerFlow.Result.Error
        assertThat(errorResult.exception).isInstanceOf(IOException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("Request failed.")
        assertThat(errorResult.message).isEqualTo("Token request failed.")
    }
}
