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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OAuth2ClientBuilderTest {
    @Test
    fun create_withValidParameters_succeeds() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "https://example.okta.com/oauth2/default",
                clientId = "test-client-id",
                scope = listOf("openid", "profile")
            )

        assertTrue(result.isSuccess)
        val client = result.getOrThrow()
        assertEquals("test-client-id", client.configuration.clientId)
        assertEquals("openid profile", client.configuration.defaultScope)
        assertEquals(
            "https://example.okta.com/oauth2/default",
            client.configuration.issuerUrl
        )
    }

    @Test
    fun create_withCustomConfig_appliesCustomization() {
        val customClock = OidcClock { 12345L }
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "https://example.okta.com/oauth2/default",
                clientId = "test-client-id",
                scope = listOf("openid")
            ) {
                clock = customClock
                authorizationServerId = "custom-as"
            }

        assertTrue(result.isSuccess)
        val client = result.getOrThrow()
        assertEquals(12345L, client.configuration.clock.currentTimeEpochSecond())
        assertEquals("custom-as", client.configuration.authorizationServerId)
    }

    @Test
    fun create_withHttpIssuer_fails() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "http://example.okta.com/oauth2/default",
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
    fun create_withBlankClientId_fails() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "https://example.okta.com/oauth2/default",
                clientId = "  ",
                scope = listOf("openid")
            )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun create_withBlankScope_fails() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "https://example.okta.com/oauth2/default",
                clientId = "test-client-id",
                scope = emptyList()
            )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun create_discoveryUrl_trimsTrailingSlash() {
        val result =
            OAuth2ClientBuilder.create(
                issuerUrl = "https://example.okta.com/oauth2/default/",
                clientId = "test-client-id",
                scope = listOf("openid")
            )

        assertTrue(result.isSuccess)
        assertEquals(
            "https://example.okta.com/oauth2/default",
            result.getOrThrow().configuration.issuerUrl
        )
    }
}
