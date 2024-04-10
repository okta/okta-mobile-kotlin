/*
 * Copyright 2024-Present Okta, Inc.
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
package com.okta.authfoundation.credential.storage.migration

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.MasterKeys
import com.okta.authfoundation.AuthFoundationDefaults
import com.okta.authfoundation.NoSetAfterGetWithLazyDefaultFactory
import com.okta.authfoundation.client.ApplicationContextHolder
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.credential.DefaultCredentialIdDataStore
import com.okta.authfoundation.credential.LegacyTokenStorage
import com.okta.authfoundation.credential.SharedPreferencesTokenStorage
import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.credential.TokenStorage
import com.okta.authfoundation.jwt.JwtParser
import kotlinx.coroutines.flow.firstOrNull

class V1ToV2StorageMigrator internal constructor(
    private val tokenStorage: TokenStorage
) {
    companion object {
        private const val PREFERENCE_NAME = "com.okta.authfoundation.credential.storage.isStorageMigratedFromV1"
        private val MIGRATED = booleanPreferencesKey("migrated")
        internal const val LEGACY_CREDENTIAL_NAME_TAG_KEY: String = "com.okta.kotlin.credential.name"
        internal const val LEGACY_DEFAULT_CREDENTIAL_NAME_TAG_VALUE: String = "Default"
        var legacyKeyGenParameterSpec: KeyGenParameterSpec by NoSetAfterGetWithLazyDefaultFactory {
            MasterKeys.AES256_GCM_SPEC
        }
        var legacyStorage: LegacyTokenStorage by NoSetAfterGetWithLazyDefaultFactory {
            legacyStorageWith(legacyKeyGenParameterSpec)
        }

        private fun legacyStorageWith(keyGenParameterSpec: KeyGenParameterSpec): SharedPreferencesTokenStorage {
            return SharedPreferencesTokenStorage(
                OidcConfiguration.defaultJson(),
                AuthFoundationDefaults.ioDispatcher,
                AuthFoundationDefaults.eventCoordinator,
                ApplicationContextHolder.appContext,
                keyGenParameterSpec
            )
        }
    }

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(PREFERENCE_NAME)
    private val context by lazy { ApplicationContextHolder.appContext }
    private val defaultCredentialIdDataStore by lazy { DefaultCredentialIdDataStore.instance }
    private val jwtParser = JwtParser.create()

    internal suspend fun migrateIfNeeded() {
        if (isMigrationNeeded()) migrate()
        context.dataStore.edit { preferences ->
            preferences[MIGRATED] = true
        }
    }

    internal suspend fun isMigrationNeeded(): Boolean {
        return context.dataStore.data.firstOrNull()?.get(MIGRATED)?.not() ?: true
    }

    private suspend fun migrate() {
        val legacyEntries = try {
            legacyStorage.entries()
        } catch (e: Exception) {
            emptyList()
        }

        for (entry in legacyEntries) {
            if (entry.token == null) continue
            val idToken = entry.token.idToken?.let { jwtParser.parse(it) }
            val metadata = Token.Metadata(entry.identifier, entry.tags, idToken)
            tokenStorage.add(entry.token, metadata)
            if (entry.isDefaultTokenEntry()) {
                defaultCredentialIdDataStore.setDefaultCredentialId(entry.identifier)
            }
        }
    }

    private fun LegacyTokenStorage.Entry.isDefaultTokenEntry(): Boolean {
        return tags[LEGACY_CREDENTIAL_NAME_TAG_KEY] == LEGACY_DEFAULT_CREDENTIAL_NAME_TAG_VALUE
    }
}
