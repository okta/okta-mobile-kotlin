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
package com.okta.authfoundation.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CryptoProviderTest {
    @Test
    fun secureRandomBytes_ReturnsCorrectSize() {
        val bytes = secureRandomBytes(32)
        assertEquals(32, bytes.size)
    }

    @Test
    fun secureRandomBytes_ReturnsDifferentValues() {
        val first = secureRandomBytes(32)
        val second = secureRandomBytes(32)
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun secureRandomBytes_ZeroSize_ReturnsEmptyArray() {
        val bytes = secureRandomBytes(0)
        assertEquals(0, bytes.size)
    }

    @Test
    fun sha256Digest_ReturnsCorrectLength() {
        val digest = sha256Digest("test".toByteArray())
        assertEquals(32, digest.size)
    }

    @Test
    fun sha256Digest_ProducesConsistentResults() {
        val first = sha256Digest("hello".toByteArray())
        val second = sha256Digest("hello".toByteArray())
        assertEquals(first.toList(), second.toList())
    }

    @Test
    fun sha256Digest_DifferentInputs_ProduceDifferentDigests() {
        val first = sha256Digest("hello".toByteArray())
        val second = sha256Digest("world".toByteArray())
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun sha256Digest_KnownValue() {
        // SHA-256 of empty string = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        val digest = sha256Digest(ByteArray(0))
        val hex = digest.joinToString("") { "%02x".format(it) }
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hex)
    }

    @Test
    fun secureRandomBytes_LargeSize() {
        val bytes = secureRandomBytes(1024)
        assertEquals(1024, bytes.size)
        // Verify not all zeros (statistically impossible for secure random)
        assertTrue(bytes.any { it != 0.toByte() })
    }
}
