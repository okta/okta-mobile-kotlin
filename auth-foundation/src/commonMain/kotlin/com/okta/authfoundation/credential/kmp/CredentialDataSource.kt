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
import com.okta.authfoundation.credential.events.TokenStorageAccessErrorEvent
import com.okta.authfoundation.events.Event
import com.okta.authfoundation.util.CoalescingOrchestrator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield

/**
 * Cross-platform data source managing [TokenInfo] storage.
 *
 * Handles token CRUD with caching. Event emission is handled by credential
 * implementations ([CredentialImpl], Android [Credential]).
 *
 * @param storage the [TokenStorage] for persisting tokens.
 * @param eventsFlow optional shared flow for emitting [TokenStorageAccessErrorEvent]. If provided, database exceptions will be caught and emitted.
 */
internal class CredentialDataSource(
    private val storage: TokenStorage,
    private val eventsFlow: MutableSharedFlow<Event>? = null,
) {
    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<String, TokenData>()

    // Shared state for immutable credential snapshots (T001-T003)
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
        cacheMutex.withLock { deletedIds.add(id) }
        // Emit null to signal deletion on the token flow.
        emitToken(id, null)
    }

    /** Returns true if the credential with [id] has been deleted. */
    suspend fun isDeleted(id: String): Boolean = cacheMutex.withLock { id in deletedIds }

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
    suspend fun allIds(): List<String> =
        wrapStorageOperation {
            storage.allIds().getOrThrow()
        }

    /** Returns metadata for the token with [id]. */
    suspend fun metadata(id: String): TokenMetadata? =
        wrapStorageOperation {
            storage.metadata(id).getOrThrow()
        }

    /** Updates metadata for a stored token. */
    suspend fun setMetadata(metadata: TokenMetadata) {
        wrapStorageOperation {
            storage.setMetadata(metadata).getOrThrow()
        }
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
        cacheMutex.withLock {
            cache[token.id] = token
        }
        wrapStorageOperation {
            storage.add(token, metadata).getOrThrow()
        }
        return token
    }

    /** Replaces an existing token atomically. Returns the updated token. */
    suspend fun replaceToken(token: TokenInfo): TokenData? {
        val existing = cacheMutex.withLock { cache[token.id] }
        if (existing == null) {
            throw IllegalStateException("Attempted replacing a non-existent Token")
        }
        wrapStorageOperation {
            storage.replace(token).getOrThrow()
        }
        if (token is TokenData) {
            val updated =
                token.copy(
                    refreshToken = token.refreshToken ?: existing.refreshToken,
                    deviceSecret = token.deviceSecret ?: existing.deviceSecret
                )
            cacheMutex.withLock { cache[token.id] = updated }
            return updated
        }
        return null
    }

    /** Retrieves a token by ID, using cache if available. */
    suspend fun getToken(id: String): TokenInfo? {
        cacheMutex.withLock {
            cache[id]?.let { return it }
        }
        val token =
            wrapStorageOperation {
                storage.getToken(id).getOrNull()
            } ?: return null
        if (token is TokenData) {
            cacheMutex.withLock { cache[id] = token }
        }
        return token
    }

    /** Removes a token from storage and cleans up shared state. */
    suspend fun remove(id: String) {
        cacheMutex.withLock { cache.remove(id) }
        synchronized(tokenFlows) { tokenFlows.remove(id) }
        synchronized(refreshOrchestrators) { refreshOrchestrators.remove(id) }
        wrapStorageOperation {
            storage.remove(id).getOrThrow()
        }
    }

    /** Finds all tokens matching the predicate. */
    suspend fun findTokens(where: (TokenMetadata) -> Boolean): List<TokenInfo> =
        allIds()
            .mapNotNull { metadata(it) }
            .filter { where(it) }
            .mapNotNull { metadata -> getToken(metadata.id) }

    /**
     * Wraps a storage operation with error handling and event emission.
     *
     * If the operation throws an exception and eventsFlow is available, emits
     * a [TokenStorageAccessErrorEvent]. If the event's [TokenStorageAccessErrorEvent.shouldClearStorageAndTryAgain]
     * is true, attempts the operation again.
     */
    private suspend fun <T> wrapStorageOperation(operation: suspend () -> T): T =
        try {
            operation()
        } catch (e: Exception) {
            if (eventsFlow != null) {
                val event =
                    TokenStorageAccessErrorEvent(
                        exception = e,
                        shouldClearStorageAndTryAgain = false
                    )
                // Emit event non-blocking (adds to replay buffer immediately if there's room).
                // Fall back to suspending emit if needed (buffer full or to wait for subscribers).
                if (!eventsFlow.tryEmit(event)) {
                    eventsFlow.emit(event)
                }
                // Yield control to allow other coroutines (like recovery handlers) to run.
                yield()

                // If recovery is requested, attempt the operation again (after theoretical cleanup)
                if (event.shouldClearStorageAndTryAgain) {
                    try {
                        operation()
                    } catch (retryException: Exception) {
                        throw retryException
                    }
                } else {
                    throw e
                }
            } else {
                throw e
            }
        }
}
