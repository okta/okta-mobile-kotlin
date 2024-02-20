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
import com.okta.authfoundation.client.OidcConfiguration

internal class TokenEncryptionHandler {
    fun encrypt(
        token: Token,
        security: Credential.Security,
    ): Pair<ByteArray, Map<String, String>> {
        // TODO("Add android keystore encryption")
        val serializedToken = OidcConfiguration.defaultJson().encodeToString(Token.serializer(), token)
        return Pair(serializedToken.toByteArray(), emptyMap())
    }

    fun decrypt(
        encryptedToken: ByteArray,
        encryptionExtras: Map<String, String>,
        security: Credential.Security,
        promptInfo: BiometricPrompt.PromptInfo? = null
    ): Token {
        // TODO("Add android keystore encryption")
        val serializedToken = encryptedToken.decodeToString()
        val token = OidcConfiguration.defaultJson().decodeFromString(Token.serializer(), serializedToken)
        return token
    }
}
