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
import androidx.annotation.VisibleForTesting
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.util.UUID

class DeviceTokenProvider private constructor(appContext: Context) {
    internal companion object {
        private const val FILE_NAME = "com.okta.authfoundation.device_token_storage"
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal const val PREFERENCE_KEY = "com.okta.authfoundation.device_token_key"

        private val lock = Any()
        private lateinit var instance: DeviceTokenProvider
        internal val deviceToken: String
            get() = instance.deviceToken.filter { it.isLetterOrDigit() }

        internal fun initialize(context: Context): DeviceTokenProvider {
            synchronized(lock) {
                if (::instance.isInitialized) return instance
                instance = DeviceTokenProvider(context.applicationContext)
                return instance
            }
        }
    }

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val sharedPrefs = EncryptedSharedPreferences.create(
        FILE_NAME,
        masterKeyAlias,
        appContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val sharedPrefsEditor = sharedPrefs.edit()

    private val deviceToken = sharedPrefs.getString(PREFERENCE_KEY, null) ?: run {
        val newDeviceToken = UUID.randomUUID().toString()
        sharedPrefsEditor.putString(PREFERENCE_KEY, newDeviceToken)
        sharedPrefsEditor.commit()
        newDeviceToken
    }
}
