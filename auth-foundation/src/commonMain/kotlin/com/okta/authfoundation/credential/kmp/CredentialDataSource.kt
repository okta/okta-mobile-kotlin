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
import com.okta.authfoundation.util.CoalescingOrchestrator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.transformWhile

/**
 * Cross-platform data source managing [TokenInfo] storage.
 *
 * Handles token CRUD with caching. Event emission is handled by credential
 * implementations ([CredentialImpl], Android [Credential]).
 *
 * All shared state is guarded by [synchronized] because every critical section is a
 * trivially short map operation that never suspends. This avoids the overhead and
 * `suspend`-signature requirement of [kotlinx.coroutines.sync.Mutex].
 */
internal class CredentialDataSource(
    private val storage: TokenStorage,
) {
    private val cache = mutableMapOf<String, TokenData>()

    private val tokenFlows = mutableMapOf<String, MutableStateFlow<TokenData?>>()
    private val deletedIds = mutableSetOf<String>()
    private val refreshOrchestrators = mutableMapOf<String, CoalescingOrchestrator<Result<TokenInfo>>>()

    /** Returns a shared [Flow] of token updates for the given credential [id]. */
    fun getTokenFlow(
        id: String,
        initialToken: TokenData,
    ): Flow<TokenData> {
        val flow =
            synchronized(tokenFlows) {
                tokenFlows.getOrPut(id) { MutableStateFlow(initialToken) }
            }
        return flow
            .transformWhile {
                emit(it)
                it != null
            }.filterNotNull()
    }

    /** Emits a new token value on the shared flow for the given credential [id]. */
    suspend fun emitToken(
        id: String,
        token: TokenData?,
    ) {
        val flow = synchronized(tokenFlows) { tokenFlows[id] } ?: return
        flow.emit(token)
    }

    /** Marks the credential with [id] as deleted. */
    suspend fun markDeleted(id: String) {
        synchronized(deletedIds) { deletedIds.add(id) }
        // Emit null to signal deletion on the token flow.
        emitToken(id, null)
    }

    /** Returns true if the credential with [id] has been deleted. */
    fun isDeleted(id: String): Boolean = synchronized(deletedIds) { id in deletedIds }

    /**
     * Returns (or creates) a shared [CoalescingOrchestrator] for token refresh deduplication
     * for the given credential [id].
     */
    internal fun getOrCreateRefreshOrchestrator(
        id: String,
        factory: suspend () -> Result<TokenInfo>,
    ): CoalescingOrchestrator<Result<TokenInfo>> =
        synchronized(refreshOrchestrators) {
            refreshOrchestrators.getOrPut(id) {
                CoalescingOrchestrator(
                    factory = factory,
                    keepDataInMemory = { false }
                )
            }
        }

    /** Returns all stored token IDs. */
    suspend fun allIds(): List<String> = storage.allIds().getOrThrow()

    /** Returns metadata for the token with [id]. */
    suspend fun metadata(id: String): TokenMetadata? = storage.metadata(id).getOrThrow()

    /** Updates metadata for a stored token. */
    suspend fun setMetadata(metadata: TokenMetadata) {
        storage.setMetadata(metadata).getOrThrow()
    }

    /** Stores a new token and returns its data. */
    suspend fun createToken(
        token: TokenData,
        tags: Map<String, String> = emptyMap(),
    ): TokenData {
        val metadata =
            TokenMetadata(
                id = token.id,
                tags = tags,
                payloadData = null
            )
        synchronized(cache) { cache[token.id] = token }
        storage.add(token, metadata).getOrThrow()
        return token
    }

    /** Replaces an existing token atomically. Returns the updated token. */
    suspend fun replaceToken(token: TokenInfo): TokenData? {
        val existing = synchronized(cache) { cache[token.id] }
        if (existing == null) {
            throw IllegalStateException("Attempted replacing a non-existent Token")
        }
        storage.replace(token).getOrThrow()
        if (token is TokenData) {
            val updated =
                token.copy(
                    refreshToken = token.refreshToken ?: existing.refreshToken,
                    deviceSecret = token.deviceSecret ?: existing.deviceSecret
                )
            synchronized(cache) { cache[token.id] = updated }
            return updated
        }
        return null
    }

    /** Retrieves a token by ID, using cache if available. Returns null only if the token does not exist. */
    suspend fun getToken(id: String): TokenInfo? {
        synchronized(cache) { cache[id] }?.let { return it }
        metadata(id) ?: return null
        val token = storage.getToken(id).getOrNull() ?: return null
        if (token is TokenData) {
            synchronized(cache) { cache[id] = token }
        }
        return token
    }

    /** Removes a token from storage and cleans up shared state. */
    suspend fun remove(id: String) {
        synchronized(cache) { cache.remove(id) }
        synchronized(tokenFlows) { tokenFlows.remove(id) }
        synchronized(refreshOrchestrators) { refreshOrchestrators.remove(id) }
        storage.remove(id).getOrThrow()
    }

    /** Finds all tokens matching the predicate. */
    suspend fun findTokens(where: (TokenMetadata) -> Boolean): List<TokenInfo> =
        allIds()
            .mapNotNull { metadata(it) }
            .filter { where(it) }
            .mapNotNull { metadata -> getToken(metadata.id) }
}
