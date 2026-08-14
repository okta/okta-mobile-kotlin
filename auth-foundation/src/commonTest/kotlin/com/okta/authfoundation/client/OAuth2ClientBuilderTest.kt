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

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OAuth2ClientBuilderTest {
    @Test
    fun create_WithOrgAuthServer_UsesBaseUrlAsIssuer() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "https://example.okta.com",
                clientId = "test-client-id",
                scope = listOf("openid", "profile")
            )

        assertTrue(result.isSuccess)
        val client = result.getOrThrow()
        assertEquals("test-client-id", client.configuration.clientId)
        assertEquals(listOf("openid", "profile"), client.configuration.defaultScope)
        assertEquals("https://example.okta.com", client.configuration.issuerUrl)
        assertNull(client.configuration.authorizationServerId)
    }

    @Test
    fun create_WithAuthorizationServerId_BuildsCustomAuthServerIssuerUrl() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "https://example.okta.com",
                clientId = "test-client-id",
                scope = listOf("openid", "profile")
            ) {
                authorizationServerId = "default"
            }

        assertTrue(result.isSuccess)
        val client = result.getOrThrow()
        assertEquals("https://example.okta.com/oauth2/default", client.configuration.issuerUrl)
        assertEquals("default", client.configuration.authorizationServerId)
    }

    @Test
    fun create_WithCustomAuthorizationServerId_BuildsCorrectIssuerUrl() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "https://example.okta.com",
                clientId = "test-client-id",
                scope = listOf("openid")
            ) {
                authorizationServerId = "my-custom-server"
            }

        assertTrue(result.isSuccess)
        assertEquals(
            "https://example.okta.com/oauth2/my-custom-server",
            result.getOrThrow().configuration.issuerUrl
        )
    }

    @Test
    fun create_WithTrailingSlashOnBaseUrl_TrimsBeforeAppendingAuthServerId() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "https://example.okta.com/",
                clientId = "test-client-id",
                scope = listOf("openid")
            ) {
                authorizationServerId = "default"
            }

        assertTrue(result.isSuccess)
        assertEquals(
            "https://example.okta.com/oauth2/default",
            result.getOrThrow().configuration.issuerUrl
        )
    }

    @Test
    fun create_WithTrailingSlashAndNoAuthServerId_StripsToBaseUrl() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "https://example.okta.com/",
                clientId = "test-client-id",
                scope = listOf("openid")
            )

        assertTrue(result.isSuccess)
        assertEquals("https://example.okta.com", result.getOrThrow().configuration.issuerUrl)
    }

    @Test
    fun create_WithCustomConfig_AppliesCustomization() {
        val customClock = OidcClock { 12345L }
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "https://example.okta.com",
                clientId = "test-client-id",
                scope = listOf("openid")
            ) {
                clock = customClock
                authorizationServerId = "custom-as"
                enablePushedAuthorizationRequests = false
                allowPushedAuthorizationRequestFallback = false
            }

        assertTrue(result.isSuccess)
        val client = result.getOrThrow()
        assertEquals(12345L, client.configuration.clock.currentTimeEpochSecond())
        assertEquals("custom-as", client.configuration.authorizationServerId)
        assertEquals("https://example.okta.com/oauth2/custom-as", client.configuration.issuerUrl)
        assertEquals(false, client.configuration.enablePushedAuthorizationRequests)
        assertEquals(false, client.configuration.allowPushedAuthorizationRequestFallback)
    }

    @Test
    fun create_WithClientSecret_StoresClientSecret() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "https://example.okta.com",
                clientId = "test-client-id",
                scope = listOf("openid")
            ) {
                clientSecret = "test-client-secret"
            }

        assertTrue(result.isSuccess)
        assertEquals("test-client-secret", result.getOrThrow().configuration.clientSecret)
    }

    @Test
    fun create_WithClientAssertionProvider_StoresClientAssertionProvider() {
        val provider =
            ClientAssertionProvider { audience ->
                ClientAssertion(type = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer", assertion = "jwt-for-$audience")
            }
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "https://example.okta.com",
                clientId = "test-client-id",
                scope = listOf("openid")
            ) {
                clientAssertionProvider = provider
            }

        assertTrue(result.isSuccess)
        val config = result.getOrThrow().configuration
        assertEquals(provider, config.clientAssertionProvider)
        val assertion = config.clientAssertionProvider?.provide("https://example.okta.com/v1/token")
        assertEquals("urn:ietf:params:oauth:client-assertion-type:jwt-bearer", assertion?.type)
        assertEquals("jwt-for-https://example.okta.com/v1/token", assertion?.assertion)
    }

    @Test
    fun create_WithClientSecretAndClientAssertionProvider_Fails() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "https://example.okta.com",
                clientId = "test-client-id",
                scope = listOf("openid")
            ) {
                clientSecret = "test-client-secret"
                clientAssertionProvider = ClientAssertionProvider { audience -> ClientAssertion("type", "jwt-for-$audience") }
            }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun create_WithDefaultParConfiguration_IsDisabledWithFallbackDisabled() {
        val client =
            OAuth2ClientBuilder
                .create(
                    issuerUrl = "https://example.okta.com",
                    clientId = "test-client-id",
                    scope = listOf("openid")
                ).getOrThrow()

        assertFalse(client.configuration.enablePushedAuthorizationRequests)
        assertFalse(client.configuration.allowPushedAuthorizationRequestFallback)
    }

    @Test
    fun create_WithFullIssuerUrlAndAuthServerId_StripsPathBeforeAppending() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "https://example.okta.com/oauth2/old-server",
                clientId = "test-client-id",
                scope = listOf("openid")
            ) {
                authorizationServerId = "new-server"
            }

        assertTrue(result.isSuccess)
        assertEquals(
            "https://example.okta.com/oauth2/new-server",
            result.getOrThrow().configuration.issuerUrl
        )
    }

    @Test
    fun create_WithFullIssuerUrlAndNoAuthServerId_StripsToBaseUrl() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "https://example.okta.com/oauth2/default",
                clientId = "test-client-id",
                scope = listOf("openid")
            )

        assertTrue(result.isSuccess)
        assertEquals("https://example.okta.com", result.getOrThrow().configuration.issuerUrl)
    }

    @Test
    fun create_WithNonDefaultPort_PreservesPort() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "https://example.okta.com:8443",
                clientId = "test-client-id",
                scope = listOf("openid")
            ) {
                authorizationServerId = "default"
            }

        assertTrue(result.isSuccess)
        assertEquals(
            "https://example.okta.com:8443/oauth2/default",
            result.getOrThrow().configuration.issuerUrl
        )
    }

    @Test
    fun create_WithHttpIssuer_Fails() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "http://example.okta.com",
                clientId = "test-client-id",
                scope = listOf("openid")
            )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(error.message!!.contains("https"))
    }

    @Test
    fun clientAuthenticationFormParameters_WithClientSecret_ReturnsClientSecret() =
        runTest {
            val config =
                OAuth2ClientConfiguration(
                    clientId = "test-client-id",
                    defaultScope = listOf("openid"),
                    issuerUrl = "https://example.okta.com",
                    apiExecutor = noOpApiExecutor(),
                    clock = OidcClock { 0L },
                    json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
                    cache = NoOpCache(),
                    authorizationServerId = null,
                    clientSecret = "test-client-secret",
                    acrValues = null
                )

            assertEquals(
                mapOf("client_secret" to "test-client-secret"),
                config.clientAuthenticationFormParameters("https://example.okta.com/v1/token")
            )
        }

    @Test
    fun clientAuthenticationFormParameters_WithClientAssertionProvider_InvokesProviderWithAudience() =
        runTest {
            var receivedAudience: String? = null
            val config =
                OAuth2ClientConfiguration(
                    clientId = "test-client-id",
                    defaultScope = listOf("openid"),
                    issuerUrl = "https://example.okta.com",
                    apiExecutor = noOpApiExecutor(),
                    clock = OidcClock { 0L },
                    json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
                    cache = NoOpCache(),
                    authorizationServerId = null,
                    clientSecret = "",
                    clientAssertionProvider =
                        ClientAssertionProvider { audience ->
                            receivedAudience = audience
                            ClientAssertion(
                                type = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                                assertion = "test-signed-jwt"
                            )
                        },
                    acrValues = null
                )

            assertEquals(
                mapOf(
                    "client_assertion_type" to "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                    "client_assertion" to "test-signed-jwt"
                ),
                config.clientAuthenticationFormParameters("https://example.okta.com/v1/token")
            )
            assertEquals("https://example.okta.com/v1/token", receivedAudience)
        }

    @Test
    fun clientAuthenticationFormParameters_WithNoClientAuth_ReturnsEmptyMap() =
        runTest {
            val config =
                OAuth2ClientConfiguration(
                    clientId = "test-client-id",
                    defaultScope = listOf("openid"),
                    issuerUrl = "https://example.okta.com",
                    apiExecutor = noOpApiExecutor(),
                    clock = OidcClock { 0L },
                    json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
                    cache = NoOpCache(),
                    authorizationServerId = null,
                    clientSecret = "",
                    acrValues = null
                )

            assertTrue(config.clientAuthenticationFormParameters("https://example.okta.com/v1/token").isEmpty())
        }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun clientAuthenticationFormParameters_WithClientAssertionProvider_InvokesOnComputeDispatcher() =
        runTest {
            val computeDispatcher = newSingleThreadContext("test-compute-dispatcher")
            try {
                var observedThreadName: String? = null
                val config =
                    OAuth2ClientConfiguration(
                        clientId = "test-client-id",
                        defaultScope = listOf("openid"),
                        issuerUrl = "https://example.okta.com",
                        apiExecutor = noOpApiExecutor(),
                        clock = OidcClock { 0L },
                        json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
                        cache = NoOpCache(),
                        authorizationServerId = null,
                        clientSecret = "",
                        clientAssertionProvider =
                            ClientAssertionProvider { _ ->
                                observedThreadName = Thread.currentThread().name
                                ClientAssertion(type = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer", assertion = "jwt")
                            },
                        acrValues = null,
                        computeDispatcher = computeDispatcher
                    )

                config.clientAuthenticationFormParameters("https://example.okta.com/v1/token")

                assertEquals("test-compute-dispatcher", observedThreadName)
            } finally {
                computeDispatcher.close()
            }
        }

    @Test
    fun clientAuthenticationFormParameters_WithClientAssertionProvider_InvokedFreshPerCallWithDistinctAudiences() =
        runTest {
            val receivedAudiences = mutableListOf<String>()
            val config =
                OAuth2ClientConfiguration(
                    clientId = "test-client-id",
                    defaultScope = listOf("openid"),
                    issuerUrl = "https://example.okta.com",
                    apiExecutor = noOpApiExecutor(),
                    clock = OidcClock { 0L },
                    json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
                    cache = NoOpCache(),
                    authorizationServerId = null,
                    clientSecret = "",
                    clientAssertionProvider =
                        ClientAssertionProvider { audience ->
                            receivedAudiences.add(audience)
                            ClientAssertion(type = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer", assertion = "jwt-${receivedAudiences.size}")
                        },
                    acrValues = null
                )

            val parParams = config.clientAuthenticationFormParameters("https://example.okta.com/v1/par")
            val tokenParams = config.clientAuthenticationFormParameters("https://example.okta.com/v1/token")

            assertEquals(listOf("https://example.okta.com/v1/par", "https://example.okta.com/v1/token"), receivedAudiences)
            assertEquals("jwt-1", parParams["client_assertion"])
            assertEquals("jwt-2", tokenParams["client_assertion"])
        }

    private fun noOpApiExecutor(): com.okta.authfoundation.api.http.ApiExecutor =
        object : com.okta.authfoundation.api.http.ApiExecutor {
            override suspend fun execute(request: com.okta.authfoundation.api.http.ApiRequest): Result<com.okta.authfoundation.api.http.ApiResponse> = error("Not used in this test")
        }

    @Test
    fun create_WithBlankClientId_Fails() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "https://example.okta.com",
                clientId = "  ",
                scope = listOf("openid")
            )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun create_WithEmptyScope_Fails() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "https://example.okta.com",
                clientId = "test-client-id",
                scope = emptyList()
            )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
