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

/**
 * JVM-based AES-256-GCM token encryption handler for KMP apps.
 *
 * Encrypts and decrypts token data using AES-256 in Galois Counter Mode (GCM).
 * The encryption key is generated on first use and stored in a PKCS12 keystore at `~/.okta/okta.p12`.
 * A fresh 12-byte IV is generated for each encryption and stored in `encryptionExtras["iv"]`.
 *
 * **Note:** The default key storage uses a fixed keystore password, so security relies on file-system
 * permissions. This is suitable as an SDK default but may not meet enterprise security requirements.
 * For production deployments, supply a custom [keyProvider] backed by a hardware security module
 * (HSM), or a cloud key management service (AWS KMS, Azure Key Vault, GCP Cloud KMS, HashiCorp Vault).
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

        private val KEYSTORE_PATH =
            "${System.getProperty("user.home")}${File.separator}.okta${File.separator}okta.p12"
        private val KEYSTORE_PASSWORD =
            (System.getProperty("okta.keystore.password") ?: "okta-sdk-default").toCharArray()
        private const val KEY_ALIAS = "token-encryption-key"
        private const val KEYSTORE_TYPE = "PKCS12"

        /**
         * Loads the encryption key from the PKCS12 keystore, or generates and stores a new one.
         *
         * Synchronized to prevent a TOCTOU race where two concurrent callers both find the
         * keystore missing, generate different keys, and the second write overwrites the first.
         */
        @Synchronized
        private fun defaultKeyProvider(): SecretKey {
            val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
            val keystoreFile = File(KEYSTORE_PATH)
            val protection = KeyStore.PasswordProtection(KEYSTORE_PASSWORD)
            if (keystoreFile.exists()) {
                keystoreFile.inputStream().use { keyStore.load(it, KEYSTORE_PASSWORD) }
                (keyStore.getEntry(KEY_ALIAS, protection) as? KeyStore.SecretKeyEntry)
                    ?.let { return it.secretKey }
            } else {
                keyStore.load(null, KEYSTORE_PASSWORD)
                keystoreFile.parentFile?.mkdirs()
            }
            val key = KeyGenerator.getInstance(ALGORITHM).also { it.init(KEY_SIZE, SecureRandom()) }.generateKey()
            keyStore.setEntry(KEY_ALIAS, KeyStore.SecretKeyEntry(key), protection)
            keystoreFile.outputStream().use { keyStore.store(it, KEYSTORE_PASSWORD) }
            return key
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
