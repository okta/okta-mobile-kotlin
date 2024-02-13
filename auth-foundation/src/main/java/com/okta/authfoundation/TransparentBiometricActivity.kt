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
package com.okta.authfoundation

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.AuthenticationCallback
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.core.content.ContextCompat
import com.okta.authfoundation.credential.DefaultTokenEncryptionHandler
import kotlinx.parcelize.Parcelize
import java.security.KeyStore
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TransparentBiometricActivity : AppCompatActivity() {
    private lateinit var keyStore: KeyStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transparent_biometric)
        val activityParameters = getActivityParameters()
            ?: throw IllegalStateException("${TransparentBiometricActivity::class.simpleName} called without parameters")

        keyStore = KeyStore.getInstance(activityParameters.keyStoreName).apply { load(null) }
        val privateRsaKey = keyStore.getKey(activityParameters.keyAlias, null)
        val rsaCipher = DefaultTokenEncryptionHandler.getRsaCipher()
            .apply { init(Cipher.DECRYPT_MODE, privateRsaKey) }
        biometricPrompt(activityParameters).authenticate(promptInfo, BiometricPrompt.CryptoObject(rsaCipher))
    }

    override fun finish() {
        super.finish()
        // Disable exit animations
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overridePendingTransition(0, 0)
        } else {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
    }

    private fun biometricPrompt(activityParameters: ActivityParameters): BiometricPrompt {
        val executor = ContextCompat.getMainExecutor(this)
        return BiometricPrompt(
            this, executor,
            object : AuthenticationCallback() {
                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    DefaultTokenEncryptionHandler.biometricDecryptionContinuation
                        ?.resumeWithException(
                            BiometricAuthenticationException("Failed biometric authentication with errorCode: $errorCode, and errString: $errString")
                        )
                    DefaultTokenEncryptionHandler.biometricDecryptionContinuation = null
                    finish()
                }

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    result.cryptoObject?.cipher?.let { rsaCipher ->
                        val token = DefaultTokenEncryptionHandler.internalDecrypt(
                            activityParameters.encryptedToken,
                            rsaCipher,
                            activityParameters.encryptionExtras
                        )
                        DefaultTokenEncryptionHandler.biometricDecryptionContinuation?.resume(token)
                    } ?: run {
                        DefaultTokenEncryptionHandler.biometricDecryptionContinuation?.resumeWithException(
                            BiometricAuthenticationException("Biometric prompt onAuthenticationSucceeded called without crypto object")
                        )
                    }
                    DefaultTokenEncryptionHandler.biometricDecryptionContinuation = null
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    DefaultTokenEncryptionHandler.biometricDecryptionContinuation
                        ?.resumeWithException(BiometricAuthenticationException())
                    DefaultTokenEncryptionHandler.biometricDecryptionContinuation = null
                    finish()
                }
            }
        )
    }

    private fun getActivityParameters(): ActivityParameters? {
        return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
            intent.extras?.getParcelable(ACTIVITY_PARAMS, ActivityParameters::class.java)
        } else {
            intent.extras?.getParcelable(ACTIVITY_PARAMS)
        }
    }

    @Parcelize
    internal class ActivityParameters(
        val keyStoreName: String,
        val encryptedToken: ByteArray,
        val encryptionExtras: Map<String, String>,
        val keyAlias: String
    ) : Parcelable

    internal companion object {
        private const val ACTIVITY_PARAMS = "ACTIVITY_PARAMS"

        private lateinit var promptInfo: PromptInfo

        internal fun navigate(
            appContext: Context,
            activityParameters: ActivityParameters,
            promptInfo: PromptInfo
        ) {
            TransparentBiometricActivity.promptInfo = promptInfo
            val intent = Intent()
            intent.putExtra(ACTIVITY_PARAMS, activityParameters)
            intent.setClass(appContext, TransparentBiometricActivity::class.java)
            intent.setAction(TransparentBiometricActivity::class.java.name)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            appContext.startActivity(intent)
        }
    }
}

class BiometricAuthenticationException : IllegalAccessException {
    constructor() : super()
    constructor(message: String) : super(message)
}
