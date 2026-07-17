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

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.api.http.ApiResponse
import com.okta.authfoundation.client.internal.OAuth2Endpoints
import com.okta.authfoundation.client.kmp.DefaultIdTokenValidator
import com.okta.authfoundation.client.kmp.IdTokenValidator
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.authfoundation.jwt.Jwt
import com.okta.authfoundation.util.CoalescingOrchestrator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(InternalAuthFoundationApi::class)
class OAuth2ClientSharedConfigTest {
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

    private var requestCount = 0

    private fun countingApiExecutor(responseBody: String): ApiExecutor =
        object : ApiExecutor {
            override suspend fun execute(request: ApiRequest): Result<ApiResponse> {
                requestCount++
                return Result.success(
                    object : ApiResponse {
                        override val statusCode: Int = 200
                        override val body: ByteArray = responseBody.toByteArray()
                        override val headers: Map<String, List<String>> = emptyMap()
                        override val contentLength: Long = responseBody.length.toLong()
                        override val contentType: String = "application/json"
                    }
                )
            }
        }

    @Test
    fun twoClients_withSharedApiExecutor_functionIndependently() =
        runTest {
            requestCount = 0
            val sharedExecutor =
                countingApiExecutor(
                    """
                    {
                        "token_type": "Bearer",
                        "expires_in": 3600,
                        "access_token": "token",
                        "scope": "openid",
                        "refresh_token": null,
                        "id_token": null
                    }
                    """.trimIndent()
                )

            val client1 =
                OAuth2ClientBuilder
                    .create(
                        issuerUrl = "https://example1.okta.com/oauth2/default",
                        clientId = "client-1",
                        scope = listOf("openid")
                    ) {
                        apiExecutor = sharedExecutor
                    }.getOrThrow()

            val client2 =
                OAuth2ClientBuilder
                    .create(
                        issuerUrl = "https://example2.okta.com/oauth2/default",
                        clientId = "client-2",
                        scope = listOf("openid", "profile")
                    ) {
                        apiExecutor = sharedExecutor
                    }.getOrThrow()

            assertNotSame(client1, client2)
            assertEquals("client-1", client1.configuration.clientId)
            assertEquals("client-2", client2.configuration.clientId)
        }

    @Test
    fun twoClients_withSharedClock_usesSameClock() {
        val fixedClock = OidcClock { 99999L }

        val client1 =
            OAuth2ClientBuilder
                .create(
                    issuerUrl = "https://example1.okta.com/oauth2/default",
                    clientId = "client-1",
                    scope = listOf("openid")
                ) {
                    clock = fixedClock
                }.getOrThrow()

        val client2 =
            OAuth2ClientBuilder
                .create(
                    issuerUrl = "https://example2.okta.com/oauth2/default",
                    clientId = "client-2",
                    scope = listOf("openid")
                ) {
                    clock = fixedClock
                }.getOrThrow()

        assertEquals(99999L, client1.configuration.clock.currentTimeEpochSecond())
        assertEquals(99999L, client2.configuration.clock.currentTimeEpochSecond())
    }

    @Test
    fun twoClients_withSharedExecutor_bothPerformOperations() =
        runTest {
            requestCount = 0
            val sharedExecutor = countingApiExecutor("")

            val config1 =
                OAuth2ClientBuilder
                    .create(
                        issuerUrl = "https://example1.okta.com/oauth2/default",
                        clientId = "client-1",
                        scope = listOf("openid")
                    ) {
                        apiExecutor = sharedExecutor
                    }.getOrThrow()
                    .configuration

            val config2 =
                OAuth2ClientBuilder
                    .create(
                        issuerUrl = "https://example2.okta.com/oauth2/default",
                        clientId = "client-2",
                        scope = listOf("openid")
                    ) {
                        apiExecutor = sharedExecutor
                    }.getOrThrow()
                    .configuration

            val orchestratorFactory: () -> CoalescingOrchestrator<OAuth2ClientResult<OAuth2Endpoints>> = {
                CoalescingOrchestrator(
                    factory = { OAuth2ClientResult.Success(testEndpoints) },
                    keepDataInMemory = { true }
                )
            }

            val commonClient1 = OAuth2Client(config1, orchestratorFactory())
            val commonClient2 = OAuth2Client(config2, orchestratorFactory())

            val result1 = commonClient1.revokeToken("token-1")
            val result2 = commonClient2.revokeToken("token-2")

            assertTrue(result1.isSuccess)
            assertTrue(result2.isSuccess)
            assertEquals(2, requestCount)
        }

    @Test
    fun builder_defaultIdTokenValidator_IsDefaultIdTokenValidator() {
        val client =
            OAuth2ClientBuilder
                .create(
                    issuerUrl = "https://example.okta.com/oauth2/default",
                    clientId = "client-1",
                    scope = listOf("openid")
                ).getOrThrow()

        assertIs<DefaultIdTokenValidator>(client.configuration.idTokenValidator)
    }

    @Test
    fun builder_customIdTokenValidator_IsUsed() {
        val custom =
            object : IdTokenValidator {
                override suspend fun validate(
                    issuerUrl: String,
                    clientId: String,
                    idToken: Jwt,
                    clock: OidcClock,
                    parameters: IdTokenValidator.Parameters,
                ) {
                    // no-op
                }
            }

        val client =
            OAuth2ClientBuilder
                .create(
                    issuerUrl = "https://example.okta.com/oauth2/default",
                    clientId = "client-1",
                    scope = listOf("openid")
                ) {
                    idTokenValidator = custom
                }.getOrThrow()

        assertSame(custom, client.configuration.idTokenValidator)
    }
}
