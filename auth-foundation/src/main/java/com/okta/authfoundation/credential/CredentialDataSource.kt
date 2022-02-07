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

import com.okta.authfoundation.OktaSdk
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.credential.events.CredentialCreatedEvent

class CredentialDataSource internal constructor(
    private val oidcClient: OidcClient,
    private val storage: TokenStorage,
) {
    companion object {
        fun OidcClient.credentialDataSource(
            storage: TokenStorage = OktaSdk.storage,
        ): CredentialDataSource {
            return CredentialDataSource(this, storage)
        }
    }

    fun create(): Credential {
        val credential = Credential(oidcClient, storage)
        oidcClient.configuration.eventCoordinator.sendEvent(CredentialCreatedEvent(credential))
        return credential
    }

    suspend fun all(): List<Credential> {
        return storage.entries().map {
            val metadataCopy = it.metadata.toMap() // Making a defensive copy, so it's not modified outside our control.
            Credential(oidcClient, storage, it.token, metadataCopy)
        }
    }

    suspend fun fetch(filter: (metadata: Map<String, String>) -> Boolean): Credential? {
        return storage.entries().filter {
            filter(it.metadata)
        }.map {
            val metadataCopy = it.metadata.toMap() // Making a defensive copy, so it's not modified outside our control.
            Credential(oidcClient, storage, it.token, metadataCopy)
        }.firstOrNull()
    }

    suspend fun fetchOrCreate(filter: (metadata: Map<String, String>) -> Boolean): Credential {
        return storage.entries().filter {
            filter(it.metadata)
        }.map {
            val metadataCopy = it.metadata.toMap() // Making a defensive copy, so it's not modified outside our control.
            Credential(oidcClient, storage, it.token, metadataCopy)
        }.firstOrNull() ?: create()
    }
}
