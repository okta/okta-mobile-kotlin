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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In memory thread safe implementation of [AuthFoundationCache]. This is mainly used to cache the well-known/openid-configuration responses.
 * Sensitive data is not stored with this cache.
 */
class LocalInMemoryCache : AuthFoundationCache {
    private val mutex = Mutex()
    private val map = mutableMapOf<String, String>()

    /**
     * Stores a key-value pair in the cache.
     *
     * @param key The cache key.
     * @param value The value to store.
     */
    override suspend fun set(
        key: String,
        value: String,
    ) {
        mutex.withLock { map[key] = value }
    }

    /**
     * Retrieves a value from the cache.
     *
     * @param key The cache key.
     * @return The cached value, or null if not found.
     */
    override suspend fun get(key: String): String? = mutex.withLock { map[key] }

    /**
     * Clears all entries from the cache.
     */
    override suspend fun clear() {
        mutex.withLock { map.clear() }
    }
}
