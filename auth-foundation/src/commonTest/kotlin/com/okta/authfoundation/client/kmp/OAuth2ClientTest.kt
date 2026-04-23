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
import com.okta.authfoundation.client.dto.IntrospectInfo
import com.okta.authfoundation.client.internal.OAuth2Endpoints
import com.okta.authfoundation.util.CoalescingOrchestrator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(InternalAuthFoundationApi::class)
class OAuth2ClientTest {
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

    private val testEndpointsWithDeviceAuth =
        OAuth2Endpoints(
            issuer = "https://example.okta.com/oauth2/default",
            authorizationEndpoint = "https://example.okta.com/oauth2/default/v1/authorize",
            tokenEndpoint = "https://example.okta.com/oauth2/default/v1/token",
            userInfoEndpoint = "https://example.okta.com/oauth2/default/v1/userinfo",
            jwksUri = "https://example.okta.com/oauth2/default/v1/keys",
            introspectionEndpoint = "https://example.okta.com/oauth2/default/v1/introspect",
            revocationEndpoint = "https://example.okta.com/oauth2/default/v1/revoke",
            endSessionEndpoint = "https://example.okta.com/oauth2/default/v1/logout",
            deviceAuthorizationEndpoint = "https://example.okta.com/oauth2/default/v1/device/authorize"
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

            assertTrue(result.isSuccess)
            val tokenInfo = result.getOrThrow()
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

            assertTrue(result.isSuccess)
        }

    @Test
    fun introspectToken_returnsIntrospectInfo() =
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

            assertTrue(result.isSuccess)
            val info = result.getOrThrow()
            assertIs<IntrospectInfo.Active>(info)
            assertEquals(true, info.active)
            val scope = info.deserializeClaim("scope", String.serializer())
            assertEquals("openid profile", scope)
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
            val userInfo = client.getUserInfo("access-token").getOrThrow()
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
            val jwks = client.jwks().getOrThrow()
            assertEquals(1, jwks.keys.size)
            assertEquals("key1", jwks.keys[0].keyId)
        }

    @Test
    fun tokenRequest_ReturnsValidatedTokenInfo() =
        runTest {
            val tokenJson =
                """
                {
                    "token_type": "Bearer",
                    "expires_in": 3600,
                    "access_token": "tr-access-token",
                    "scope": "openid profile",
                    "refresh_token": "tr-refresh-token",
                    "id_token": null
                }
                """.trimIndent()

            val client = createClient(mockApiExecutor(tokenJson))
            val formParams =
                mapOf(
                    "client_id" to "test-client-id",
                    "grant_type" to "authorization_code",
                    "code" to "auth-code-123",
                    "redirect_uri" to "https://example.com/callback"
                )
            val result = client.tokenRequest(formParams)

            assertTrue(result.isSuccess)
            val tokenInfo = result.getOrThrow()
            assertEquals("Bearer", tokenInfo.tokenType)
            assertEquals(3600, tokenInfo.expiresIn)
            assertEquals("tr-access-token", tokenInfo.accessToken)
            assertEquals("openid profile", tokenInfo.scope)
            assertEquals("tr-refresh-token", tokenInfo.refreshToken)
        }

    @Test
    fun tokenRequest_WithNoEndpoints_ReturnsError() =
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

            val result = client.tokenRequest(mapOf("grant_type" to "authorization_code"))
            assertTrue(result.isFailure)
            assertIs<OAuth2ClientResult.Error.OidcEndpointsNotAvailableException>(result.exceptionOrNull())
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
            assertTrue(result.isFailure)
        }

    @Test
    fun deviceAuthorizationRequest_ReturnsDeviceAuthorizationInfo() =
        runTest {
            val deviceAuthJson =
                """
                {
                    "device_code": "1a521d9f-0922-4e6d-8db9-8b654297435a",
                    "user_code": "GDLMZQCT",
                    "verification_uri": "https://example.okta.com/activate",
                    "verification_uri_complete": "https://example.okta.com/activate?user_code=GDLMZQCT",
                    "expires_in": 600,
                    "interval": 10
                }
                """.trimIndent()

            val client = createClient(mockApiExecutor(deviceAuthJson), testEndpointsWithDeviceAuth)
            val result =
                client.deviceAuthorizationRequest(
                    mapOf("client_id" to "test-client-id", "scope" to "openid")
                )

            assertTrue(result.isSuccess)
            val info = result.getOrThrow()
            assertEquals("1a521d9f-0922-4e6d-8db9-8b654297435a", info.deviceCode)
            assertEquals("GDLMZQCT", info.userCode)
            assertEquals("https://example.okta.com/activate", info.verificationUri)
            assertEquals("https://example.okta.com/activate?user_code=GDLMZQCT", info.verificationUriComplete)
            assertEquals(600, info.expiresIn)
            assertEquals(10, info.interval)
        }

    @Test
    fun deviceAuthorizationRequest_WithMinimalJson_ReturnsDeviceAuthorizationInfo() =
        runTest {
            val deviceAuthJson =
                """
                {
                    "device_code": "abc123",
                    "user_code": "WXYZ",
                    "verification_uri": "https://example.okta.com/activate",
                    "expires_in": 300
                }
                """.trimIndent()

            val client = createClient(mockApiExecutor(deviceAuthJson), testEndpointsWithDeviceAuth)
            val result =
                client.deviceAuthorizationRequest(
                    mapOf("client_id" to "test-client-id", "scope" to "openid")
                )

            assertTrue(result.isSuccess)
            val info = result.getOrThrow()
            assertEquals("abc123", info.deviceCode)
            assertEquals("WXYZ", info.userCode)
            assertEquals(null, info.verificationUriComplete)
            assertEquals(5, info.interval)
            assertEquals(300, info.expiresIn)
        }

    @Test
    fun deviceAuthorizationRequest_WithNoDeviceAuthorizationEndpoint_ReturnsError() =
        runTest {
            val client = createClient(mockApiExecutor(""), testEndpoints)
            val result =
                client.deviceAuthorizationRequest(
                    mapOf("client_id" to "test-client-id", "scope" to "openid")
                )

            assertTrue(result.isFailure)
            assertIs<OAuth2ClientResult.Error.OidcEndpointsNotAvailableException>(result.exceptionOrNull())
        }

    @Test
    fun deviceAuthorizationRequest_WithNoEndpoints_ReturnsError() =
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

            val result = client.deviceAuthorizationRequest(mapOf("client_id" to "test-client-id"))
            assertTrue(result.isFailure)
            assertIs<OAuth2ClientResult.Error.OidcEndpointsNotAvailableException>(result.exceptionOrNull())
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
