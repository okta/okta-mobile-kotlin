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
import com.okta.authfoundation.credential.Credential
import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.credential.TokenEncryptionHandler
import kotlinx.serialization.json.Json

class TestTokenEncryptionHandler : TokenEncryptionHandler {
    override fun generateKey(security: Credential.Security) {
        // Do nothing
    }

    override suspend fun encrypt(
        token: Token,
        security: Credential.Security
    ): TokenEncryptionHandler.EncryptionResult {
        val serializedToken = Json.encodeToString(Token.serializer(), token)
        return TokenEncryptionHandler.EncryptionResult(serializedToken.toByteArray(), emptyMap())
    }

    override suspend fun decrypt(
        encryptedToken: ByteArray,
        encryptionExtras: Map<String, String>,
        security: Credential.Security,
        promptInfo: BiometricPrompt.PromptInfo?
    ): Token {
        val serializedToken = encryptedToken.decodeToString()
        val token = Json.decodeFromString(Token.serializer(), serializedToken)
        return token
    }
}
