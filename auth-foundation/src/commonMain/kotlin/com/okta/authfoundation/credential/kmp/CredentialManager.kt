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

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.authfoundation.credential.TokenMetadata
import com.okta.authfoundation.credential.events.CredentialCreatedEvent
import com.okta.authfoundation.credential.events.DefaultCredentialChangedEvent
import com.okta.authfoundation.events.Event
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Cross-platform credential manager providing CRUD operations for credentials.
 *
 * Holds the shared dependencies ([client], [storage], [defaultIdStore])
 * so callers don't need to pass them on every operation.
 *
 * Credential lifecycle events are emitted on [events]. All active collectors receive every event
 * independently. There is no replay; events emitted before a collector subscribes are not delivered.
 *
 * @param client The [OAuth2Client] used for token operations.
 * @param storage The [TokenStorage] for persisting tokens.
 * @param defaultIdStore Store for the default credential ID.
 */
class CredentialManager(
    val client: OAuth2Client,
    storage: TokenStorage,
    val defaultIdStore: DefaultCredentialIdStore,
) {
    internal val dataSource = CredentialDataSource(storage)

    private val _events =
        MutableSharedFlow<Event>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    /**
     * A read-only flow of credential lifecycle events emitted by this manager.
     *
     * Events are multicast — all active collectors receive every event independently.
     * There is no replay; events emitted before a collector subscribes are not delivered.
     * When no collectors are active, events are silently dropped (fire-and-forget semantics).
     * The emitter is never suspended; if the buffer is full, the oldest event is dropped.
     */
    val events: SharedFlow<Event> = _events.asSharedFlow()

    /**
     * Stores a new credential with the given [token] and optional [tags].
     *
     * @return [Result.success] with the stored [Credential].
     */
    @OptIn(InternalAuthFoundationApi::class)
    suspend fun store(
        token: TokenData,
        tags: Map<String, String> = emptyMap(),
    ): Result<Credential> =
        runCatching {
            val stored = dataSource.createToken(token, tags)
            val credential =
                CredentialImpl(
                    token = stored,
                    client = client,
                    tags = tags,
                    dataSource = dataSource,
                    events = _events,
                    defaultIdStore = defaultIdStore
                )
            _events.tryEmit(CredentialCreatedEvent(credential))
            credential
        }

    /**
     * Retrieves a credential by its [id].
     *
     * @return [Result.success] with the [Credential], or null if not found.
     */
    @OptIn(InternalAuthFoundationApi::class)
    suspend fun get(id: String): Result<Credential?> =
        runCatching {
            val token = dataSource.getToken(id) ?: return@runCatching null
            if (token !is TokenData) return@runCatching null
            val metadata = dataSource.metadata(id)
            CredentialImpl(
                token = token,
                tags = metadata?.tags ?: emptyMap(),
                dataSource = dataSource,
                events = _events,
                defaultIdStore = defaultIdStore
            )
        }

    /**
     * Returns IDs of all stored credentials.
     */
    @OptIn(InternalAuthFoundationApi::class)
    suspend fun allIds(): Result<List<String>> = runCatching { dataSource.allIds() }

    /**
     * Returns metadata for the credential with [id].
     */
    @OptIn(InternalAuthFoundationApi::class)
    suspend fun metadata(id: String): Result<TokenMetadata?> = runCatching { dataSource.metadata(id) }

    /**
     * Updates metadata for an existing credential.
     */
    @OptIn(InternalAuthFoundationApi::class)
    suspend fun setMetadata(metadata: TokenMetadata): Result<Unit> = runCatching { dataSource.setMetadata(metadata) }

    /**
     * Returns the default credential, or null if none is set.
     */
    @OptIn(InternalAuthFoundationApi::class)
    suspend fun getDefault(): Result<Credential?> =
        runCatching {
            val defaultId = defaultIdStore.getDefaultCredentialId().getOrThrow() ?: return@runCatching null
            val token = dataSource.getToken(defaultId) ?: return@runCatching null
            if (token !is TokenData) return@runCatching null
            val metadata = dataSource.metadata(defaultId)
            CredentialImpl(
                token = token,
                tags = metadata?.tags ?: emptyMap(),
                dataSource = dataSource,
                events = _events,
                defaultIdStore = defaultIdStore
            )
        }

    /**
     * Sets or clears the default credential.
     *
     * @param credential The credential to set as default, or null to clear.
     */
    suspend fun setDefault(credential: Credential?): Result<Unit> =
        runCatching {
            if (credential != null) {
                defaultIdStore.setDefaultCredentialId(credential.id).getOrThrow()
                _events.tryEmit(DefaultCredentialChangedEvent(credential))
            } else {
                defaultIdStore.clearDefaultCredentialId().getOrThrow()
                _events.tryEmit(DefaultCredentialChangedEvent(null))
            }
        }

    /**
     * Returns all credentials matching the given [where] predicate.
     *
     * @param where a function that receives [TokenMetadata] and returns true for credentials to include.
     */
    @OptIn(InternalAuthFoundationApi::class)
    suspend fun find(where: (TokenMetadata) -> Boolean): Result<List<Credential>> =
        runCatching {
            dataSource
                .allIds()
                .mapNotNull { dataSource.metadata(it) }
                .filter { where(it) }
                .mapNotNull { metadata ->
                    val token = dataSource.getToken(metadata.id) ?: return@mapNotNull null
                    if (token !is TokenData) return@mapNotNull null
                    CredentialImpl(
                        token = token,
                        tags = metadata.tags,
                        dataSource = dataSource,
                        events = _events,
                        defaultIdStore = defaultIdStore
                    )
                }
        }
}
