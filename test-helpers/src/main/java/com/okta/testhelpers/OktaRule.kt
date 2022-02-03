/*
 * Copyright 2021-Present Okta, Inc.
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
package com.okta.testhelpers

import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.client.OidcEndpoints
import com.okta.authfoundation.events.EventCoordinator
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class OktaRule : TestRule {
    private val mockWebServer: OktaMockWebServer = OktaMockWebServer()

    val okHttpClient: OkHttpClient = mockWebServer.okHttpClient
    val baseUrl: HttpUrl = mockWebServer.baseUrl

    val eventHandler = RecordingEventHandler()

    val configuration: OidcConfiguration = OidcConfiguration(
        clientId = "unit_test_client_id",
        defaultScopes = setOf("openid", "email", "profile", "offline_access"),
        signInRedirectUri = "unitTest:/login",
        signOutRedirectUri = "unitTest:/logout",
        okHttpCallFactory = okHttpClient,
        eventCoordinator = EventCoordinator(eventHandler)
    )

    fun createOidcClient(): OidcClient {
        val endpoints = OidcEndpoints(
            issuer = baseUrl.newBuilder().encodedPath("/oauth2/default").build(),
            authorizationEndpoint = baseUrl.newBuilder().encodedPath("/oauth2/default/v1/authorize").build(),
            tokenEndpoint = baseUrl.newBuilder().encodedPath("/oauth2/default/v1/token").build(),
            userInfoEndpoint = baseUrl.newBuilder().encodedPath("/oauth2/default/v1/userinfo").build(),
            jwksUri = baseUrl.newBuilder().encodedPath("/oauth2/default/v1/keys").build(),
            registrationEndpoint = baseUrl.newBuilder().encodedPath("/oauth2/v1/clients").build(),
            introspectionEndpoint = baseUrl.newBuilder().encodedPath("/oauth2/default/v1/introspect").build(),
            revocationEndpoint = baseUrl.newBuilder().encodedPath("/oauth2/default/v1/revoke").build(),
            endSessionEndpoint = baseUrl.newBuilder().encodedPath("/oauth2/default/v1/logout").build(),
        )
        return OidcClient(configuration, endpoints)
    }

    override fun apply(base: Statement, description: Description): Statement {
        return MockWebServerStatement(base, mockWebServer.dispatcher, description)
    }

    fun enqueue(vararg requestMatcher: RequestMatcher, responseFactory: (MockResponse) -> Unit) {
        mockWebServer.dispatcher.enqueue(*requestMatcher) { response ->
            responseFactory(response)
        }
    }
}
