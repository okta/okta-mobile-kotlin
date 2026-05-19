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
package com.okta.oauth2.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrlUtilsTest {
    @Test
    fun parseQueryParameter_BasicParam() {
        val result = parseQueryParameter("https://example.com?code=abc123", "code")
        assertEquals("abc123", result)
    }

    @Test
    fun parseQueryParameter_MultipleParams() {
        val result = parseQueryParameter("https://example.com?state=xyz&code=abc123&scope=openid", "code")
        assertEquals("abc123", result)
    }

    @Test
    fun parseQueryParameter_MissingParam() {
        val result = parseQueryParameter("https://example.com?state=xyz", "code")
        assertNull(result)
    }

    @Test
    fun parseQueryParameter_NoQueryString() {
        val result = parseQueryParameter("https://example.com", "code")
        assertNull(result)
    }

    @Test
    fun parseQueryParameter_EncodedValue() {
        val result = parseQueryParameter("https://example.com?error_description=An+error+occurred%21", "error_description")
        assertEquals("An error occurred!", result)
    }

    @Test
    fun parseQueryParameter_EmptyValue() {
        val result = parseQueryParameter("https://example.com?key=", "key")
        assertEquals("", result)
    }

    @Test
    fun parseQueryParameter_WithFragment() {
        val result = parseQueryParameter("https://example.com?code=abc123#fragment", "code")
        assertEquals("abc123", result)
    }

    @Test
    fun parseQueryParameter_FragmentParam_NotReturned() {
        val result = parseQueryParameter("https://example.com?state=xyz#code=abc123", "code")
        assertNull(result)
    }

    @Test
    fun generateUuid_CorrectFormat() {
        val uuid = generateUuid()
        // UUID v4 format: 8-4-4-4-12 hex chars
        val pattern = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        assertTrue(pattern.matches(uuid), "UUID doesn't match v4 format: $uuid")
    }

    @Test
    fun generateUuid_UniquenessAcrossCalls() {
        val first = generateUuid()
        val second = generateUuid()
        assertNotEquals(first, second)
    }

    @Test
    fun generateUuid_CorrectLength() {
        val uuid = generateUuid()
        assertEquals(36, uuid.length)
    }

    @Test
    fun generateUuid_Version4Bits() {
        val uuid = generateUuid()
        // Character at index 14 must be '4' (version)
        assertEquals('4', uuid[14])
        // Character at index 19 must be 8, 9, a, or b (variant)
        assertTrue(uuid[19] in listOf('8', '9', 'a', 'b'), "Variant bits incorrect: ${uuid[19]}")
    }
}
