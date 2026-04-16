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

import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * JVM-based AES-256-GCM token encryption handler for KMP apps.
 *
 * Encrypts and decrypts token data using AES-256 in Galois Counter Mode (GCM).
 * The encryption key is generated on first use and stored at `~/.okta/.encryption_key` (Base64 encoded).
 * A fresh 12-byte IV is generated for each encryption and stored in `encryptionExtras["iv"]`.
 *
 * **Note:** The default key storage writes a Base64-encoded key to disk with restrictive file
 * permissions. This is suitable as an SDK default but may not meet enterprise security requirements.
 * For production deployments, supply a custom [keyProvider] backed by a Java KeyStore (JKS/PKCS12),
 * a hardware security module (HSM), or a cloud key management service (AWS KMS, Azure Key Vault,
 * GCP Cloud KMS, HashiCorp Vault).
 *
 * @param keyProvider optional lambda to provide a custom [SecretKey]. Defaults to loading/generating the standard key.
 */
class JceTokenEncryptionHandler(
    private val keyProvider: () -> SecretKey = ::defaultKeyProvider,
) : TokenEncryptionHandler {
    companion object {
        private const val ALGORITHM = "AES"
        private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_SIZE = 256
        private const val IV_SIZE = 12
        private const val GCM_TAG_LENGTH = 128
        private const val IV_KEY = "iv"

        private val ENCRYPTION_KEY_PATH =
            "${System.getProperty("user.home")}${File.separator}.okta${File.separator}.encryption_key"

        /**
         * Generates a new 256-bit AES key and stores it at the default location.
         */
        private fun generateAndStoreKey(): SecretKey {
            val keyGen = KeyGenerator.getInstance(ALGORITHM)
            keyGen.init(KEY_SIZE, SecureRandom())
            val key = keyGen.generateKey()
            storeKey(key)
            return key
        }

        /**
         * Stores an encryption key in Base64 format at the default location.
         */
        private fun storeKey(key: SecretKey) {
            val keyFile = File(ENCRYPTION_KEY_PATH)
            keyFile.parentFile?.mkdirs()
            val encodedKey = Base64.getEncoder().encodeToString(key.encoded)
            keyFile.writeText(encodedKey)
            // Set restrictive file permissions (read/write owner only)
            keyFile.setReadable(false, false)
            keyFile.setReadable(true, true)
            keyFile.setWritable(false, false)
            keyFile.setWritable(true, true)
            keyFile.setExecutable(false)
        }

        /**
         * Loads the encryption key from the default location, or generates a new one if not found.
         */
        private fun defaultKeyProvider(): SecretKey {
            val keyFile = File(ENCRYPTION_KEY_PATH)
            return if (keyFile.exists()) {
                val encodedKey = keyFile.readText()
                val decodedKey = Base64.getDecoder().decode(encodedKey)
                SecretKeySpec(decodedKey, 0, decodedKey.size, ALGORITHM)
            } else {
                generateAndStoreKey()
            }
        }
    }

    override suspend fun encrypt(plaintext: ByteArray): EncryptionResult {
        val key = keyProvider()
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
        val ciphertext = cipher.doFinal(plaintext)
        val encodedIv = Base64.getEncoder().encodeToString(iv)
        return EncryptionResult(
            ciphertext = ciphertext,
            encryptionExtras = mapOf(IV_KEY to encodedIv)
        )
    }

    override suspend fun decrypt(
        ciphertext: ByteArray,
        encryptionExtras: Map<String, String>,
    ): ByteArray {
        val key = keyProvider()
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val encodedIv =
            encryptionExtras[IV_KEY]
                ?: throw IllegalArgumentException("Missing IV in encryptionExtras")
        val iv = Base64.getDecoder().decode(encodedIv)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
        return cipher.doFinal(ciphertext)
    }
}
