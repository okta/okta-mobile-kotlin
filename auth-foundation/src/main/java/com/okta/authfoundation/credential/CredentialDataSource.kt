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

import com.okta.authfoundation.AuthFoundationDefaults
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.credential.events.CredentialCreatedEvent
import com.okta.authfoundation.util.CoalescingOrchestrator
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
         * @param storage the [TokenStorage] used to persist [Token]s, see [AuthFoundationDefaults.storage] for information on the default.
         * @receiver the [OidcClient] used to perform the low level OIDC requests, as well as with which to use the configuration from.
         */
        fun OidcClient.credentialDataSource(
            storage: TokenStorage = AuthFoundationDefaults.storage,
        ): CredentialDataSource {
            return CredentialDataSource(this, storage)
        }
    }

    private val credentials = CoalescingOrchestrator(
        factory = ::fetchCredentialsFromStorage,
        keepDataInMemory = { true },
    )

    private suspend fun fetchCredentialsFromStorage(): MutableList<Credential> {
        return Collections.synchronizedList(storage.entries().map {
            val metadataCopy = it.metadata.toMap() // Making a defensive copy, so it's not modified outside our control.
            Credential(oidcClient, storage, this, it.identifier, it.token, metadataCopy)
        }.toMutableList())
    }

    /**
     * Creates a [Credential], and stores it in the associated [TokenStorage].
     */
    suspend fun create(): Credential {
        val storageIdentifier = UUID.randomUUID().toString()
        val credential = Credential(oidcClient, storage, this, storageIdentifier)
        credentials.get().add(credential)
        storage.add(storageIdentifier)
        oidcClient.configuration.eventCoordinator.sendEvent(CredentialCreatedEvent(credential))
        return credential
    }

    /**
     * Returns all of the [Credential]s stored in the associated [TokenStorage].
     */
    suspend fun all(): List<Credential> {
        return credentials.get().toMutableList() // Making a defensive copy, so it's not modified outside our control.
    }

    internal suspend fun remove(credential: Credential) {
        credentials.get().remove(credential)
    }
}