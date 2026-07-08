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

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.client.OidcConfiguration
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
internal class DefaultTokenEncryptionHandlerTest {
    private val defaultSecurity = Credential.Security.Default("keyAlias")
    private val token =
        Token(
            id = "id",
            tokenType = "Bearer",
            expiresIn = 1000,
            accessToken = "accessToken",
            scope = "scope",
            refreshToken = "refreshToken",
            idToken = "idToken",
            deviceSecret = "deviceSecret",
            issuedTokenType = "issuedTokenType",
            oidcConfiguration = OidcConfiguration("clientId", "defaultScope", "discoveryUrl")
        )

    private lateinit var tokenEncryptionHandler: DefaultTokenEncryptionHandler

    @Before
    fun setup() {
        tokenEncryptionHandler = DefaultTokenEncryptionHandler()
        tokenEncryptionHandler.keyStore.deleteEntry(defaultSecurity.keyAlias)
    }

    @Test
    fun generateKeyShouldCreateKeyInKeyStore() {
        assertThat(tokenEncryptionHandler.keyStore.containsAlias(defaultSecurity.keyAlias)).isFalse()
        tokenEncryptionHandler.generateKey(defaultSecurity)
        assertThat(tokenEncryptionHandler.keyStore.containsAlias(defaultSecurity.keyAlias)).isTrue()
    }

    @Test
    fun encryptSucceeds() =
        runTest {
            tokenEncryptionHandler.generateKey(defaultSecurity)
            tokenEncryptionHandler.encrypt(token, defaultSecurity)
        }

    @Test
    fun encryptThenDecryptSucceeds() =
        runTest {
            tokenEncryptionHandler.generateKey(defaultSecurity)
            val encryptionResult = tokenEncryptionHandler.encrypt(token, defaultSecurity)
            val decryptionResult =
                tokenEncryptionHandler.decrypt(
                    encryptionResult.encryptedToken,
                    encryptionResult.encryptionExtras,
                    defaultSecurity
                )
            assertThat(decryptionResult).isEqualTo(token)
        }

    @Test
    fun encryptThrowsKeyPermanentlyInvalidatedException_whenKeyDeletedAfterGeneration() =
        runTest {
            tokenEncryptionHandler.generateKey(defaultSecurity)
            tokenEncryptionHandler.keyStore.deleteEntry(defaultSecurity.keyAlias)
            assertFailsWith<KeyPermanentlyInvalidatedException> {
                tokenEncryptionHandler.encrypt(token, defaultSecurity)
            }
        }

    @Test
    fun decryptingBioTokenWithNoPromptFails() =
        runTest {
            val bioSecurity = Credential.Security.BiometricStrong(keyAlias = defaultSecurity.keyAlias)
            tokenEncryptionHandler.generateKey(bioSecurity)
            val encryptionResult = tokenEncryptionHandler.encrypt(token, bioSecurity)
            val exception =
                assertFailsWith<IllegalArgumentException> {
                    tokenEncryptionHandler.decrypt(
                        encryptionResult.encryptedToken,
                        encryptionResult.encryptionExtras,
                        bioSecurity
                    )
                }
            assertThat(exception.message).isEqualTo(DefaultTokenEncryptionHandler.BIO_TOKEN_NO_PROMPT_INFO_ERROR)
        }

    @Test
    fun decryptRoutesThroughAndroidBiometricAuthenticator_andPreservesThrowingContractOnFailure() =
        runTest {
            // AndroidBiometricAuthenticator internally resolves a Cipher/key for `security.keyAlias` from
            // `keyStore`. Deleting the key after key-generation forces its cipher resolution to fail before
            // any BiometricPrompt UI is shown (an InvalidKeyException, a GeneralSecurityException), which
            // surfaces internally as a Result.failure(...) from AndroidBiometricAuthenticator and is
            // unwrapped/re-thrown by DefaultTokenEncryptionHandler.decrypt(...) at its existing public
            // throwing boundary rather than being swallowed or silently returned.
            val bioSecurity =
                Credential.Security.BiometricStrong(
                    keyAlias = defaultSecurity.keyAlias,
                    userAuthenticationTimeout = 0 // auth-per-use key routes through AndroidBiometricAuthenticator.decrypt(...)
                )
            tokenEncryptionHandler.generateKey(bioSecurity)
            val encryptionResult = tokenEncryptionHandler.encrypt(token, bioSecurity)
            tokenEncryptionHandler.keyStore.deleteEntry(bioSecurity.keyAlias)

            assertFailsWith<GeneralSecurityException> {
                tokenEncryptionHandler.decrypt(
                    encryptionResult.encryptedToken,
                    encryptionResult.encryptionExtras,
                    bioSecurity,
                    promptInfo =
                        androidx.biometric.BiometricPrompt.PromptInfo
                            .Builder()
                            .setTitle("title")
                            .setNegativeButtonText("cancel")
                            .build()
                )
            }
        }
}
