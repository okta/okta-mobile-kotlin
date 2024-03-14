/*
 * Copyright 2023-Present Okta, Inc.
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
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.runBlocking
import java.util.UUID

internal class DeviceTokenProvider(private val appContext: Context) {
    internal companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal const val FILE_NAME = "com.okta.authfoundation.device_token_storage"
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal const val PREFERENCE_KEY = "com.okta.authfoundation.device_token_key"

        internal val instance: DeviceTokenProvider by lazy {
            val appContext = runBlocking { ApplicationContextHolder.getApplicationContext() }
            DeviceTokenProvider(appContext)
        }

        internal val deviceToken: String
            get() = instance.deviceToken.filter { it.isLetterOrDigit() }
    }

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private fun createSharedPreferences(): SharedPreferences {
        return EncryptedSharedPreferences.create(
            FILE_NAME,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val sharedPrefs: SharedPreferences by lazy {
        try {
            createSharedPreferences()
        } catch (e: Exception) {
            val sharedPreferences = appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            sharedPreferences.edit().clear().commit()
            createSharedPreferences()
        }
    }

    private val sharedPrefsEditor = sharedPrefs.edit()

    internal val deviceToken: String
        get() {
            val tokenUUID = sharedPrefs.getString(PREFERENCE_KEY, null) ?: run {
                val newDeviceToken = UUID.randomUUID().toString()
                sharedPrefsEditor.putString(PREFERENCE_KEY, newDeviceToken)
                sharedPrefsEditor.commit()
                newDeviceToken
            }
            return tokenUUID.filter { it.isLetterOrDigit() }
        }
}
