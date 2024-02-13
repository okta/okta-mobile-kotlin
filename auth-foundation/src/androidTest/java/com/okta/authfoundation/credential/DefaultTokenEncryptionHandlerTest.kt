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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.AuthFoundationDefaults
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
internal class DefaultTokenEncryptionHandlerTest {
    private val defaultSecurityOption = SecurityOptions.Default("keyAlias")
    private val token = Token(
        tokenType = "Bearer",
        expiresIn = 1000,
        accessToken = "accessToken",
        scope = "scope",
        refreshToken = "refreshToken",
        idToken = "idToken",
        deviceSecret = "deviceSecret",
        issuedTokenType = "issuedTokenType"
    )

    private lateinit var tokenEncryptionHandler: DefaultTokenEncryptionHandler

    @Before fun setup() {
        tokenEncryptionHandler = DefaultTokenEncryptionHandler()
        tokenEncryptionHandler.keyStore.deleteEntry(defaultSecurityOption.keyAlias)
    }

    @Test fun generateKeyShouldCreateKeyInKeyStore() {
        assertThat(tokenEncryptionHandler.keyStore.containsAlias(defaultSecurityOption.keyAlias)).isFalse()
        tokenEncryptionHandler.generateKey(defaultSecurityOption)
        assertThat(tokenEncryptionHandler.keyStore.containsAlias(defaultSecurityOption.keyAlias)).isTrue()
    }

    @Test fun encryptSucceeds() = runTest {
        tokenEncryptionHandler.generateKey(defaultSecurityOption)
        tokenEncryptionHandler.encrypt(token, defaultSecurityOption.keyAlias, AuthFoundationDefaults.Encryption.algorithm)
    }

    @Test fun encryptThenDecryptSucceeds() = runTest {
        tokenEncryptionHandler.generateKey(defaultSecurityOption)
        val encryptionResult = tokenEncryptionHandler.encrypt(token, defaultSecurityOption.keyAlias, AuthFoundationDefaults.Encryption.algorithm)
        val decryptionResult = tokenEncryptionHandler.decrypt(
            encryptionResult.encryptedToken,
            AuthFoundationDefaults.Encryption.algorithm,
            encryptionResult.encryptionExtras,
            defaultSecurityOption.keyAlias,
            userAuthenticationRequired = false
        )
        assertThat(decryptionResult).isEqualTo(token)
    }

    @Test fun decryptingBioTokenWithNoPromptFails() = runTest {
        tokenEncryptionHandler.generateKey(defaultSecurityOption)
        val encryptionResult = tokenEncryptionHandler.encrypt(token, defaultSecurityOption.keyAlias, AuthFoundationDefaults.Encryption.algorithm)
        val exception = assertFailsWith<IllegalArgumentException> {
            tokenEncryptionHandler.decrypt(
                encryptionResult.encryptedToken,
                AuthFoundationDefaults.Encryption.algorithm,
                encryptionResult.encryptionExtras,
                defaultSecurityOption.keyAlias,
                userAuthenticationRequired = true,
                promptInfo = null
            )
        }
        assertThat(exception.message).isEqualTo(DefaultTokenEncryptionHandler.BIO_TOKEN_NO_PROMPT_INFO_ERROR)
    }
}
