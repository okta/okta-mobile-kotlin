/*
 * Copyright 2024-Present Okta, Inc.
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

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricPrompt
import com.okta.authfoundation.AuthFoundationDefaults
import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.TransparentBiometricActivity
import com.okta.authfoundation.client.ApplicationContextHolder
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.util.AndroidKeystoreUtil
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.Continuation

/**
 * Interface for defining how [Token]s are encrypted before storing in [RoomTokenStorage], the default implementation of [TokenStorage].
 *
 * The default implementation is provided, and handles all [Credential.Security] options.
 * A custom implementation may be useful for more advanced use-cases, and for more fine-grained control in handling biometrics.
 * Custom implementations of [TokenEncryptionHandler] can be passed to [AuthFoundationDefaults.tokenEncryptionHandler].
 */
interface TokenEncryptionHandler {
    /**
     * Generate the key with the specified [Credential.Security]. If a key with [Credential.Security.keyAlias] already exists, do nothing.
     *
     * @param security the [Credential.Security] for creating the key. A key with [Credential.Security.keyAlias] must already exist or be created.
     */
    fun generateKey(security: Credential.Security)

    /**
     * Encrypt the provided [token] with [security], and return the [EncryptionResult].
     *
     * @param token the [Token] to be encrypted.
     * @param security the [Credential.Security] to encrypt the [token] with.
     *
     * @return [EncryptionResult] containing the [EncryptionResult.encryptedToken], and [EncryptionResult.encryptionExtras] containing encryption primitives for decryption (such as IV).
     */
    suspend fun encrypt(
        token: Token,
        security: Credential.Security
    ): EncryptionResult

    /**
     * Decrypt the provided [encryptedToken], and return the [Token].
     *
     * @param encryptedToken the encrypted [Token] that was previously encrypted using [TokenEncryptionHandler.encrypt].
     * @param encryptionExtras the encryption extras previously returned by [TokenEncryptionHandler.encrypt].
     * @param security the [Credential.Security] object holding the required [Credential.Security.keyAlias] for decrypting [encryptedToken].
     * @param promptInfo the [BiometricPrompt.PromptInfo] to be displayed if the stored [Token] is using biometric [Credential.Security].
     *
     * @return [Token] object from decrypting [encryptedToken].
     */
    suspend fun decrypt(
        encryptedToken: ByteArray,
        encryptionExtras: Map<String, String>,
        security: Credential.Security,
        promptInfo: BiometricPrompt.PromptInfo? = Credential.Security.promptInfo
    ): Token

    /**
     * The encryption result when calling [TokenEncryptionHandler.encrypt].
     */
    class EncryptionResult(
        /**
         * Encrypted token as a result of [TokenEncryptionHandler.encrypt].
         */
        val encryptedToken: ByteArray,
        /**
         * Encryption primitives as a result of [TokenEncryptionHandler.encrypt].
         */
        val encryptionExtras: Map<String, String>
    ) {
        operator fun component1(): ByteArray = encryptedToken
        operator fun component2(): Map<String, String> = encryptionExtras
    }
}

