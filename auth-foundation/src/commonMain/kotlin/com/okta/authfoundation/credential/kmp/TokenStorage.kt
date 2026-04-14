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

import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.credential.TokenMetadata

/**
 * Platform-agnostic abstraction for persisting and retrieving OAuth2 tokens.
 *
 * Platform-specific implementations handle encryption, database access,
 * and secure storage. On Android, the existing [TokenStorage][com.okta.authfoundation.credential.TokenStorage]
 * interface adds biometric-aware operations.
 *
 * Warning: When implementing a custom [TokenStorage], it is vitally important that you
 * do not directly invoke any of these methods yourself. These methods are intended to be called
 * on-demand by the other AuthFoundation classes, and the behavior is undefined if these methods
 * are called directly by the developer.
 */
interface TokenStorage {
    /**
     * Returns the IDs of all stored tokens.
     */
    suspend fun allIds(): Result<List<String>>

    /**
     * Returns metadata for the token with [id], or null if not found.
     *
     * @param id id of stored token to retrieve metadata for.
     */
    suspend fun metadata(id: String): Result<TokenMetadata?>

    /**
     * Updates the metadata for an existing token.
     */
    suspend fun setMetadata(metadata: TokenMetadata): Result<Unit>

    /**
     * Stores a new token with associated metadata.
     *
     * @param token the token to add to storage.
     * @param metadata metadata for the token.
     */
    suspend fun add(
        token: TokenInfo,
        metadata: TokenMetadata,
    ): Result<Unit>

    /**
     * Removes the token with [id] from storage. Does nothing if the provided [id] does not exist.
     *
     * @param id id of the token to remove from storage.
     */
    suspend fun remove(id: String): Result<Unit>

    /**
     * Replaces an existing token with the same ID atomically.
     *
     * @param token set the storage entry with this token's id to this token.
     */
    suspend fun replace(token: TokenInfo): Result<Unit>

    /**
     * Retrieves and decrypts the token with [id].
     *
     * @param id id of the token to be retrieved.
     * @return [Result.success] with the token, or [Result.failure] with [NoSuchElementException] if not found.
     */
    suspend fun getToken(id: String): Result<TokenInfo>
}
