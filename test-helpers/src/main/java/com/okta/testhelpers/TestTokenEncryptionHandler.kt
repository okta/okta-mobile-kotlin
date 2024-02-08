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
package com.okta.testhelpers

import androidx.biometric.BiometricPrompt
import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.credential.TokenEncryptionHandler
import kotlinx.serialization.json.Json

class TestTokenEncryptionHandler : TokenEncryptionHandler {
    override fun encrypt(
        token: Token,
        keyAlias: String,
        encryptionAlgorithm: String
    ): TokenEncryptionHandler.EncryptionResult {
        val serializedToken = Json.encodeToString(Token.serializer(), token)
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
        val serializedToken = encryptedToken.decodeToString()
        val token = Json.decodeFromString(Token.serializer(), serializedToken)
        return token
    }
}