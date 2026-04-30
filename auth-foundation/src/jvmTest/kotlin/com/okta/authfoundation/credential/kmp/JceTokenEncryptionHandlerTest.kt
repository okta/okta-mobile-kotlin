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
package com.okta.authfoundation.credential.kmp

import kotlinx.coroutines.test.runTest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JceTokenEncryptionHandlerTest {
    @Test
    fun encrypt_Roundtrip_RecoverPlaintext() =
        runTest {
            val handler = JceTokenEncryptionHandler()
            val plaintext = "secret-access-token".encodeToByteArray()

            val encrypted = handler.encrypt(plaintext)
            val decrypted = handler.decrypt(encrypted.ciphertext, encrypted.encryptionExtras)

            assertContentEquals(plaintext, decrypted)
        }

    @Test
    fun encrypt_CiphertextDiffersFromPlaintext() =
        runTest {
            val handler = JceTokenEncryptionHandler()
            val plaintext = "secret-access-token".encodeToByteArray()

            val encrypted = handler.encrypt(plaintext)

            // Ciphertext should be different from plaintext
            assertFalse(plaintext.contentEquals(encrypted.ciphertext))
        }

    @Test
    fun encrypt_DifferentPlaintexts_DifferentCiphertexts() =
        runTest {
            val handler = JceTokenEncryptionHandler()
            val plaintext1 = "token-1".encodeToByteArray()
            val plaintext2 = "token-2".encodeToByteArray()

            val encrypted1 = handler.encrypt(plaintext1)
            val encrypted2 = handler.encrypt(plaintext2)

            // Different plaintexts should produce different ciphertexts
            assertFalse(encrypted1.ciphertext.contentEquals(encrypted2.ciphertext))
        }

    @Test
    fun encrypt_DifferentIVs_ProducedForEachCall() =
        runTest {
            val handler = JceTokenEncryptionHandler()
            val plaintext = "same-token".encodeToByteArray()

            val encrypted1 = handler.encrypt(plaintext)
            val encrypted2 = handler.encrypt(plaintext)

            // Same plaintext with different IVs should produce different ciphertexts
            assertFalse(encrypted1.ciphertext.contentEquals(encrypted2.ciphertext))

            val iv1 = encrypted1.encryptionExtras["iv"]
            val iv2 = encrypted2.encryptionExtras["iv"]
            assertNotNull(iv1)
            assertNotNull(iv2)
            assertNotEquals(iv1, iv2)
        }

    @Test
    fun encrypt_EncryptionExtras_ContainsIv() =
        runTest {
            val handler = JceTokenEncryptionHandler()
            val plaintext = "test-token".encodeToByteArray()

            val encrypted = handler.encrypt(plaintext)

            assertNotNull(encrypted.encryptionExtras["iv"])
            val ivBase64 = encrypted.encryptionExtras["iv"]!!
            // IV should be valid Base64
            val decoded = Base64.getDecoder().decode(ivBase64)
            assertEquals(12, decoded.size) // 12-byte IV for GCM
        }

    @Test
    fun decrypt_WrongKey_Fails() =
        runTest {
            // Generate a key and encrypt
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            val key1 = keyGen.generateKey()

            val handler1 = JceTokenEncryptionHandler { key1 }
            val plaintext = "secret-data".encodeToByteArray()
            val encrypted = handler1.encrypt(plaintext)

            // Try to decrypt with a different key
            val key2 = keyGen.generateKey()
            val handler2 = JceTokenEncryptionHandler { key2 }

            var decryptFailed = false
            try {
                handler2.decrypt(encrypted.ciphertext, encrypted.encryptionExtras)
            } catch (e: Exception) {
                decryptFailed = true
            }
            assertTrue(decryptFailed)
        }

    @Test
    fun decrypt_MissingIv_Fails() =
        runTest {
            val handler = JceTokenEncryptionHandler()
            val plaintext = "test".encodeToByteArray()
            val encrypted = handler.encrypt(plaintext)

            var decryptFailed = false
            try {
                // Try to decrypt without the IV
                handler.decrypt(encrypted.ciphertext, emptyMap())
            } catch (e: IllegalArgumentException) {
                decryptFailed = true
            }
            assertTrue(decryptFailed)
        }

    @Test
    fun customKeyProvider_UsesSuppliedKey() =
        runTest {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            val customKey = keyGen.generateKey()

            val handler = JceTokenEncryptionHandler { customKey }
            val plaintext = "custom-key-data".encodeToByteArray()

            val encrypted = handler.encrypt(plaintext)
            val decrypted = handler.decrypt(encrypted.ciphertext, encrypted.encryptionExtras)

            assertContentEquals(plaintext, decrypted)
        }

    @Test
    fun multipleHandlers_SameKey_CanDecryptEachOther() =
        runTest {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            val sharedKey = keyGen.generateKey()

            val handler1 = JceTokenEncryptionHandler { sharedKey }
            val handler2 = JceTokenEncryptionHandler { sharedKey }

            val plaintext = "shared-secret".encodeToByteArray()
            val encrypted = handler1.encrypt(plaintext)
            val decrypted = handler2.decrypt(encrypted.ciphertext, encrypted.encryptionExtras)

            assertContentEquals(plaintext, decrypted)
        }

    @Test
    fun decrypt_TamperedCiphertext_Fails() =
        runTest {
            val handler = JceTokenEncryptionHandler()
            val plaintext = "original".encodeToByteArray()
            val encrypted = handler.encrypt(plaintext)

            // Tamper with the ciphertext
            val tamperedCiphertext = encrypted.ciphertext.copyOf()
            if (tamperedCiphertext.isNotEmpty()) {
                tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0xFF).toByte()
            }

            var decryptFailed = false
            try {
                handler.decrypt(tamperedCiphertext, encrypted.encryptionExtras)
            } catch (e: Exception) {
                decryptFailed = true
            }
            assertTrue(decryptFailed)
        }
}
