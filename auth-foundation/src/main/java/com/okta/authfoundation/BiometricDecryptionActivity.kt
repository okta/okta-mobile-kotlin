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

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.AuthenticationCallback
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.core.content.ContextCompat
import com.okta.authfoundation.client.ApplicationContextHolder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BiometricDecryptionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_decrypt_biometric)

        when (val action = biometricAction) {
            is BiometricAction.Unlock -> {
                biometricPrompt().authenticate(promptInfo)
            }

            is BiometricAction.Decrypt -> {
                biometricPrompt().authenticate(
                    promptInfo,
                    BiometricPrompt.CryptoObject(action.cipher)
                )
            }
        }
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

    private fun biometricPrompt(): BiometricPrompt {
        val executor = ContextCompat.getMainExecutor(this)
        return BiometricPrompt(
            this, executor,
            object : AuthenticationCallback() {
                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    biometricAction.resumeWithException(
                        BiometricAuthenticationException(
                            "Failed biometric authentication with errorCode: $errorCode, and errString: $errString",
                            BiometricExceptionDetails.OnAuthenticationError(errorCode, errString)
                        )
                    )
                    finish()
                }

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    when (val action = biometricAction) {
                        is BiometricAction.Unlock -> action.continuation.resume(Unit)
                        is BiometricAction.Decrypt -> {
                            result.cryptoObject?.cipher?.let { cipher ->
                                val decryptedData = cipher.doFinal(action.encryptedData)
                                action.continuation.resume(decryptedData)
                            } ?: run {
                                action.resumeWithException(
                                    IllegalStateException(
                                        "Biometric prompt onAuthenticationSucceeded called without crypto object"
                                    )
                                )
                            }
                        }
                    }
                    finish()
                }

                override fun onAuthenticationFailed() {
                    biometricAction.resumeWithException(
                        BiometricAuthenticationException(
                            "Unexpected Biometric error",
                            BiometricExceptionDetails.OnAuthenticationFailed
                        )
                    )
                    finish()
                }
            }
        )
    }

    private sealed interface BiometricAction {
        fun resumeWithException(exception: Exception)

        class Unlock(
            val continuation: Continuation<Unit>
        ) : BiometricAction {
            override fun resumeWithException(exception: Exception) {
                continuation.resumeWithException(exception)
            }
        }

        class Decrypt(
            val cipher: Cipher,
            val encryptedData: ByteArray,
            val continuation: Continuation<ByteArray>
        ) : BiometricAction {
            override fun resumeWithException(exception: Exception) {
                continuation.resumeWithException(exception)
            }
        }
    }

    internal companion object {
        private lateinit var promptInfo: PromptInfo
        private lateinit var biometricAction: BiometricAction
        private val accessMutex = Mutex()

        private fun startActivity() {
            val intent = Intent()
            intent.setClass(ApplicationContextHolder.appContext, BiometricDecryptionActivity::class.java)
            intent.setAction(BiometricDecryptionActivity::class.java.name)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            ApplicationContextHolder.appContext.startActivity(intent)
        }

        internal suspend fun biometricUnlock(promptInfo: PromptInfo) {
            return accessMutex.withLock {
                suspendCancellableCoroutine { continuation ->
                    this.promptInfo = promptInfo
                    biometricAction = BiometricAction.Unlock(continuation)
                    startActivity()
                }
            }
        }

        internal suspend fun biometricDecrypt(
            cipher: Cipher,
            encryptedData: ByteArray,
            promptInfo: PromptInfo
        ): ByteArray {
            return accessMutex.withLock {
                suspendCancellableCoroutine { continuation ->
                    this.promptInfo = promptInfo
                    biometricAction = BiometricAction.Decrypt(
                        cipher, encryptedData, continuation
                    )
                    startActivity()
                }
            }
        }
    }
}

class BiometricAuthenticationException(
    message: String,
    val biometricExceptionDetails: BiometricExceptionDetails
) : GeneralSecurityException(message)

sealed interface BiometricExceptionDetails {
    data object OnAuthenticationFailed : BiometricExceptionDetails
    data class OnAuthenticationError(
        val errorCode: Int,
        val errString: CharSequence
    ) : BiometricExceptionDetails
}
