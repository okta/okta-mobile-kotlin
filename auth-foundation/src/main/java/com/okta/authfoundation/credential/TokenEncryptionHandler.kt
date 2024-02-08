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

import androidx.biometric.BiometricPrompt
import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.client.OidcConfiguration

interface TokenEncryptionHandler {
    fun encrypt(
        token: Token,
        keyAlias: String,
        encryptionAlgorithm: String
    ): EncryptionResult

    fun decrypt(
        encryptedToken: ByteArray,
        encryptionAlgorithm: String,
        encryptionExtras: Map<String, String>,
        keyAlias: String,
        userAuthenticationRequired: Boolean,
        promptInfo: BiometricPrompt.PromptInfo? = null
    ): Token

    class EncryptionResult(
        val encryptedToken: ByteArray,
        val encryptionExtras: Map<String, String>
    )
}

@InternalAuthFoundationApi
class DefaultTokenEncryptionHandler : TokenEncryptionHandler {
    override fun encrypt(
        token: Token,
        keyAlias: String,
        encryptionAlgorithm: String
    ): TokenEncryptionHandler.EncryptionResult {
        // TODO("Add android keystore encryption")
        val serializedToken = OidcConfiguration.defaultJson().encodeToString(Token.serializer(), token)
        return TokenEncryptionHandler.EncryptionResult(serializedToken.toByteArray(), emptyMap())
    }

    override fun decrypt(
        encryptedToken: ByteArray,
        encryptionAlgorithm: String,
        encryptionExtras: Map<String, String>,
        keyAlias: String,
        userAuthenticationRequired: Boolean,
        promptInfo: BiometricPrompt.PromptInfo?
    ): Token {
        // TODO("Add android keystore encryption")
        val serializedToken = encryptedToken.decodeToString()
        val token = OidcConfiguration.defaultJson().decodeFromString(Token.serializer(), serializedToken)
        return token
    }
}