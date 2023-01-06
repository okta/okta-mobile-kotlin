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
package com.okta.authfoundationbootstrap

import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.credential.Credential
import com.okta.authfoundation.credential.CredentialDataSource
import com.okta.authfoundation.util.CoalescingOrchestrator

/**
 * [CredentialBootstrap] implements best practices for single [Credential] use cases.
 *
 * Other [Credential]s can be created via the associated [CredentialBootstrap.credentialDataSource] property.
 *
 * [CredentialBootstrap] must be initialized via [CredentialBootstrap.initialize] before accessing any properties or methods.
 */
object CredentialBootstrap {
    private const val CREDENTIAL_NAME_TAG_KEY: String = "com.okta.kotlin.credential.name"
    private const val DEFAULT_CREDENTIAL_NAME_TAG_VALUE: String = "Default"
    @Volatile private var privateCredentialDataSource: CredentialDataSource? = null

    private val credentialCoalescingOrchestrator = CoalescingOrchestrator(
        factory = ::realFetchCredential,
        keepDataInMemory = { false },
    )

    /**
     * The singleton [CredentialDataSource].
     */
    val credentialDataSource: CredentialDataSource
        get() {
            return privateCredentialDataSource
                ?: throw IllegalStateException("CredentialBoostrap not initialized. Please call initialize before attempting to access properties and methods.")
        }

    /**
     * The singleton [OidcClient].
     */
    val oidcClient: OidcClient
        get() {
            return credentialDataSource.oidcClient
        }

    /**
     * Initializes [CredentialBootstrap] with a [CredentialDataSource].
     *
     * @param credentialDataSource the [CredentialDataSource] to associate with [CredentialBootstrap] as a singleton.
     */
    fun initialize(credentialDataSource: CredentialDataSource) {
        if (privateCredentialDataSource != null) {
            throw IllegalStateException("Credential bootstrap was already initialized.")
        }
        privateCredentialDataSource = credentialDataSource
    }

    /**
     * The default [Credential] associated with the associated [CredentialDataSource].
     *
     * This will get the default [Credential] if one exists, or create the default [Credential] is there is not an existing one.
     */
    suspend fun defaultCredential(): Credential {
        return credentialCoalescingOrchestrator.get()
    }

    private suspend fun realFetchCredential(): Credential {
        return credentialDataSource.listCredentials().firstOrNull { credential ->
            credential.tags[CREDENTIAL_NAME_TAG_KEY] == DEFAULT_CREDENTIAL_NAME_TAG_VALUE
        } ?: createDefaultCredential()
    }

    private suspend fun createDefaultCredential(): Credential {
        val credential = credentialDataSource.createCredential()
        credential.storeToken(
            tags = mapOf(
                Pair(
                    CREDENTIAL_NAME_TAG_KEY,
                    DEFAULT_CREDENTIAL_NAME_TAG_VALUE
                )
            )
        )
        return credential
    }

    internal fun reset() {
        privateCredentialDataSource = null
    }
}
