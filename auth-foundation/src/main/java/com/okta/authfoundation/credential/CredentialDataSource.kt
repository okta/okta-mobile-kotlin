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

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.room.Room
import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.client.ApplicationContextHolder
import com.okta.authfoundation.client.DeviceTokenProvider
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.credential.events.CredentialCreatedEvent
import com.okta.authfoundation.credential.storage.TokenDatabase
import com.okta.authfoundation.jwt.JwtParser
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.util.Collections
import java.util.UUID

/**
 * Responsible for managing [Credential] instances.
 *
 * This is intended to be held as a singleton, and used throughout the lifecycle of the application.
 */
class CredentialDataSource internal constructor(
    @property:InternalAuthFoundationApi val oidcClient: OidcClient,
    private val storage: TokenStorage,
) {
    companion object {
        /**
         * Initializes a credential data source using the [OidcClient].
         *
         * @param storage the [TokenStorage] used to persist [Token]s.
         * @receiver the [OidcClient] used to perform the low level OIDC requests, as well as with which to use the configuration from.
         */
        fun OidcClient.createCredentialDataSource(
            storage: TokenStorage,
        ): CredentialDataSource {
            return CredentialDataSource(this, storage)
        }

        /**
         * Initializes a credential data source using the [OidcClient].
         *
         * @param context the [Context] used to access Android Shared Preferences and crypto primitives to persist [Token]s.
         * @receiver the [OidcClient] used to perform the low level OIDC requests, as well as with which to use the configuration from.
         */
        @JvmOverloads
        fun OidcClient.createCredentialDataSource(
            context: Context,
            tokenEncryptionHandler: TokenEncryptionHandler = DefaultTokenEncryptionHandler()
        ): CredentialDataSource {
            ApplicationContextHolder.setApplicationContext(context.applicationContext)
            val sqlCipherPassword = runBlocking { DeviceTokenProvider.instance.getDeviceToken() }
            System.loadLibrary("sqlcipher")
            val tokenDatabase =
                Room.databaseBuilder(context, TokenDatabase::class.java, TokenDatabase.DB_NAME)
                    .openHelperFactory(SupportOpenHelperFactory(sqlCipherPassword.toByteArray()))
                    .build()
            val storage = RoomTokenStorage(tokenDatabase, tokenEncryptionHandler)
            return CredentialDataSource(this, storage)
        }
    }

    private val jwtParser = JwtParser.create()

    private val credentialsCache = Collections.synchronizedMap(mutableMapOf<String, Credential>())

    /**
     * Returns IDs of all available [Credential] objects in storage.
     */
    suspend fun allIds() = storage.allIds()

    /**
     * Returns [Token.Metadata] of [Credential] with specified [id].
     *
     * @param id The identifier of the [Credential].
     */
    suspend fun metadata(id: String) = storage.metadata(id)

    /**
     * Creates a new [Credential] with specified options and returns it.
     *
     * @param token The [Token] to store in the newly created [Credential].
     * @param tags A map of additional values to store in [Credential].
     * @param security The [Credential.Security] level to encrypt the stored [token] with.
     * @param isDefault Specify whether the newly created [Credential] should be set as the default [Credential].
     */
    suspend fun createCredential(
        token: Token,
        tags: Map<String, String> = emptyMap(),
        security: Credential.Security = Credential.Security.standard,
        isDefault: Boolean = false,
    ): Credential {
        val storageIdentifier = UUID.randomUUID().toString()
        val idToken = token.idToken?.let { jwtParser.parse(it) }
        val credential =
            Credential(oidcClient, this, storageIdentifier, token, tags)
        credentialsCache[storageIdentifier] = credential
        storage.add(
            token,
            Token.Metadata(
                storageIdentifier,
                tags,
                idToken?.deserializeClaims(JsonObject.serializer()),
                isDefault
            ),
            security
        )
        oidcClient.configuration.eventCoordinator.sendEvent(CredentialCreatedEvent(credential))
        return credential
    }

    /**
     * Replaces the stored values of [Credential] associated with [id], and returns the [Credential] with updated values.
     *
     * @param id The id of the stored [Credential].
     * @param token The token object to store.
     * @param tags The new tags for replacing the stored tags of this [Credential]. If null, keep the previous stored tags.
     * @param security The new [Credential.Security] level for this token. If null, keep the previous stored [Credential.Security] level.
     * @param isDefault Specify whether the [Credential] with [id] should be set as default. If null, the default [Credential] is unchanged.
     */
    suspend fun replaceCredential(
        id: String,
        token: Token,
        tags: Map<String, String>? = null,
        security: Credential.Security? = null,
        isDefault: Boolean? = null
    ): Credential {
        if (id !in allIds()) {
            throw IllegalArgumentException("Can't replace non-existing token with id: $id")
        }
        val credential = credentialsCache[id] ?: Credential(oidcClient, this, id, token, tags ?: emptyMap())
        credential.storeToken(token, security, tags, isDefault)
        credentialsCache[id] = credential
        return credential
    }

    internal suspend fun internalReplaceCredential(
        id: String,
        token: Token,
        tags: Map<String, String>,
        security: Credential.Security?,
        isDefault: Boolean?
    ) {
        val idToken = token.idToken?.let { jwtParser.parse(it) }
        val payloadData = idToken?.deserializeClaims(JsonObject.serializer())
        val previousMetadata = metadata(id) ?: throw IllegalStateException("Token metadata for $id not found")
        val newMetadata = previousMetadata.copy(
            tags = tags,
            payloadData = payloadData,
            isDefault = isDefault ?: previousMetadata.isDefault
        )
        storage.replace(id, token, newMetadata, security)
    }

    /**
     * Return the [Credential] associated with the given [id].
     *
     * @param id The id of the [Credential] to fetch.
     * @param promptInfo The [BiometricPrompt.PromptInfo] for displaying biometric prompt. A non-null value is required if the [Credential] with [id] is stored using a biometric [Credential.Security].
     */
    suspend fun getCredential(id: String, promptInfo: BiometricPrompt.PromptInfo? = Credential.Security.promptInfo): Credential? {
        return if (id in credentialsCache) credentialsCache[id]
        else {
            val metadata = metadata(id) ?: return null
            val token = storage.getToken(id, promptInfo)
            credentialsCache[id] = Credential(
                oidcClient,
                this,
                metadata.id,
                token,
                metadata.tags
            )
            return credentialsCache[id]
        }
    }

    /**
     * Return all [Credential] objects matching the given [where] expression. The [where] expression is supplied with [Token.Metadata] and should return true for cases where the user wants to fetch [Credential] with given [Token.Metadata].
     *
     * @param promptInfo The [BiometricPrompt.PromptInfo] for displaying biometric prompt. A non-null value is required if a fetched [Credential] is stored using a biometric [Credential.Security].
     * @param where A function specifying whether a [Credential] with [Token.Metadata] should be fetched. This function should return true for [Credential] with [Token.Metadata] that should be retrieved from storage.
     */
    suspend fun findCredential(
        promptInfo: BiometricPrompt.PromptInfo? = Credential.Security.promptInfo,
        where: (Token.Metadata) -> Boolean
    ): List<Credential> {
        return allIds()
            .mapNotNull { metadata(it) }
            .filter { where(it) }
            .mapNotNull { metadata ->
                getCredential(metadata.id, promptInfo)
            }
    }

    /**
     * Return true if this [CredentialDataSource] contains a default [Credential], otherwise return false.
     */
    suspend fun containsDefaultCredential(): Boolean {
        return allIds().any { metadata(it)?.isDefault == true }
    }

    internal suspend fun remove(credential: Credential) {
        credentialsCache.remove(credential.storageIdentifier)
        storage.remove(credential.storageIdentifier)
    }
}
