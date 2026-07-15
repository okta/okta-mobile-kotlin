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
package com.okta.authfoundation.credential.jvm

import com.okta.authfoundation.client.jvm.AuthFoundationResult
import com.okta.authfoundation.credential.TokenMetadata
import com.okta.authfoundation.credential.kmp.Credential
import com.okta.authfoundation.credential.kmp.DefaultCredentialIdStore
import com.okta.authfoundation.credential.kmp.TokenData
import com.okta.authfoundation.credential.kmp.TokenStorage
import com.okta.authfoundation.events.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import java.util.function.Predicate
import com.okta.authfoundation.client.kmp.OAuth2Client as KmpOAuth2Client
import com.okta.authfoundation.credential.kmp.TokenCredentialManager as KmpCredentialManager

/**
 * Java-friendly token credential manager for JVM applications.
 *
 * Provides blocking CRUD operations for credentials, wrapping the cross-platform
 * [KmpCredentialManager][com.okta.authfoundation.credential.kmp.TokenCredentialManager]
 * with [AuthFoundationResult] for Java interop.
 *
 * **Warning**: All public methods use [runBlocking] internally. Do **not** call them from
 * a coroutine or a thread that is part of a coroutine dispatcher — this will deadlock.
 * For coroutine-based callers, use [KmpCredentialManager] directly.
 *
 * Event listeners can be registered via [addEventListener] to receive credential lifecycle events.
 *
 * @param client The [OAuth2Client] used for token operations.
 * @param storage The [TokenStorage] for persisting tokens.
 * @param defaultIdStore Store for the default credential ID.
 * @param onStorageError Optional predicate invoked when a storage operation throws. Return `true`
 *   to retry the failed operation once; return `false` (or omit) to rethrow.
 *   Example: `e -> e instanceof java.sql.SQLException`
 */
class TokenCredentialManager
    @JvmOverloads
    constructor(
        private val client: KmpOAuth2Client,
        storage: TokenStorage,
        defaultIdStore: DefaultCredentialIdStore,
        onStorageError: Predicate<Exception>? = null,
    ) : Closeable {
        private val delegate =
            KmpCredentialManager(
                client = client,
                storage = storage,
                defaultIdStore = defaultIdStore,
                onStorageError = onStorageError?.let { predicate -> { e: Exception -> predicate.test(e) } }
            )

        private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val listeners = CopyOnWriteArrayList<Consumer<Event>>()

        init {
            coroutineScope.launch {
                delegate.events.collect { event ->
                    listeners.forEach { it.accept(event) }
                }
            }
        }

        /**
         * Registers a listener that is notified whenever a credential lifecycle event occurs.
         *
         * @param listener A [Consumer] that receives [Event] updates.
         * @return A [Closeable] that, when closed, removes the listener.
         */
        fun addEventListener(listener: Consumer<Event>): Closeable {
            listeners.add(listener)
            return Closeable { listeners.remove(listener) }
        }

        /**
         * Stores a new credential with the given token data and optional tags.
         *
         * @param tokenData The token data to store.
         * @param tags Optional key-value tags to associate with the credential.
         * @return The stored [Credential].
         */
        @JvmOverloads
        fun store(
            tokenData: TokenData,
            tags: Map<String, String> = emptyMap(),
        ): AuthFoundationResult<Credential> =
            runCatching { runBlocking { delegate.store(tokenData, tags).getOrThrow() } }
                .let { AuthFoundationResult.fromKotlinResult(it) }

        /**
         * Retrieves a credential by its ID.
         *
         * Use this when you already know the credential ID (e.g., stored in app preferences).
         * To retrieve the "current user" credential without tracking the ID, use [getDefault] instead.
         *
         * @param id The credential identifier.
         * @return The [Credential] or null if not found.
         * @see getDefault
         */
        fun get(id: String): AuthFoundationResult<Credential?> =
            runCatching { runBlocking { delegate.get(id).getOrThrow() } }
                .let { AuthFoundationResult.fromKotlinResult(it) }

        /**
         * Returns all stored credential IDs.
         */
        fun allIds(): AuthFoundationResult<List<String>> =
            runCatching { runBlocking { delegate.allIds().getOrThrow() } }
                .let { AuthFoundationResult.fromKotlinResult(it) }

        /**
         * Returns metadata for the credential with the specified ID.
         */
        fun metadata(id: String): AuthFoundationResult<TokenMetadata?> =
            runCatching { runBlocking { delegate.metadata(id).getOrThrow() } }
                .let { AuthFoundationResult.fromKotlinResult(it) }

        /**
         * Deletes a credential by ID.
         */
        fun delete(id: String): AuthFoundationResult<Unit> =
            runCatching { runBlocking { delegate.dataSource.remove(id) } }
                .let { AuthFoundationResult.fromKotlinResult(it) }

        /**
         * Returns the default credential, or null if no default has been set.
         *
         * The default credential is set via [setDefault]. This is a convenience for apps with a
         * "current user" concept — store once with [setDefault], retrieve later without tracking
         * the credential ID. To retrieve a credential by a known ID, use [get] instead.
         *
         * @return The default [Credential], or null if no default is set or the default credential
         *   no longer exists in storage.
         * @see setDefault
         * @see get
         */
        fun getDefault(): AuthFoundationResult<Credential?> =
            runCatching { runBlocking { delegate.getDefault().getOrThrow() } }
                .let { AuthFoundationResult.fromKotlinResult(it) }

        /**
         * Sets the default credential.
         *
         * @param credential The credential to set as default, or null to clear.
         */
        fun setDefault(credential: Credential?): AuthFoundationResult<Unit> =
            runCatching { runBlocking { delegate.setDefault(credential).getOrThrow() } }
                .let { AuthFoundationResult.fromKotlinResult(it) }

        /**
         * Creates a [TokenData] from raw token fields. Convenience method for Java callers.
         *
         * @param accessToken The OAuth2 access token.
         * @param tokenType The token type (e.g., "Bearer").
         * @param expiresIn Token lifetime in seconds.
         * @return A [TokenData] ready to be stored.
         */
        @JvmOverloads
        fun createTokenData(
            accessToken: String,
            tokenType: String = "Bearer",
            expiresIn: Int = 3600,
            scope: String? = null,
            refreshToken: String? = null,
            idToken: String? = null,
            deviceSecret: String? = null,
        ): TokenData =
            TokenData(
                id = UUID.randomUUID().toString(),
                tokenType = tokenType,
                expiresIn = expiresIn,
                accessToken = accessToken,
                scope = scope,
                refreshToken = refreshToken,
                idToken = idToken,
                deviceSecret = deviceSecret,
                issuedTokenType = null,
                configuration = client.configuration
            )

        /**
         * Closes this manager, cancelling event dispatch.
         */
        override fun close() {
            coroutineScope.cancel()
        }
    }
