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

import androidx.biometric.BiometricPrompt
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
internal class AndroidTokenEncryptionHandlerTest {
    private val keyAlias = "AndroidTokenEncryptionHandlerTest.keyAlias"
    private val bioKeyAlias = "$keyAlias.bio"
    private val plaintext = "top-secret-access-token".encodeToByteArray()

    private lateinit var plainHandler: AndroidTokenEncryptionHandler
    private lateinit var bioHandler: AndroidTokenEncryptionHandler

    // Shares bioKeyAlias with `bioHandler` — the RSA key material lives in the shared Android Keystore,
    // not on the handler instance, so a second instance configured with promptInfo can decrypt what the
    // first instance (deliberately constructed without promptInfo) encrypted.
    private lateinit var bioHandlerWithPrompt: AndroidTokenEncryptionHandler

    @Before
    fun setup() {
        plainHandler = AndroidTokenEncryptionHandler(keyAlias = keyAlias)
        bioHandler = AndroidTokenEncryptionHandler(keyAlias = bioKeyAlias, requireBiometric = true, userAuthenticationTimeout = 0)
        bioHandlerWithPrompt =
            AndroidTokenEncryptionHandler(
                keyAlias = bioKeyAlias,
                requireBiometric = true,
                userAuthenticationTimeout = 0,
                promptInfo =
                    BiometricPrompt.PromptInfo
                        .Builder()
                        .setTitle("title")
                        .setNegativeButtonText("cancel")
                        .build()
            )
        plainHandler.keyStore.deleteEntry(keyAlias)
        bioHandler.keyStore.deleteEntry(bioKeyAlias)
    }

    @Test
    fun encryptThenDecryptSucceeds() =
        runTest {
            val result = plainHandler.encrypt(plaintext)
            val decrypted = plainHandler.decrypt(result.ciphertext, result.encryptionExtras)
            assertThat(decrypted).isEqualTo(plaintext)
        }

    @Test
    fun encryptRegeneratesKey_whenKeyDeletedAfterGeneration() =
        runTest {
            // Unlike the legacy DefaultTokenEncryptionHandler (which requires an explicit generateKey()
            // call before use), this handler lazily provisions its Keystore key on first use, since the
            // KMP TokenEncryptionHandler interface has no separate key-generation step. Deleting the alias
            // therefore causes the next encrypt() to self-heal by regenerating the key, rather than fail.
            plainHandler.encrypt(plaintext) // provisions the key
            plainHandler.keyStore.deleteEntry(keyAlias)

            val result = plainHandler.encrypt(plaintext)
            val decrypted = plainHandler.decrypt(result.ciphertext, result.encryptionExtras)
            assertThat(decrypted).isEqualTo(plaintext)
        }

    @Test
    fun decryptingBiometricTokenWithNoPromptInfoFails() =
        runTest {
            val result = bioHandler.encrypt(plaintext)
            val exception =
                assertFailsWith<IllegalArgumentException> {
                    bioHandler.decrypt(result.ciphertext, result.encryptionExtras)
                }
            assertThat(exception.message).contains("promptInfo")
        }

    @Test
    fun biometricDecryptRoutesThroughAuthenticator_andPreservesThrowingContractOnFailure() =
        runTest {
            // AndroidBiometricAuthenticator resolves a Cipher/key for the alias from the Keystore. Deleting
            // the key after encryption forces cipher resolution to fail before any BiometricPrompt UI is
            // shown, surfacing as a GeneralSecurityException rather than being swallowed.
            val result = bioHandler.encrypt(plaintext)
            bioHandler.keyStore.deleteEntry(bioKeyAlias)

            assertFailsWith<GeneralSecurityException> {
                bioHandlerWithPrompt.decrypt(result.ciphertext, result.encryptionExtras)
            }
        }
}
