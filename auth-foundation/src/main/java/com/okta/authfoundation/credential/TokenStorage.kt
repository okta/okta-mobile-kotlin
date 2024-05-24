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
import com.okta.authfoundation.AuthFoundationDefaults

/**
 * Interface used to customize the way tokens are stored, updated, and removed throughout the lifecycle of an application.
 *
 * A default implementation is provided, but for advanced use-cases, you may implement this protocol yourself and pass an instance to [AuthFoundationDefaults.tokenStorageFactory].
 *
 * Warning: When implementing a custom [TokenStorage] class, it's vitally important that you do not directly invoke any of these methods yourself. These methods are intended to be called on-demand by the other AuthFoundation classes, and the behavior is undefined if these methods are called directly by the developer.
 */
interface TokenStorage {
    /**
     * @return All token IDs.
     */
    suspend fun allIds(): List<String>

    /**
     * @return [Token.Metadata] for token with specified [id], null if no token with given [id] exists.
     *
     * @param id id of stored token to retrieve [Token.Metadata] for.
     */
    suspend fun metadata(id: String): Token.Metadata?

    /**
     * Set [Token.Metadata] for the token with [Token.Metadata.id]
     */
    suspend fun setMetadata(metadata: Token.Metadata)

    /**
     * Add [Token] and its [Token.Metadata] to storage. Encrypt [Token] with specified [security].
     *
     * @param token [Token] to add to storage.
     * @param metadata [Token.Metadata] for the [token] to add to storage.
     * @param security [Credential.Security] for specifying how to encrypt the [token] before adding to storage.
     */
    suspend fun add(
        token: Token,
        metadata: Token.Metadata,
        security: Credential.Security = Credential.Security.standard
    )

    /**
     * Remove [Token] with given [id] from the storage. Does nothing if the provided [id] does not exist in storage.
     *
     * @param id id of the [Token] to remove from storage.
     */
    suspend fun remove(id: String)

    /**
     * Replace [Token] with [Token.id] with the given [token]. If no such [Token] with the given [Token.id] exists, [NoSuchElementException] is thrown.
     *
     * @param token set the storage entry with [Token.id] to this [Token].
     *
     * @throws [NoSuchElementException] if no such storage entry with [Token.id] exists.
     */
    suspend fun replace(token: Token)

    /**
     * Get [Token] from storage with associated [id].
     *
     * @param id id of the [Token] to be retrieved.
     * @param promptInfo [BiometricPrompt.PromptInfo] to be displayed if the stored [Token] is using biometric [Credential.Security].
     *
     * @return [Token] with the specified [id].
     * @throws [NoSuchElementException] if no storage entry with [id] exists.
     */
    suspend fun getToken(id: String, promptInfo: BiometricPrompt.PromptInfo? = Credential.Security.promptInfo): Token
}
