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
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import androidx.annotation.VisibleForTesting
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.credential.events.TokenStorageAccessErrorEvent
import com.okta.authfoundation.events.EventCoordinator
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Objects
import kotlin.coroutines.CoroutineContext

/**
 * Legacy interface used to customize the way tokens are stored, updated, and removed throughout the lifecycle of an application.
 * See [TokenStorage] for the current version.
 */
interface LegacyTokenStorage {
    /**
     * Used to access all [Entry]s in storage.
     *
     *  @return all [Entry] in storage.
     */
    suspend fun entries(): List<Entry>

    /**
     *  Add a new entry to storage.
     *
     *  @param id the unique identifier related to a [LegacyTokenStorage.Entry].
     */
    suspend fun add(id: String)

    /**
     *  Remove an existing entry from storage.
     *
     *  @param id the unique identifier related to a [LegacyTokenStorage.Entry].
     */
    suspend fun remove(id: String)

    /**
     *  Replace an existing [Entry] in storage with an updated [Entry].
     *
     *  @param updatedEntry the new [Entry] to store.
     */
    suspend fun replace(updatedEntry: Entry)

    /**
     *  Represents the data to store in [LegacyTokenStorage].
     */
    class Entry(
        /**
         * The unique identifier for this [LegacyTokenStorage] entry.
         */
        val identifier: String,
        /**
         *  The [Token] associated with the [LegacyTokenStorage] entry.
         */
        val token: Token?,
        /**
         *  The tags associated with the [LegacyTokenStorage] entry.
         */
        val tags: Map<String, String>,
    ) {
        override fun equals(other: Any?): Boolean {
            if (other === this) {
                return true
            }
            if (other !is Entry) {
                return false
            }
            return other.identifier == identifier &&
                other.token == token &&
                other.tags == tags
        }

        override fun hashCode(): Int {
            return Objects.hash(
                identifier,
                token,
                tags,
            )
        }
    }
}

internal class SharedPreferencesTokenStorage(
    private val json: Json,
    private val dispatcher: CoroutineContext,
    eventCoordinator: EventCoordinator,
    context: Context,
    keyGenParameterSpec: KeyGenParameterSpec
) : LegacyTokenStorage {
    internal companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal const val FILE_NAME = "com.okta.authfoundation.storage"

        private const val PREFERENCE_KEY = "com.okta.authfoundation.storage_entries"

        internal fun createSharedPreferences(
            applicationContext: Context,
            keyGenParameterSpec: KeyGenParameterSpec
        ): SharedPreferences {
            val masterKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec)

            return EncryptedSharedPreferences.create(
                FILE_NAME,
                masterKeyAlias,
                applicationContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    private val applicationContext: Context = context.applicationContext
    private val accessLock: Any = Any()

    private val sharedPreferences: SharedPreferences by lazy {
        try {
            createSharedPreferences(applicationContext, keyGenParameterSpec)
        } catch (e: Exception) {
            val event = TokenStorageAccessErrorEvent(e, true)
            eventCoordinator.sendEvent(event)
            if (event.shouldClearStorageAndTryAgain) {
                val sharedPreferences =
                    applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
                sharedPreferences.edit().clear().commit()
                createSharedPreferences(applicationContext, keyGenParameterSpec)
            } else {
                throw e
            }
        }
    }

    override suspend fun entries(): List<LegacyTokenStorage.Entry> {
        var entries: List<LegacyTokenStorage.Entry> = emptyList()
        accessStorage { existingEntries ->
            entries = existingEntries
            existingEntries
        }
        return entries
    }

    override suspend fun add(id: String) {
        accessStorage { existingEntries ->
            existingEntries += LegacyTokenStorage.Entry(id, null, emptyMap())
            existingEntries
        }
    }

    override suspend fun remove(id: String) {
        accessStorage { existingEntries ->
            existingEntries.indexOfFirst {
                it.identifier == id
            }.takeIf { it >= 0 }?.let { index ->
                existingEntries.removeAt(index)
            }
            existingEntries
        }
    }

    override suspend fun replace(updatedEntry: LegacyTokenStorage.Entry) {
        accessStorage { existingEntries ->
            existingEntries.indexOfFirst {
                it.identifier == updatedEntry.identifier
            }.takeIf { it >= 0 }?.let { index ->
                existingEntries[index] = updatedEntry
            }
            existingEntries
        }
    }

    private suspend fun accessStorage(
        block: (MutableList<LegacyTokenStorage.Entry>) -> List<LegacyTokenStorage.Entry>
    ) {
        withContext(dispatcher) {
            synchronized(accessLock) {
                val existingJson = sharedPreferences.getString(PREFERENCE_KEY, null)
                val existingEntries: MutableList<LegacyTokenStorage.Entry> =
                    if (existingJson == null) {
                        mutableListOf()
                    } else {
                        json.decodeFromString(StoredTokens.serializer(), existingJson)
                            .toTokenStorageEntries()
                    }
                val updatedEntries = block(existingEntries)
                val updatedJson = json.encodeToString(
                    StoredTokens.serializer(),
                    StoredTokens.from(updatedEntries)
                )
                val editor = sharedPreferences.edit()
                editor.putString(PREFERENCE_KEY, updatedJson)
                editor.commit()
            }
        }
    }
}

@Serializable
private class StoredTokens(
    @SerialName("entries") val entries: List<Entry>
) {
    @Serializable
    class Entry(
        @SerialName("identifier") val identifier: String,
        @SerialName("token") val token: SerializableToken?,
        @SerialName("tags") val tags: Map<String, String>,
    )

    companion object {
        fun from(entries: List<LegacyTokenStorage.Entry>): StoredTokens {
            return StoredTokens(
                entries.map {
                    Entry(
                        it.identifier,
                        it.token?.asSerializableToken(),
                        it.tags
                    )
                }
            )
        }
    }

    fun toTokenStorageEntries(): MutableList<LegacyTokenStorage.Entry> {
        return entries.map {
            LegacyTokenStorage.Entry(
                it.identifier,
                it.token?.asToken(id = it.identifier, OidcConfiguration.default),
                it.tags
            )
        }.toMutableList()
    }
}
