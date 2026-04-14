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
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.authfoundation.util.CoalescingOrchestrator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(InternalAuthFoundationApi::class)
class CommonOAuth2ClientTest {
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

    private fun createClient(
        apiExecutor: ApiExecutor,
        endpoints: OAuth2Endpoints = testEndpoints,
    ): OAuth2Client {
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
                    factory = { OAuth2ClientResult.Success(endpoints) },
                    keepDataInMemory = { true }
                )
        )
    }

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

    @Test
    fun refreshToken_returnsTokenInfo() =
        runTest {
            val tokenJson =
                """
                {
                    "token_type": "Bearer",
                    "expires_in": 3600,
                    "access_token": "new-access-token",
                    "scope": "openid profile",
                    "refresh_token": "new-refresh-token",
                    "id_token": null
                }
                """.trimIndent()

            val client = createClient(mockApiExecutor(tokenJson))
            val result = client.refreshToken("old-refresh-token")

            assertTrue(result is OAuth2ClientResult.Success)
            val tokenInfo = result.result
            assertEquals("Bearer", tokenInfo.tokenType)
            assertEquals(3600, tokenInfo.expiresIn)
            assertEquals("new-access-token", tokenInfo.accessToken)
            assertEquals("openid profile", tokenInfo.scope)
            assertEquals("new-refresh-token", tokenInfo.refreshToken)
        }

    @Test
    fun revokeToken_succeeds() =
        runTest {
            val client = createClient(mockApiExecutor(""))
            val result = client.revokeToken("some-token")

            assertTrue(result is OAuth2ClientResult.Success)
        }

    @Test
    fun introspectToken_returnsJsonObject() =
        runTest {
            val introspectJson =
                """
                {
                    "active": true,
                    "scope": "openid profile",
                    "client_id": "test-client-id"
                }
                """.trimIndent()

            val client = createClient(mockApiExecutor(introspectJson))
            val result = client.introspectToken("access_token", "some-token")

            assertTrue(result is OAuth2ClientResult.Success)
            val json = result.result
            assertTrue(json["active"]!!.jsonPrimitive.boolean)
            assertEquals("openid profile", json["scope"]!!.jsonPrimitive.content)
        }

    @Test
    fun getUserInfo_returnsOidcUserInfo() =
        runTest {
            val userInfoJson =
                """
                {
                    "sub": "user123",
                    "name": "Test User",
                    "email": "test@example.com"
                }
                """.trimIndent()

            val client = createClient(mockApiExecutor(userInfoJson))
            val result = client.getUserInfo("access-token")

            assertTrue(result is OAuth2ClientResult.Success)
            val userInfo = result.result
            assertNotNull(userInfo)
        }

    @Test
    fun jwks_returnsJwks() =
        runTest {
            val jwksJson =
                """
                {
                    "keys": [
                        {
                            "kty": "RSA",
                            "alg": "RS256",
                            "kid": "key1",
                            "use": "sig",
                            "n": "test-modulus",
                            "e": "AQAB"
                        }
                    ]
                }
                """.trimIndent()

            val client = createClient(mockApiExecutor(jwksJson))
            val result = client.jwks()

            assertTrue(result is OAuth2ClientResult.Success)
            val jwks = result.result
            assertEquals(1, jwks.keys.size)
            assertEquals("key1", jwks.keys[0].keyId)
        }

    @Test
    fun operations_withNoEndpoints_returnError() =
        runTest {
            val config =
                OAuth2ClientBuilder
                    .create(
                        issuerUrl = "https://example.okta.com/oauth2/default",
                        clientId = "test-client-id",
                        scope = listOf("openid")
                    ) {
                        apiExecutor = mockApiExecutor("")
                    }.getOrThrow()
                    .configuration

            val client =
                OAuth2Client(
                    configuration = config,
                    endpointsOrchestrator =
                        CoalescingOrchestrator(
                            factory = {
                                OAuth2ClientResult.Error(
                                    OAuth2ClientResult.Error.OidcEndpointsNotAvailableException()
                                )
                            },
                            keepDataInMemory = { false }
                        )
                )

            val result = client.refreshToken("token")
            assertTrue(result is OAuth2ClientResult.Error)
        }

    @Test
    fun createFromConfiguration_UsesConfigFromOriginalClient() {
        val originalClient =
            OAuth2ClientBuilder
                .create(
                    issuerUrl = "https://example.okta.com/oauth2/default",
                    clientId = "test-client-id",
                    scope = listOf("openid", "profile")
                ).getOrThrow()

        val recreated = OAuth2Client.createFromConfiguration(originalClient.configuration)

        assertSame(originalClient.configuration, recreated.configuration)
        assertEquals("test-client-id", recreated.configuration.clientId)
    }
}
