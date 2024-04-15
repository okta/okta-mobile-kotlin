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
package com.okta.authfoundation.credential

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.okta.authfoundation.client.ApplicationContextHolder
import com.okta.authfoundation.util.AesEncryptionHandler
import kotlinx.coroutines.flow.firstOrNull

internal class DefaultTokenIdDataStore(
    private val aesEncryptionHandler: AesEncryptionHandler = AesEncryptionHandler()
) {
    companion object {
        const val PREFERENCE_NAME = "com.okta.authfoundation.credential.defaultTokenIdDataStore"
        val PREFERENCE_KEY = stringPreferencesKey("encryptedDefaultTokenId")
    }

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(PREFERENCE_NAME)
    private val context by lazy { ApplicationContextHolder.appContext }

    suspend fun getDefaultTokenId(): String? {
        val encryptedDefaultTokenId = context.dataStore.data.firstOrNull()?.get(PREFERENCE_KEY)
        return encryptedDefaultTokenId?.let {
            aesEncryptionHandler.decryptString(it)
        }
    }

    suspend fun setDefaultTokenId(id: String) = context.dataStore.edit { preferences ->
        preferences[PREFERENCE_KEY] = aesEncryptionHandler.encryptString(id)
    }
}