@InternalAuthFoundationApi
class DefaultTokenEncryptionHandler(
    internal val keyStore: KeyStore = AndroidKeystoreUtil.keyStore,
    private val keyPairGenerator: KeyPairGenerator = AndroidKeystoreUtil.getRsaKeyPairGenerator()
) : TokenEncryptionHandler {
    private val aesKeyGenerator = KeyGenerator.getInstance("AES").apply {
        // Generate 256-bit AES keys for encryption
        init(256)
    }

    init {
        keyStore.load(null)
    }

    override fun generateKey(security: Credential.Security) {
        if (keyStore.containsAlias(security.keyAlias)) return

        val keyGenParameterSpecBuilder = KeyGenParameterSpec.Builder(
            security.keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setKeySize(2048)
            .setDigests(
                KeyProperties.DIGEST_SHA1,
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA512
            )

        val keyGenParameterSpec = when (security) {
            is Credential.Security.Default -> {
                keyGenParameterSpecBuilder.setUserAuthenticationRequired(false).build()
            }

            is Credential.Security.BiometricStrong -> {
                keyGenParameterSpecBuilder.apply {
                    setUserAuthenticationRequired(true)
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                    } else {
                        // Setting -1 timeout sets strong biometric encryption in Android 10 and below
                        setUserAuthenticationValidityDurationSeconds(-1)
                    }
                }.build()
            }

            is Credential.Security.BiometricStrongOrDeviceCredential -> {
                keyGenParameterSpecBuilder.apply {
                    setUserAuthenticationRequired(true)
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(
                            0,
                            KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                        )
                    } else {
                        // Setting 0 timeout sets strong or device credential encryption in Android 10 and below
                        setUserAuthenticationValidityDurationSeconds(0)
                    }
                }.build()
            }
        }

        keyPairGenerator.initialize(keyGenParameterSpec)
        keyPairGenerator.generateKeyPair()
    }

    override suspend fun encrypt(
        token: Token,
        security: Credential.Security
    ): TokenEncryptionHandler.EncryptionResult {
        val publicRsaKey = keyStore.getCertificate(security.keyAlias).publicKey.let {
            // workaround for using public key from
            // https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.html#known-issues
            KeyFactory.getInstance(it.algorithm)
                .generatePublic(X509EncodedKeySpec(it.encoded))
        }
        val aesKey = aesKeyGenerator.generateKey()
        val encryptionExtras = mutableMapOf<String, String>()

        val serializedToken =
            OidcConfiguration.defaultJson().encodeToString(Token.serializer(), token)
        val aesCipher = getAesCipher().apply { init(Cipher.ENCRYPT_MODE, aesKey) }
        val encryptedToken = aesCipher.doFinal(serializedToken.toByteArray())

        val rsaCipher = getRsaCipher().apply { init(Cipher.ENCRYPT_MODE, publicRsaKey) }
        val aesKeyMaterial = (
            Base64.encodeToString(
                aesKey.encoded,
                Base64.NO_WRAP
            ) + BASE64_SEPARATOR + Base64.encodeToString(aesCipher.iv, Base64.NO_WRAP)
            ).toByteArray()
        val encryptedAesKeyMaterial = rsaCipher.doFinal(aesKeyMaterial)
        encryptionExtras[ENCRYPTED_AES_KEY_MATERIAL] =
            Base64.encodeToString(encryptedAesKeyMaterial, Base64.NO_WRAP)

        return TokenEncryptionHandler.EncryptionResult(encryptedToken, encryptionExtras.toMap())
    }

    override suspend fun decrypt(
        encryptedToken: ByteArray,
        encryptionExtras: Map<String, String>,
        security: Credential.Security,
        promptInfo: BiometricPrompt.PromptInfo?
    ): Token {
        val userAuthenticationRequired = security is Credential.BiometricSecurity
        return if (userAuthenticationRequired) {
            if (promptInfo == null) {
                throw IllegalArgumentException(BIO_TOKEN_NO_PROMPT_INFO_ERROR)
            }
            suspendCancellableCoroutine { continuation ->
                biometricDecryptionContinuation = continuation
                TransparentBiometricActivity.navigate(
                    ApplicationContextHolder.appContext,
                    TransparentBiometricActivity.ActivityParameters(
                        keyStore.provider.name,
                        encryptedToken,
                        encryptionExtras,
                        security.keyAlias
                    ),
                    promptInfo
                )
            }
        } else {
            val privateRsaKey = keyStore.getKey(security.keyAlias, null)
            val rsaCipher = getRsaCipher().apply { init(Cipher.DECRYPT_MODE, privateRsaKey) }
            internalDecrypt(encryptedToken, rsaCipher, encryptionExtras)
        }
    }

    internal companion object {
        internal const val ENCRYPTED_AES_KEY_MATERIAL = "ENCRYPTED_AES_KEY_MATERIAL"
        internal const val BASE64_SEPARATOR = ","
        internal const val BIO_TOKEN_NO_PROMPT_INFO_ERROR =
            "promptInfo is required for decrypting biometric tokens"

        internal var biometricDecryptionContinuation: Continuation<Token>? = null

        internal fun internalDecrypt(
            encryptedToken: ByteArray,
            rsaCipher: Cipher,
            encryptionExtras: Map<String, String>,
        ): Token {
            val encryptedAesKeyMaterial = Base64.decode(encryptionExtras[ENCRYPTED_AES_KEY_MATERIAL], Base64.NO_WRAP)
            val aesKeyMaterial = rsaCipher.doFinal(encryptedAesKeyMaterial).decodeToString().split(
                BASE64_SEPARATOR, limit = 2
            )
            val encodedAesKey = Base64.decode(aesKeyMaterial[0], Base64.NO_WRAP)
            val aesIv = Base64.decode(aesKeyMaterial[1], Base64.NO_WRAP)
            val aesKey = SecretKeySpec(encodedAesKey, "AES") as SecretKey

            val aesCipher =
                getAesCipher().apply {
                    init(
                        Cipher.DECRYPT_MODE,
                        aesKey,
                        GCMParameterSpec(128, aesIv)
                    )
                }
            val decryptedToken = aesCipher.doFinal(encryptedToken)
            val serializedToken = decryptedToken.decodeToString()
            return OidcConfiguration.defaultJson()
                .decodeFromString(Token.serializer(), serializedToken)
        }

        private fun getAesCipher(): Cipher {
            return Cipher.getInstance(
                KeyProperties.KEY_ALGORITHM_AES + "/" +
                    KeyProperties.BLOCK_MODE_GCM + "/" +
                    KeyProperties.ENCRYPTION_PADDING_NONE
            )
        }

        internal fun getRsaCipher(): Cipher {
            return Cipher.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA + "/" +
                    KeyProperties.BLOCK_MODE_ECB + "/" +
                    KeyProperties.ENCRYPTION_PADDING_RSA_OAEP
            )
        }
    }
}
