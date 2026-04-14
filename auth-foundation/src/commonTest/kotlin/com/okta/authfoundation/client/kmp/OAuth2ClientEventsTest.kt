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
package com.okta.authfoundation.client.kmp

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.api.http.ApiResponse
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.events.TokenRefreshedEvent
import com.okta.authfoundation.client.events.TokenRevokedEvent
import com.okta.authfoundation.client.internal.OAuth2Endpoints
import com.okta.authfoundation.events.Event
import com.okta.authfoundation.util.CoalescingOrchestrator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(InternalAuthFoundationApi::class, ExperimentalCoroutinesApi::class)
class OAuth2ClientEventsTest {
    private val testEndpoints =
        OAuth2Endpoints(
            issuer = "https://example.okta.com/oauth2/default",
            authorizationEndpoint = "https://example.okta.com/oauth2/default/v1/authorize",
            tokenEndpoint = "https://example.okta.com/oauth2/default/v1/token",
            userInfoEndpoint = "https://example.okta.com/oauth2/default/v1/userinfo",
            jwksUri = "https://example.okta.com/oauth2/default/v1/keys",
            introspectionEndpoint = "https://example.okta.com/oauth2/default/v1/introspect",
            revocationEndpoint = "https://example.okta.com/oauth2/default/v1/revoke",
            endSessionEndpoint = "https://example.okta.com/oauth2/default/v1/logout",
            deviceAuthorizationEndpoint = null
        )

    private fun mockApiExecutor(responseBody: String): ApiExecutor =
        object : ApiExecutor {
            override suspend fun execute(request: ApiRequest): Result<ApiResponse> =
                Result.success(
                    object : ApiResponse {
                        override val statusCode: Int = 200
                        override val body: ByteArray = responseBody.toByteArray()
                        override val headers: Map<String, List<String>> = emptyMap()
                        override val contentLength: Long = responseBody.length.toLong()
                        override val contentType: String = "application/json"
                    }
                )
        }

    private fun createClient(apiExecutor: ApiExecutor): OAuth2Client {
        val config =
            OAuth2ClientBuilder
                .create(
                    issuerUrl = "https://example.okta.com/oauth2/default",
                    clientId = "test-client-id",
                    scope = listOf("openid", "profile")
                ) {
                    this.apiExecutor = apiExecutor
                }.getOrThrow()
                .configuration

        return OAuth2Client(
            configuration = config,
            endpointsOrchestrator =
                CoalescingOrchestrator(
                    factory = { OAuth2ClientResult.Success(testEndpoints) },
                    keepDataInMemory = { true }
                )
        )
    }

    @Test
    fun refreshToken_EmitsTokenRefreshedEvent() =
        runTest {
            val recordedEvents = mutableListOf<Event>()
            val tokenJson =
                """
                {
                    "token_type": "Bearer",
                    "expires_in": 3600,
                    "access_token": "new-access-token",
                    "scope": "openid profile",
                    "refresh_token": null,
                    "id_token": null
                }
                """.trimIndent()

            val client = createClient(mockApiExecutor(tokenJson))
            val collectJob =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    client.events.collect { recordedEvents.add(it) }
                }

            val result = client.refreshToken("old-refresh-token")

            assertTrue(result.isSuccess)
            assertEquals(1, recordedEvents.size)
            val event = assertIs<TokenRefreshedEvent>(recordedEvents[0])
            assertEquals("new-access-token", event.tokenInfo.accessToken)
            collectJob.cancel()
        }

    @Test
    fun revokeToken_EmitsTokenRevokedEvent() =
        runTest {
            val recordedEvents = mutableListOf<Event>()
            val client = createClient(mockApiExecutor(""))
            val collectJob =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    client.events.collect { recordedEvents.add(it) }
                }

            val result = client.revokeToken("some-token")

            assertTrue(result.isSuccess)
            assertEquals(1, recordedEvents.size)
            val event = assertIs<TokenRevokedEvent>(recordedEvents[0])
            assertEquals("some-token", event.token)
            collectJob.cancel()
        }

    @Test
    fun refreshToken_OnFailure_DoesNotEmitEvent() =
        runTest {
            val recordedEvents = mutableListOf<Event>()
            val failingExecutor =
                object : ApiExecutor {
                    override suspend fun execute(request: ApiRequest): Result<ApiResponse> = Result.failure(RuntimeException("Network error"))
                }

            val client = createClient(failingExecutor)
            val collectJob =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    client.events.collect { recordedEvents.add(it) }
                }

            val result = client.refreshToken("old-refresh-token")

            assertTrue(result.isFailure)
            assertEquals(0, recordedEvents.size)
            collectJob.cancel()
        }
}
