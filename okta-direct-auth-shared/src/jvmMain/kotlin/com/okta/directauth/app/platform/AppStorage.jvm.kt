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
package com.okta.directauth.app.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.prefs.Preferences

actual class AppStorage {
    private val prefs: Preferences = Preferences.userRoot().node("directauth_app_prefs")
    private val cache = mutableMapOf<String, MutableStateFlow<String>>()

    private fun flowForKey(key: String): MutableStateFlow<String> = cache.getOrPut(key) { MutableStateFlow(prefs.get(key, "")) }

    actual fun get(key: String): Flow<String> = flowForKey(key).map { it }

    actual suspend fun save(
        key: String,
        value: String,
    ) {
        prefs.put(key, value)
        prefs.flush()
        flowForKey(key).value = value
    }

    actual suspend fun clear(key: String) {
        prefs.remove(key)
        prefs.flush()
        flowForKey(key).value = ""
    }
}
