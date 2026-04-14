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
package com.okta.authfoundation.credential

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class TokenDataKmpTest {
    @Test
    fun create_FieldsAssignedCorrectly() {
        val config = TestConfiguration.create(clientId = "my-client", issuerUrl = "https://issuer.example.com")
        val token = createTestToken(id = "t1", accessToken = "at1", refreshToken = "rt1", configuration = config)

        assertEquals("t1", token.id)
        assertEquals("at1", token.accessToken)
        assertEquals("rt1", token.refreshToken)
        assertEquals("Bearer", token.tokenType)
        assertEquals(3600, token.expiresIn)
        assertEquals("openid profile", token.scope)
        assertNull(token.idToken)
        assertNull(token.deviceSecret)
        assertNull(token.issuedTokenType)
    }

    @Test
    fun create_DeriveClientIdFromConfiguration() {
        val config = TestConfiguration.create(clientId = "derived-client")
        val token = createTestToken(configuration = config)

        assertEquals("derived-client", token.clientId)
    }

    @Test
    fun create_DeriveIssuerUrlFromConfiguration() {
        val config = TestConfiguration.create(issuerUrl = "https://my-issuer.com")
        val token = createTestToken(configuration = config)

        assertEquals("https://my-issuer.com", token.issuerUrl)
    }

    @Test
    fun create_IssuedAtComputedFromClock() {
        val config = TestConfiguration.create(clockTimeSeconds = 5000L)
        val token = createTestToken(expiresIn = 1000, configuration = config)

        assertEquals(5000L - 1000, token.issuedAt)
    }

    @Test
    fun copy_PreservesFieldsByDefault() {
        val original = createTestToken(id = "orig")
        val copied = original.copy()

        assertEquals(original.id, copied.id)
        assertEquals(original.accessToken, copied.accessToken)
        assertEquals(original.refreshToken, copied.refreshToken)
        assertEquals(original.deviceSecret, copied.deviceSecret)
    }

    @Test
    fun copy_OverridesRefreshToken() {
        val original = createTestToken(refreshToken = "old-rt")
        val copied = original.copy(refreshToken = "new-rt")

        assertEquals("new-rt", copied.refreshToken)
        assertEquals(original.accessToken, copied.accessToken)
    }

    @Test
    fun copy_OverridesDeviceSecret() {
        val original = createTestToken()
        val copied = original.copy(deviceSecret = "device-secret")

        assertEquals("device-secret", copied.deviceSecret)
    }

    @Test
    fun copy_OverridesId() {
        val original = createTestToken(id = "orig")
        val copied = original.copy(id = "new-id")

        assertEquals("new-id", copied.id)
    }

    @Test
    fun equals_SameTokenFields_Equal() {
        val config = TestConfiguration.create()
        val a = createTestToken(id = "a", accessToken = "at", refreshToken = "rt", configuration = config)
        val b = createTestToken(id = "b", accessToken = "at", refreshToken = "rt", configuration = config)

        assertEquals(a, b)
    }

    @Test
    fun equals_DifferentAccessToken_NotEqual() {
        val config = TestConfiguration.create()
        val a = createTestToken(accessToken = "at1", refreshToken = "rt", configuration = config)
        val b = createTestToken(accessToken = "at2", refreshToken = "rt", configuration = config)

        assertNotEquals(a, b)
    }

    @Test
    fun equals_DifferentRefreshToken_NotEqual() {
        val config = TestConfiguration.create()
        val a = createTestToken(refreshToken = "rt1", configuration = config)
        val b = createTestToken(refreshToken = "rt2", configuration = config)

        assertNotEquals(a, b)
    }

    @Test
    fun hashCode_SameTokenFields_SameHash() {
        val config = TestConfiguration.create()
        val a = createTestToken(accessToken = "same", refreshToken = "rt", configuration = config)
        val b = createTestToken(accessToken = "same", refreshToken = "rt", configuration = config)

        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun metadata_WithNullPayload_ClaimsProviderIsNull() {
        val metadata = TokenMetadata(id = "m1", tags = mapOf("env" to "test"), payloadData = null)

        assertEquals("m1", metadata.id)
        assertEquals("test", metadata.tags["env"])
        assertNull(metadata.claimsProvider)
    }

    @Test
    fun metadata_Tags_ReturnedCorrectly() {
        val tags = mapOf("label" to "primary", "org" to "okta")
        val metadata = TokenMetadata(id = "m2", tags = tags, payloadData = null)

        assertEquals(2, metadata.tags.size)
        assertEquals("primary", metadata.tags["label"])
        assertEquals("okta", metadata.tags["org"])
    }
}
