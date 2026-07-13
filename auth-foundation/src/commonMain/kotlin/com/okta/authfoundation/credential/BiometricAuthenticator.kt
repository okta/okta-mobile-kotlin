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

import com.okta.authfoundation.BiometricAuthenticationException

/**
 * Platform-agnostic contract for performing biometric authentication.
 *
 * Implementations MUST NOT block the calling thread; all authentication UI/callback handling MUST be
 * encapsulated within the suspend functions below. Neither function throws for expected
 * failure/error/cancellation outcomes — both report failure via a returned [Result] wrapping a
 * [BiometricAuthenticationException].
 *
 * This interface intentionally accepts and returns only common/primitive types (`ByteArray`, `String`,
 * `Unit`, `kotlin.Result`)
 */
interface BiometricAuthenticator {
    /**
     * Performs a biometric authentication challenge with no associated cryptographic operation.
     *
     * @return [Result.success] with [Unit] if authentication succeeds; [Result.failure] wrapping a
     * [BiometricAuthenticationException] if the challenge fails, errors, or is canceled.
     */
    suspend fun unlock(): Result<Unit>

    /**
     * Performs a biometric authentication challenge bound to the key identified by [keyAlias], then
     * uses that key to decrypt [encryptedData] and returns the result.
     *
     * Use this overload for ciphers that do not require an initialization vector (e.g. RSA/OAEP). For
     * ciphers that require one (e.g. AES/GCM), use the [decrypt] overload that accepts an `iv`.
     *
     * @param encryptedData the bytes to decrypt after successful authentication.
     * @param keyAlias identifies the previously provisioned key to use for decryption (mirrors
     * `Credential.Security.keyAlias`). The implementation resolves any platform-specific cipher/key
     * material for this alias internally — no `Cipher` or key object is passed by the caller.
     * @return [Result.success] with the decrypted `ByteArray` if authentication and decryption succeed;
     * [Result.failure] wrapping a [BiometricAuthenticationException] if the challenge fails, errors, or
     * is canceled.
     */
    suspend fun decrypt(
        encryptedData: ByteArray,
        keyAlias: String,
    ): Result<ByteArray>

    /**
     * Performs a biometric authentication challenge bound to the key identified by [keyAlias], then
     * uses that key and [iv] to decrypt [encryptedData] and returns the result.
     *
     * This is a distinct abstract overload — not a defaulted/nullable `iv` parameter on the overload
     * above — so that ciphers requiring an initialization vector (e.g. AES/GCM) are supported without
     * relying on Kotlin default parameter values on an interface method, which are not visible to Java
     * callers as separate overloads without compiling with `-Xjvm-default`.
     *
     * @param encryptedData the bytes to decrypt after successful authentication.
     * @param keyAlias identifies the previously provisioned key to use for decryption (mirrors
     * `Credential.Security.keyAlias`).
     * @param iv the initialization vector produced at encryption time for [encryptedData].
     * @return [Result.success] with the decrypted `ByteArray` if authentication and decryption succeed;
     * [Result.failure] wrapping a [BiometricAuthenticationException] if the challenge fails, errors, or
     * is cancelled.
     */
    suspend fun decrypt(
        encryptedData: ByteArray,
        keyAlias: String,
        iv: ByteArray,
    ): Result<ByteArray>
}
