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

import com.okta.authfoundation.AuthFoundationDefaults
import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.client.AccessTokenValidator
import com.okta.authfoundation.client.Cache
import com.okta.authfoundation.client.DeviceSecretValidator
import com.okta.authfoundation.client.IdTokenValidator
import com.okta.authfoundation.client.NoOpCache
import com.okta.authfoundation.client.OAuth2Client
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.client.OidcEndpoints
import com.okta.authfoundation.events.EventCoordinator
import io.mockk.every
import io.mockk.mockkObject
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(InternalAuthFoundationApi::class)
class OktaRule(
    private val idTokenValidator: IdTokenValidator = IdTokenValidator { _, _, _ -> },
    private val accessTokenValidator: AccessTokenValidator = AccessTokenValidator { _, _, _ -> },
    private val deviceSecretValidator: DeviceSecretValidator = DeviceSecretValidator { _, _, _ -> },
) : TestRule {
    private val mockWebServer: OktaMockWebServer = OktaMockWebServer()

    val mockWebServerDispatcher: NetworkDispatcher = mockWebServer.dispatcher

    val okHttpClient: OkHttpClient = mockWebServer.okHttpClient
    val baseUrl: HttpUrl = mockWebServer.baseUrl

    val eventHandler: RecordingEventHandler = RecordingEventHandler()
    val clock: TestClock = TestClock()

    var configuration: OidcConfiguration = createConfiguration()
        set(value) {
            mockkObject(OidcConfiguration)
            every { OidcConfiguration.default } returns value
            field = value
        }

    var client: OAuth2Client = createOAuth2Client()
        set(value) {
            mockkObject(OAuth2Client)
            every { OAuth2Client.default } returns value
            field = value
        }

    init {
        mockkObject(OidcConfiguration)
        mockkObject(OAuth2Client)
        every { OidcConfiguration.default } returns configuration
        every { OAuth2Client.default } returns client
    }

    fun createConfiguration(
        okHttpClient: OkHttpClient = this.okHttpClient,
        cache: Cache = NoOpCache(),
    ): OidcConfiguration {
        mockkObject(AuthFoundationDefaults)
        every { AuthFoundationDefaults.okHttpClientFactory } returns { okHttpClient }
        every { AuthFoundationDefaults.eventCoordinator } returns EventCoordinator(eventHandler)
        every { AuthFoundationDefaults.clock } returns clock
        every { AuthFoundationDefaults.idTokenValidator } returns idTokenValidator
        every { AuthFoundationDefaults.accessTokenValidator } returns accessTokenValidator
        every { AuthFoundationDefaults.deviceSecretValidator } returns deviceSecretValidator
        every { AuthFoundationDefaults.ioDispatcher } returns EmptyCoroutineContext
        every { AuthFoundationDefaults.computeDispatcher } returns EmptyCoroutineContext
        every { AuthFoundationDefaults.cacheFactory } returns { cache }
        every { AuthFoundationDefaults.cookieJar } returns CookieJar.NO_COOKIES

        return OidcConfiguration(
            clientId = "unit_test_client_id",
            defaultScope = "openid email profile offline_access",
            issuer = baseUrl.toString().removeSuffix("/")
        )
    }

    fun createEndpoints(
        urlBuilder: HttpUrl.Builder = baseUrl.newBuilder(),
        includeJwks: Boolean = false,
        includeIssuerPath: Boolean = true,
    ): OidcEndpoints {
        val issuer = if (includeIssuerPath) {
            urlBuilder.encodedPath("/oauth2/default").build()
        } else {
            urlBuilder.build()
        }
        return OidcEndpoints(
            issuer = issuer,
            authorizationEndpoint = urlBuilder.encodedPath("/oauth2/default/v1/authorize").build(),
            tokenEndpoint = urlBuilder.encodedPath("/oauth2/default/v1/token").build(),
            userInfoEndpoint = urlBuilder.encodedPath("/oauth2/default/v1/userinfo").build(),
            jwksUri = if (includeJwks) urlBuilder.encodedPath("/oauth2/default/v1/keys").build() else null,
            introspectionEndpoint = urlBuilder.encodedPath("/oauth2/default/v1/introspect").build(),
            revocationEndpoint = urlBuilder.encodedPath("/oauth2/default/v1/revoke").build(),
            endSessionEndpoint = urlBuilder.encodedPath("/oauth2/default/v1/logout").build(),
            deviceAuthorizationEndpoint = urlBuilder.encodedPath("/oauth2/default/v1/device/authorize").build(),
        )
    }

    fun createOAuth2Client(endpoints: OidcEndpoints = createEndpoints()): OAuth2Client {
        return OAuth2Client.create(configuration, endpoints)
    }

    override fun apply(base: Statement, description: Description): Statement {
        return MockWebServerStatement(base, mockWebServer.dispatcher, description)
    }

    fun enqueue(vararg requestMatcher: RequestMatcher, responseFactory: (MockResponse) -> Unit) {
        mockWebServer.dispatcher.enqueue(*requestMatcher) { response ->
            responseFactory(response)
        }
    }

    fun enqueue(vararg requestMatcher: RequestMatcher, responseFactory: (OktaRecordedRequest, MockResponse) -> Unit) {
        mockWebServer.dispatcher.enqueue(*requestMatcher) { request, response ->
            responseFactory(request, response)
        }
    }
}
