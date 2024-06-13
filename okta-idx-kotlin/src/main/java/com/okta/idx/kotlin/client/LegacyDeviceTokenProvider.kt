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
package com.okta.idx.kotlin.client

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.util.UUID

internal class LegacyDeviceTokenProvider(private val appContext: Context) {
    internal companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal const val FILE_NAME = "com.okta.authfoundation.device_token_storage"
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal const val PREFERENCE_KEY = "com.okta.authfoundation.device_token_key"
    }

    private val masterKeyAlias by lazy { MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC) }

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

    private val sharedPrefsEditor by lazy { sharedPrefs.edit() }

    internal val deviceTokenUUID: String
        get() {
            return sharedPrefs.getString(PREFERENCE_KEY, null) ?: run {
                val newDeviceToken = UUID.randomUUID().toString()
                sharedPrefsEditor.putString(PREFERENCE_KEY, newDeviceToken)
                sharedPrefsEditor.commit()
                newDeviceToken
            }
        }

    internal fun containsDeviceToken(): Boolean = sharedPrefs.contains(PREFERENCE_KEY)

    internal val deviceToken: String
        get() = deviceTokenUUID.filter { it.isLetterOrDigit() }
}
