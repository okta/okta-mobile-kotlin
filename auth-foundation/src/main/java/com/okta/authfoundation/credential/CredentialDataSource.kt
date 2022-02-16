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
package com.okta.authfoundation.credential

import com.okta.authfoundation.OktaSdkDefaults
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.credential.events.CredentialCreatedEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.UUID

/**
 * Responsible for managing [Credential] instances.
 *
 * This is intended to be held as a singleton, and used throughout the lifecycle of the application.
 */
class CredentialDataSource internal constructor(
    private val oidcClient: OidcClient,
    private val storage: TokenStorage,
) {
    companion object {
        /**
         * Initializes a credential data source using the [OidcClient].
         *
         * @param storage the [TokenStorage] used to persist [Token]s, see [OktaSdkDefaults.storage] for information on the default.
         * @receiver the [OidcClient] used to perform the low level OIDC requests, as well as with which to use the configuration from.
         */
        fun OidcClient.credentialDataSource(
            storage: TokenStorage = OktaSdkDefaults.storage,
        ): CredentialDataSource {
            return CredentialDataSource(this, storage)
        }
    }

    private val _credentials: MutableList<Credential> = Collections.synchronizedList(mutableListOf())
    @Volatile private var credentialsInitialized: Boolean = false

    private val credentialsLock: Any = Any()
    @Volatile private var deferredCredentials: Deferred<MutableList<Credential>>? = null

    private suspend fun credentials(): MutableList<Credential> {
        if (credentialsInitialized) {
            return _credentials
        }
        return withContext(oidcClient.configuration.ioDispatcher) {
            val deferred: Deferred<MutableList<Credential>>
            synchronized(credentialsLock) {
                if (credentialsInitialized) {
                    return@withContext _credentials
                }
                val localDeferred = deferredCredentials
                if (localDeferred != null) {
                    deferred = localDeferred
                } else {
                    deferred = loadCredentialsAsync(this@withContext)
                    deferredCredentials = deferred
                }
            }
            deferred.await()
        }
    }

    private fun loadCredentialsAsync(scope: CoroutineScope): Deferred<MutableList<Credential>> {
        return scope.async(start = CoroutineStart.LAZY) {
            val result = storage.entries().map {
                val metadataCopy = it.metadata.toMap() // Making a defensive copy, so it's not modified outside our control.
                Credential(oidcClient, storage, this@CredentialDataSource, it.identifier, it.token, metadataCopy)
            }.toMutableList()
            synchronized(credentialsLock) {
                _credentials += result
                credentialsInitialized = true
            }
            _credentials
        }
    }

    /**
     * Creates a [Credential], and stores it in the associated [TokenStorage].
     */
    suspend fun create(): Credential {
        val storageIdentifier = UUID.randomUUID().toString()
        val credential = Credential(oidcClient, storage, this, storageIdentifier)
        credentials().add(credential)
        storage.add(storageIdentifier)
        oidcClient.configuration.eventCoordinator.sendEvent(CredentialCreatedEvent(credential))
        return credential
    }

    /**
     * Returns all of the [Credential]s stored in the associated [TokenStorage].
     */
    suspend fun all(): List<Credential> {
        return credentials().toMutableList() // Making a defensive copy, so it's not modified outside our control.
    }

    internal suspend fun remove(credential: Credential) {
        credentials().remove(credential)
    }
}
