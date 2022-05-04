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
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.okta.authfoundation.credential.events.TokenStorageAccessErrorEvent
import com.okta.authfoundation.events.EventCoordinator
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

@RequiresApi(Build.VERSION_CODES.M)
internal class SharedPreferencesTokenStorage(
    private val json: Json,
    private val dispatcher: CoroutineContext,
    eventCoordinator: EventCoordinator,
    context: Context,
) : TokenStorage {
    internal companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal const val FILE_NAME = "com.okta.authfoundation.storage"

        private const val PREFERENCE_KEY = "com.okta.authfoundation.storage_entries"

        private fun createSharedPreferences(applicationContext: Context): SharedPreferences {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

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
            createSharedPreferences(applicationContext)
        } catch (e: Exception) {
            val event = TokenStorageAccessErrorEvent(e, true)
            eventCoordinator.sendEvent(event)
            if (event.shouldClearStorageAndTryAgain) {
                val sharedPreferences = applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
                sharedPreferences.edit().clear().commit()
                createSharedPreferences(applicationContext)
            } else {
                throw e
            }
        }
    }

    override suspend fun entries(): List<TokenStorage.Entry> {
        var entries: List<TokenStorage.Entry> = emptyList()
        accessStorage { existingEntries ->
            entries = existingEntries
            existingEntries
        }
        return entries
    }

    override suspend fun add(id: String) {
        accessStorage { existingEntries ->
            existingEntries += TokenStorage.Entry(id, null, emptyMap())
            existingEntries
        }
    }

    override suspend fun remove(id: String) {
        accessStorage { existingEntries ->
            val index = existingEntries.indexOfFirst { it.identifier == id }
            existingEntries.removeAt(index)
            existingEntries
        }
    }

    override suspend fun replace(updatedEntry: TokenStorage.Entry) {
        accessStorage { existingEntries ->
            val index = existingEntries.indexOfFirst { it.identifier == updatedEntry.identifier }
            existingEntries[index] = updatedEntry
            existingEntries
        }
    }

    private suspend fun accessStorage(
        block: (MutableList<TokenStorage.Entry>) -> List<TokenStorage.Entry>
    ) {
        withContext(dispatcher) {
            synchronized(accessLock) {
                val existingJson = sharedPreferences.getString(PREFERENCE_KEY, null)
                val existingEntries: MutableList<TokenStorage.Entry> = if (existingJson == null) {
                    mutableListOf()
                } else {
                    json.decodeFromString(StoredTokens.serializer(), existingJson).toTokenStorageEntries()
                }
                val updatedEntries = block(existingEntries)
                val updatedJson = json.encodeToString(StoredTokens.serializer(), StoredTokens.from(updatedEntries))
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
        fun from(entries: List<TokenStorage.Entry>): StoredTokens {
            return StoredTokens(entries.map { Entry(it.identifier, it.token?.asSerializableToken(), it.tags) })
        }
    }

    fun toTokenStorageEntries(): MutableList<TokenStorage.Entry> {
        return entries.map { TokenStorage.Entry(it.identifier, it.token?.asToken(), it.tags) }.toMutableList()
    }
}
