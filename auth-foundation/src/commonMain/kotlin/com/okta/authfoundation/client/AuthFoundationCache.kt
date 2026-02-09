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

/**
 * A general purpose key value cache used internally by the SDK to optimize network calls.
 * Will not be used to store sensitive information.
 */
interface AuthFoundationCache {
    /**
     * Save a value with the associated key.
     *
     * @param key the key used to look up the value at a later time.
     * @param value the value to store.
     */
    suspend fun set(
        key: String,
        value: String,
    )

    /**
     * Look up the key that was previously saved.
     *
     * @param key the key used to store a value previously.
     * @return the associated value if the key exists, null otherwise.
     */
    suspend fun get(key: String): String?

    /**
     * Clears all entries from the cache.
     */
    suspend fun clear()
}
