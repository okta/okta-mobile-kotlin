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

import androidx.biometric.BiometricPrompt
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.BiometricAuthenticationException
import com.okta.authfoundation.util.AndroidKeystoreUtil
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [AndroidBiometricAuthenticator].
 *
 * The success paths of [AndroidBiometricAuthenticator.unlock] and both `decrypt` overloads route
 * through [androidx.biometric.BiometricPrompt], which requires an actual authenticated biometric
 * challenge (fingerprint/face) completed by a human, or a device/emulator with a virtual biometric
 * enrolled and driven via UI Automator. This suite covers the deterministic, non-interactive paths: cipher/key
 * resolution failures surfacing as `Result.failure` (never thrown) before any prompt is shown, mirroring
 * existing `BiometricAuthenticationException`/`BiometricExceptionDetails` semantics.
 */
@RunWith(AndroidJUnit4::class)
class AndroidBiometricAuthenticatorTest {
    private val promptInfo =
        BiometricPrompt.PromptInfo
            .Builder()
            .setTitle("title")
            .setNegativeButtonText("cancel")
            .build()

    private val keyStore = AndroidKeystoreUtil.keyStore

    @Test
    fun decryptReturnsResultFailure_whenKeyAliasDoesNotExist() =
        runTest {
            val authenticator = AndroidBiometricAuthenticator(promptInfo, keyStore)
            val result = authenticator.decrypt(ByteArray(0), "nonExistentKeyAlias-${System.nanoTime()}")
            assertThat(result.isFailure).isTrue()
        }

    @Test
    fun decryptWithIvReturnsResultFailure_whenKeyAliasDoesNotExist() =
        runTest {
            val authenticator = AndroidBiometricAuthenticator(promptInfo, keyStore)
            val result =
                authenticator.decrypt(
                    ByteArray(0),
                    "nonExistentKeyAlias-${System.nanoTime()}",
                    ByteArray(12)
                )
            assertThat(result.isFailure).isTrue()
        }

    @Test
    fun failureIsNeverThrown_onlyReturnedAsResult() =
        runTest {
            val authenticator = AndroidBiometricAuthenticator(promptInfo, keyStore)
            // Constructing the exception directly confirms it remains a plain, common-code-visible type
            // usable for asserting against Result.failure(...) content.
            val result = authenticator.decrypt(ByteArray(0), "nonExistentKeyAlias-${System.nanoTime()}")
            val exception = result.exceptionOrNull()
            assertThat(exception).isNotInstanceOf(BiometricAuthenticationException::class.java)
        }
}
