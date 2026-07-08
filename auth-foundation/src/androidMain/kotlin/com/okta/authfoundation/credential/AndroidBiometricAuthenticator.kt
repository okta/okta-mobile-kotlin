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

import androidx.biometric.BiometricPrompt.PromptInfo
import com.okta.authfoundation.BiometricDecryptionActivity
import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.util.runCatchingCancellable
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Android implementation of [BiometricAuthenticator], backed by [androidx.biometric.BiometricPrompt] via
 * [BiometricDecryptionActivity].
 *
 * This class owns all `javax.crypto`/`java.security` interaction internally — callers of
 * [BiometricAuthenticator] never construct or pass a `Cipher`.
 *
 * @param promptInfo the [PromptInfo] to display for each biometric challenge.
 * @param keyStore the [KeyStore] used to look up private keys by alias.
 */
@InternalAuthFoundationApi
class AndroidBiometricAuthenticator(
    private val promptInfo: PromptInfo,
    private val keyStore: KeyStore,
) : BiometricAuthenticator {
    override suspend fun unlock(): Result<Unit> = BiometricDecryptionActivity.biometricUnlock(promptInfo)

    override suspend fun decrypt(
        encryptedData: ByteArray,
        keyAlias: String,
    ): Result<ByteArray> =
        runCatchingCancellable { rsaCipher(keyAlias) }
            .fold(
                onSuccess = { cipher -> BiometricDecryptionActivity.biometricDecrypt(cipher, encryptedData, promptInfo) },
                onFailure = { Result.failure(it) }
            )

    override suspend fun decrypt(
        encryptedData: ByteArray,
        keyAlias: String,
        iv: ByteArray,
    ): Result<ByteArray> =
        runCatchingCancellable { aesCipher(keyAlias, iv) }
            .fold(
                onSuccess = { cipher -> BiometricDecryptionActivity.biometricDecrypt(cipher, encryptedData, promptInfo) },
                onFailure = { Result.failure(it) }
            )

    private fun rsaCipher(keyAlias: String): Cipher {
        val privateKey =
            keyStore.getKey(keyAlias, null)
                ?: throw InvalidKeyException("No key found for alias: $keyAlias")
        return DefaultTokenEncryptionHandler.getRsaCipher().apply { init(Cipher.DECRYPT_MODE, privateKey) }
    }

    private fun aesCipher(
        keyAlias: String,
        iv: ByteArray,
    ): Cipher {
        val secretKey =
            keyStore.getKey(keyAlias, null)
                ?: throw InvalidKeyException("No key found for alias: $keyAlias")
        return DefaultTokenEncryptionHandler.getAesCipher().apply {
            init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(DefaultTokenEncryptionHandler.GCM_TAG_LENGTH_BITS, iv))
        }
    }
}
