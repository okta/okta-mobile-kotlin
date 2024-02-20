/*
 * Copyright 2021-Present Okta, Inc.
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
import com.okta.authfoundation.credential.CredentialDataSource.Companion.createCredentialDataSource
import java.util.Objects

/**
 * Interface used to customize the way tokens are stored, updated, and removed throughout the lifecycle of an application.
 *
 * A default implementation is provided, but for advanced use-cases, you may implement this protocol yourself and pass an instance to [CredentialDataSource.createCredentialDataSource].
 *
 * Warning: When implementing a custom [TokenStorage] class, it's vitally important that you do not directly invoke any of these methods yourself. These methods are intended to be called on-demand by the other AuthFoundation classes, and the behavior is undefined if these methods are called directly by the developer.
 */
interface TokenStorage {
    suspend fun allIds(): List<String>
    suspend fun metadata(id: String): Token.Metadata?
    suspend fun setMetadata(metadata: Token.Metadata)
    suspend fun add(
        token: Token,
        metadata: Token.Metadata,
        security: Credential.Security = Credential.Security.standard
    )
    suspend fun remove(id: String)
    suspend fun replace(
        id: String,
        token: Token,
        metadata: Token.Metadata? = null,
        security: Credential.Security? = null
    )
    suspend fun getToken(id: String, promptInfo: BiometricPrompt.PromptInfo? = Credential.Security.promptInfo): Token
    suspend fun getDefaultToken(promptInfo: BiometricPrompt.PromptInfo? = Credential.Security.promptInfo): Token?

    /**
     *  Represents the data to store in [TokenStorage].
     */
    class Entry(
        /**
         * The unique identifier for this [TokenStorage] entry.
         */
        val identifier: String,
        /**
         *  The [Token] associated with the [TokenStorage] entry.
         */
        val token: Token?,
        /**
         *  The tags associated with the [TokenStorage] entry.
         */
        val tags: Map<String, String>,
    ) {
        override fun equals(other: Any?): Boolean {
            if (other === this) {
                return true
            }
            if (other !is Entry) {
                return false
            }
            return other.identifier == identifier &&
                other.token == token &&
                other.tags == tags
        }

        override fun hashCode(): Int {
            return Objects.hash(
                identifier,
                token,
                tags,
            )
        }
    }
}
