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
import com.okta.authfoundation.AuthFoundationDefaults
import com.okta.authfoundation.InternalAuthFoundationApi

/**
 * A general purpose key value cache used internally by the SDK to optimize network calls.
 * Will not be used to store sensitive information.
 *
 * See [AuthFoundationDefaults.cacheFactory].
 */
interface Cache {
    /**
     * Save a value with the associated key.
     *
     * @param key the key used to lookup the value at a later time.
     * @param value the value to store.
     */
    fun set(key: String, value: String)

    /**
     * Look up the key that was previously saved.
     *
     * @param key the key used to store a value previously.
     * @return the associated value if the key exists, null otherwise.
     */
    fun get(key: String): String?
}

/**
 * An implementation of [Cache] which stores key value pairs in Android [SharedPreferences].
 */
internal class SharedPreferencesCache private constructor(context: Context) : Cache {
    internal companion object {
        internal fun getInstance() = SharedPreferencesCache(ApplicationContextHolder.appContext)
        private const val FILE_NAME = "com.okta.authfoundation.cache"
    }

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }

    @SuppressLint("ApplySharedPref")
    override fun set(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).commit()
    }

    override fun get(key: String): String? {
        return sharedPreferences.getString(key, null)
    }
}

@InternalAuthFoundationApi
class NoOpCache : Cache {
    override fun set(key: String, value: String) {
    }

    override fun get(key: String): String? {
        return null
    }
}
