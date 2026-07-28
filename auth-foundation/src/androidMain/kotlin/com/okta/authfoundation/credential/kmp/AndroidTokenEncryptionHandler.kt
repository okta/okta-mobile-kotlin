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

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.credential.AndroidBiometricAuthenticator
import com.okta.authfoundation.util.AndroidKeystoreUtil
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * Android [TokenEncryptionHandler] backed by the Android Keystore, with optional biometric gating.
 *
 * A single instance encrypts with one fixed policy chosen at construction — either a plain
 * Keystore-resident RSA/AES envelope ([requireBiometric] = false), or the same envelope protected by a
 * biometric-gated key ([requireBiometric] = true). This mirrors the JVM `JceTokenEncryptionHandler`'s
 * "one strategy per instance" model. For per-credential mixed security tiers (some biometric, some not),
 * construct multiple [RoomTokenStorage][com.okta.authfoundation.credential.kmp.storage.RoomTokenStorage]
 * instances with different [keyAlias] values.
 *
 * [promptInfo] must be non-null before a [decrypt] call is attempted when [requireBiometric] is true, or
 * that call fails with [IllegalArgumentException]. If different operations need different prompt text,
 * construct separate [RoomTokenStorage][com.okta.authfoundation.credential.kmp.storage.RoomTokenStorage]
 * instances (see [keyAlias] above) rather than mutating this instance — neither [TokenStorage.getToken] nor
 * [TokenEncryptionHandler.decrypt] take a per-call prompt, so a single instance can only ever show one
 * prompt anyway.
 *
 * @param keyAlias the Android Keystore alias used for this handler's RSA key pair.
 * @param requireBiometric when true, a biometric (or device credential, depending on Keystore capability)
 *   challenge is required to decrypt.
 * @param userAuthenticationTimeout seconds after a successful biometric challenge during which decryption
 *   may proceed without re-prompting; `0` means "auth-per-use" (every decrypt shows the prompt). Ignored
 *   when [requireBiometric] is false.
 * @param promptInfo the [BiometricPrompt.PromptInfo] to display for biometric challenges. Required
 *   (non-null) when [requireBiometric] is true.
 * @param keyStore the [KeyStore] used to store/retrieve the RSA key pair.
 * @param keyPairGenerator the generator used to create the RSA key pair on first use.
 */
@OptIn(InternalAuthFoundationApi::class)
class AndroidTokenEncryptionHandler(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val requireBiometric: Boolean = false,
    private val userAuthenticationTimeout: Int = 5,
    private val promptInfo: BiometricPrompt.PromptInfo? = null,
    internal val keyStore: KeyStore = AndroidKeystoreUtil.keyStore,
    private val keyPairGenerator: KeyPairGenerator = AndroidKeystoreUtil.getRsaKeyPairGenerator(),
) : TokenEncryptionHandler {
    private fun ensureKeyExists() {
        if (keyStore.containsAlias(keyAlias)) return

        val specBuilder =
            KeyGenParameterSpec
                .Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setKeySize(2048)
                .setDigests(
                    KeyProperties.DIGEST_SHA1,
                    KeyProperties.DIGEST_SHA256,
                    KeyProperties.DIGEST_SHA512
                )

        val spec =
            if (!requireBiometric) {
                specBuilder.setUserAuthenticationRequired(false).build()
            } else {
                specBuilder
                    .apply {
                        setUserAuthenticationRequired(true)
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
                            setUserAuthenticationParameters(
                                userAuthenticationTimeout,
                                KeyProperties.AUTH_BIOMETRIC_STRONG
                            )
                        } else {
                            // Setting -1 timeout sets strong biometric encryption in Android 10 and below;
                            // it cannot be set to a custom userAuthenticationTimeout on those versions.
                            setUserAuthenticationValidityDurationSeconds(-1)
                        }
                    }.build()
            }

        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
    }

    override suspend fun encrypt(plaintext: ByteArray): EncryptionResult {
        ensureKeyExists()
        val publicRsaKey =
            keyStore.getCertificate(keyAlias)?.publicKey
                ?: throw KeyPermanentlyInvalidatedException("No public key found for alias $keyAlias")

        // workaround for using public key from
        // https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.html#known-issues
        KeyFactory
            .getInstance(publicRsaKey.algorithm)
            .generatePublic(X509EncodedKeySpec(publicRsaKey.encoded))

        val envelope = KeystoreEnvelopeCrypto.encrypt(plaintext, publicRsaKey)
        return EncryptionResult(envelope.ciphertext, envelope.extras)
    }

    override suspend fun decrypt(
        ciphertext: ByteArray,
        encryptionExtras: Map<String, String>,
    ): ByteArray =
        try {
            if (!requireBiometric) {
                plainDecrypt(ciphertext, encryptionExtras)
            } else {
                try {
                    // The key may still be usable without a fresh challenge (non-zero userAuthenticationTimeout
                    // window still open from a prior call).
                    plainDecrypt(ciphertext, encryptionExtras)
                } catch (ex: KeyPermanentlyInvalidatedException) {
                    throw ex
                } catch (ex: GeneralSecurityException) {
                    biometricDecrypt(ciphertext, encryptionExtras)
                }
            }
        } catch (ex: KeyPermanentlyInvalidatedException) {
            keyStore.deleteEntry(keyAlias)
            throw TokenEncryptionKeyInvalidatedException(keyAlias)
        }

    private suspend fun plainDecrypt(
        ciphertext: ByteArray,
        encryptionExtras: Map<String, String>,
    ): ByteArray {
        val privateRsaKey = keyStore.getKey(keyAlias, null)
        return KeystoreEnvelopeCrypto.decrypt(ciphertext, encryptionExtras) { encryptedAesKeyMaterial ->
            KeystoreEnvelopeCrypto.getRsaCipher().apply { init(Cipher.DECRYPT_MODE, privateRsaKey) }.doFinal(encryptedAesKeyMaterial)
        }
    }

    private suspend fun biometricDecrypt(
        ciphertext: ByteArray,
        encryptionExtras: Map<String, String>,
    ): ByteArray {
        val prompt =
            promptInfo
                ?: throw IllegalArgumentException("promptInfo must be set before decrypting a biometric-protected token.")
        val authenticator = AndroidBiometricAuthenticator(prompt, keyStore)

        return if (userAuthenticationTimeout == 0) { // auth-per-use key
            KeystoreEnvelopeCrypto.decrypt(ciphertext, encryptionExtras) { encryptedAesKeyMaterial ->
                authenticator.decrypt(encryptedAesKeyMaterial, keyAlias).getOrThrow()
            }
        } else {
            authenticator.unlock().getOrThrow()
            plainDecrypt(ciphertext, encryptionExtras)
        }
    }

    companion object {
        const val DEFAULT_KEY_ALIAS = "com.okta.authfoundation.credential.kmp.AndroidTokenEncryptionHandler"
    }
}
