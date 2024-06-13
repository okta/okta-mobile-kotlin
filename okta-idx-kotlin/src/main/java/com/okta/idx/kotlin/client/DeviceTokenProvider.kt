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
package com.okta.authfoundation.client

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.util.AesEncryptionHandler
import com.okta.idx.kotlin.client.LegacyDeviceTokenProvider
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

@InternalAuthFoundationApi
class DeviceTokenProvider(
    private val aesEncryptionHandler: AesEncryptionHandler = AesEncryptionHandler()
) {
    companion object {
        const val PREFERENCE_NAME = "com.okta.idx.client.deviceToken"
        val PREFERENCE_KEY = stringPreferencesKey("encryptedDeviceToken")
        val instance by lazy { DeviceTokenProvider() }
    }

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(PREFERENCE_NAME)
    private val context by lazy { ApplicationContextHolder.appContext }

    suspend fun getDeviceToken(): String {
        val encryptedDeviceToken = context.dataStore.data.firstOrNull()?.get(PREFERENCE_KEY)
        return encryptedDeviceToken?.let {
            aesEncryptionHandler.decryptString(it)
        } ?: run {
            val deviceToken = getLegacyDeviceToken() ?: createNewDeviceToken()
            setDeviceToken(deviceToken)
            deviceToken
        }
    }

    private fun getLegacyDeviceToken(): String? {
        return try {
            val legacyDeviceTokenProvider = LegacyDeviceTokenProvider(context)
            if (legacyDeviceTokenProvider.containsDeviceToken()) {
                legacyDeviceTokenProvider.deviceToken
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun createNewDeviceToken(): String =
        UUID.randomUUID().toString().filter { it.isLetterOrDigit() }

    private suspend fun setDeviceToken(deviceToken: String) =
        context.dataStore.edit { preferences ->
            preferences[PREFERENCE_KEY] = aesEncryptionHandler.encryptString(deviceToken)
        }
}
