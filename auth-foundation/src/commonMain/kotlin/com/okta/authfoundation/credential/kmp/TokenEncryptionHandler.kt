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

/**
 * Platform-agnostic interface for encrypting and decrypting token data.
 *
 * Implementations may use platform-specific mechanisms such as Android KeyStore,
 * JVM key management, or custom encryption schemes. The [EncryptionResult.encryptionExtras]
 * map allows implementations to persist platform-specific metadata (IVs, key aliases, etc.)
 * alongside the ciphertext.
 */
interface TokenEncryptionHandler {
    /**
     * Encrypts the given [plaintext] bytes.
     *
     * @param plaintext the raw bytes to encrypt.
     * @return a [EncryptionResult] containing the ciphertext and any platform-specific extras.
     */
    suspend fun encrypt(plaintext: ByteArray): EncryptionResult

    /**
     * Decrypts the given [ciphertext] bytes.
     *
     * @param ciphertext the encrypted bytes to decrypt.
     * @param encryptionExtras platform-specific metadata stored during encryption.
     * @return the decrypted plaintext bytes.
     */
    suspend fun decrypt(
        ciphertext: ByteArray,
        encryptionExtras: Map<String, String>,
    ): ByteArray
}
