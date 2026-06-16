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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(InternalAuthFoundationApi::class)
class OAuth2EndpointOverridesTest {
    private val issuerUrl = "https://example.okta.com/oauth2/default"

    // ── allFieldsNonNull ───────────────────────────────────────────────────────

    @Test
    fun allFieldsNonNull_whenNull_returnsFalse() {
        assertFalse(OAuth2Endpoints.allFieldsNonNull(null))
    }

    @Test
    fun allFieldsNonNull_whenPartial_returnsFalse() {
        val partial = OAuth2EndpointOverrides(tokenEndpoint = "https://example.com/token")
        assertFalse(OAuth2Endpoints.allFieldsNonNull(partial))
    }

    @Test
    fun allFieldsNonNull_whenAllSet_returnsTrue() {
        val full = fullOverrides()
        assertTrue(OAuth2Endpoints.allFieldsNonNull(full))
    }

    // ── merge ──────────────────────────────────────────────────────────────────

    @Test
    fun merge_withNull_returnsOriginal() {
        val endpoints = discoveredEndpoints()
        val merged = endpoints.merge(null)
        assertEquals(endpoints.tokenEndpoint, merged.tokenEndpoint)
        assertEquals(endpoints.authorizationEndpoint, merged.authorizationEndpoint)
    }

    @Test
    fun merge_withPartialOverrides_overridesOnlySpecifiedFields() {
        val endpoints = discoveredEndpoints()
        val override = OAuth2EndpointOverrides(tokenEndpoint = "https://proxy.example.com/token")
        val merged = endpoints.merge(override)
        assertEquals("https://proxy.example.com/token", merged.tokenEndpoint)
        // Non-overridden fields keep their discovered values
        assertEquals(endpoints.authorizationEndpoint, merged.authorizationEndpoint)
        assertEquals(endpoints.userInfoEndpoint, merged.userInfoEndpoint)
    }

    @Test
    fun merge_withFullOverrides_replacesAllFields() {
        val endpoints = discoveredEndpoints()
        val overrides = fullOverrides()
        val merged = endpoints.merge(overrides)
        assertEquals("https://override.example.com/authorize", merged.authorizationEndpoint)
        assertEquals("https://override.example.com/token", merged.tokenEndpoint)
        assertEquals("https://override.example.com/userinfo", merged.userInfoEndpoint)
        assertEquals("https://override.example.com/keys", merged.jwksUri)
        assertEquals("https://override.example.com/introspect", merged.introspectionEndpoint)
        assertEquals("https://override.example.com/revoke", merged.revocationEndpoint)
        assertEquals("https://override.example.com/logout", merged.endSessionEndpoint)
        assertEquals("https://override.example.com/device", merged.deviceAuthorizationEndpoint)
    }

    // ── Builder validation ─────────────────────────────────────────────────────

    @Test
    fun endpointOverrides_Null_BehaviorUnchanged() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = issuerUrl,
                clientId = "client-id",
                scope = listOf("openid")
            ) {
                endpointOverrides = null
            }
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow().configuration.endpointOverrides)
    }

    @Test
    fun endpointOverrides_MalformedUrl_ThrowsIllegalArgument() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = issuerUrl,
                clientId = "client-id",
                scope = listOf("openid")
            ) {
                endpointOverrides = OAuth2EndpointOverrides(tokenEndpoint = "not-a-url")
            }
        assertTrue(result.isFailure)
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
        assertTrue(result.exceptionOrNull()!!.message!!.contains("tokenEndpoint"))
    }

    @Test
    fun endpointOverrides_HttpUrl_ThrowsIllegalArgument() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = issuerUrl,
                clientId = "client-id",
                scope = listOf("openid")
            ) {
                endpointOverrides = OAuth2EndpointOverrides(tokenEndpoint = "http://insecure.example.com/token")
            }
        assertTrue(result.isFailure)
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
    }

    @Test
    fun endpointOverrides_ValidPartialOverride_Stored() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = issuerUrl,
                clientId = "client-id",
                scope = listOf("openid")
            ) {
                endpointOverrides = OAuth2EndpointOverrides(tokenEndpoint = "https://proxy.example.com/token")
            }
        assertTrue(result.isSuccess)
        val config = result.getOrThrow().configuration
        assertNotNull(config.endpointOverrides)
        assertEquals("https://proxy.example.com/token", config.endpointOverrides!!.tokenEndpoint)
    }

    // ── Discovery skip ─────────────────────────────────────────────────────────

    @Test
    fun endpointOverrides_AllFieldsSet_DiscoverySkipped() =
        runTest {
            // Use a failing executor — if discovery is attempted, the test will fail
            var discoveryCallCount = 0
            val failingExecutor =
                object : ApiExecutor {
                    override suspend fun execute(request: ApiRequest): Result<ApiResponse> {
                        discoveryCallCount++
                        return Result.failure(IllegalStateException("Discovery should not be called"))
                    }
                }

            val result =
                OAuth2ClientBuilder.create(
                    issuerUrl = issuerUrl,
                    clientId = "client-id",
                    scope = listOf("openid")
                ) {
                    apiExecutor = failingExecutor
                    endpointOverrides = fullOverrides()
                }
            assertTrue(result.isSuccess)

            // Trigger endpoint resolution
            val client = result.getOrThrow()
            val endpoints = client.endpointsOrNull()

            // Discovery was never called because all overrides were provided
            assertEquals(0, discoveryCallCount)
            assertNotNull(endpoints)
            assertEquals("https://override.example.com/token", endpoints.tokenEndpoint)
        }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun discoveredEndpoints(): OAuth2Endpoints =
        OAuth2Endpoints(
            issuer = issuerUrl,
            authorizationEndpoint = "https://example.okta.com/oauth2/default/v1/authorize",
            tokenEndpoint = "https://example.okta.com/oauth2/default/v1/token",
            userInfoEndpoint = "https://example.okta.com/oauth2/default/v1/userinfo",
            jwksUri = "https://example.okta.com/oauth2/default/v1/keys",
            introspectionEndpoint = "https://example.okta.com/oauth2/default/v1/introspect",
            revocationEndpoint = "https://example.okta.com/oauth2/default/v1/revoke",
            endSessionEndpoint = "https://example.okta.com/oauth2/default/v1/logout",
            deviceAuthorizationEndpoint = null
        )

    private fun fullOverrides(): OAuth2EndpointOverrides =
        OAuth2EndpointOverrides(
            authorizationEndpoint = "https://override.example.com/authorize",
            tokenEndpoint = "https://override.example.com/token",
            userInfoEndpoint = "https://override.example.com/userinfo",
            jwksUri = "https://override.example.com/keys",
            introspectionEndpoint = "https://override.example.com/introspect",
            revocationEndpoint = "https://override.example.com/revoke",
            endSessionEndpoint = "https://override.example.com/logout",
            deviceAuthorizationEndpoint = "https://override.example.com/device"
        )
}
