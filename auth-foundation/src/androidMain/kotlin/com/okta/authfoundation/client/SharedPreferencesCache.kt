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
package com.okta.authfoundation.client

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

/**
 * An implementation of [Cache] which stores key value pairs in Android [SharedPreferences].
 */
internal class SharedPreferencesCache private constructor(
    context: Context,
) : Cache {
    internal companion object {
        internal fun getInstance() = SharedPreferencesCache(ApplicationContextHolder.appContext)

        private const val FILE_NAME = "com.okta.authfoundation.cache"
    }

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }

    @SuppressLint("ApplySharedPref")
    override fun set(
        key: String,
        value: String,
    ) {
        sharedPreferences.edit().putString(key, value).commit()
    }

    override fun get(key: String): String? = sharedPreferences.getString(key, null)

    @SuppressLint("ApplySharedPref")
    override fun clear() {
        sharedPreferences.edit().clear().commit()
    }
}
