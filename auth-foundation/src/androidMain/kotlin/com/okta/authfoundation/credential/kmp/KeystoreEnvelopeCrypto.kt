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

import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Shared RSA-envelope/AES-GCM primitives used by both the legacy Android `DefaultTokenEncryptionHandler`
 * and [AndroidTokenEncryptionHandler]. A fresh AES-256 key encrypts the plaintext; that AES key (plus its
 * GCM IV) is itself wrapped with an Android Keystore RSA key, so decryption always requires access to the
 * Keystore-resident private key (and, for biometric-gated keys, a successful biometric challenge).
 */
internal object KeystoreEnvelopeCrypto {
    internal const val ENCRYPTED_AES_KEY_MATERIAL = "ENCRYPTED_AES_KEY_MATERIAL"
    internal const val BASE64_SEPARATOR = ","
    internal const val GCM_TAG_LENGTH_BITS = 128

    internal fun getAesCipher(): Cipher =
        Cipher.getInstance(
            KeyProperties.KEY_ALGORITHM_AES + "/" +
                KeyProperties.BLOCK_MODE_GCM + "/" +
                KeyProperties.ENCRYPTION_PADDING_NONE
        )

    internal fun getRsaCipher(): Cipher =
        Cipher.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA + "/" +
                KeyProperties.BLOCK_MODE_ECB + "/" +
                KeyProperties.ENCRYPTION_PADDING_RSA_OAEP
        )

    /** Result of [encrypt]: the AES-GCM ciphertext, plus the RSA-wrapped AES key material as extras. */
    internal class Envelope(
        val ciphertext: ByteArray,
        val extras: Map<String, String>,
    )

    /**
     * Encrypts [plaintext] with a fresh AES-256-GCM key, then wraps that key (and its IV) with
     * [publicRsaKey] using RSA-OAEP so only the matching Keystore private key can unwrap it.
     */
    internal fun encrypt(
        plaintext: ByteArray,
        publicRsaKey: PublicKey,
    ): Envelope {
        val aesKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val aesCipher = getAesCipher().apply { init(Cipher.ENCRYPT_MODE, aesKey) }
        val ciphertext = aesCipher.doFinal(plaintext)

        val aesKeyMaterial =
            (
                Base64.encodeToString(aesKey.encoded, Base64.NO_WRAP) +
                    BASE64_SEPARATOR +
                    Base64.encodeToString(aesCipher.iv, Base64.NO_WRAP)
            ).toByteArray()
        val rsaCipher = getRsaCipher().apply { init(Cipher.ENCRYPT_MODE, publicRsaKey) }
        val encryptedAesKeyMaterial = rsaCipher.doFinal(aesKeyMaterial)

        return Envelope(
            ciphertext,
            mapOf(ENCRYPTED_AES_KEY_MATERIAL to Base64.encodeToString(encryptedAesKeyMaterial, Base64.NO_WRAP))
        )
    }

    /**
     * Decrypts [ciphertext] previously produced by [encrypt]. [unwrapAesKeyMaterial] is handed the
     * RSA-encrypted AES key material extracted from [encryptionExtras] and must return it decrypted —
     * callers choose how the RSA unwrap happens (plain Keystore private key, or biometric-gated).
     */
    internal suspend fun decrypt(
        ciphertext: ByteArray,
        encryptionExtras: Map<String, String>,
        unwrapAesKeyMaterial: suspend (encryptedAesKeyMaterial: ByteArray) -> ByteArray,
    ): ByteArray {
        val encryptedAesKeyMaterial = Base64.decode(encryptionExtras[ENCRYPTED_AES_KEY_MATERIAL], Base64.NO_WRAP)
        val aesKeyMaterial = unwrapAesKeyMaterial(encryptedAesKeyMaterial)
        val parts = aesKeyMaterial.decodeToString().split(BASE64_SEPARATOR, limit = 2)
        val aesKey = SecretKeySpec(Base64.decode(parts[0], Base64.NO_WRAP), "AES")
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)

        val aesCipher =
            getAesCipher().apply {
                init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            }
        return aesCipher.doFinal(ciphertext)
    }
}
