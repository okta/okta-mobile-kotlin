/*
 * Copyright 2021-Present Okta, Inc.
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

import com.okta.authfoundation.AuthFoundationDefaults
import java.util.Objects

/**
 * Interface used to customize the way tokens are stored, updated, and removed throughout the lifecycle of an application.
 *
 * A default implementation is provided, but for advanced use-cases, you may implement this protocol yourself and assign an instance to the [AuthFoundationDefaults.storage] property.
 *
 * Warning: When implementing a custom [TokenStorage] class, it's vitally important that you do not directly invoke any of these methods yourself. These methods are intended to be called on-demand by the other AuthFoundation classes, and the behavior is undefined if these methods are called directly by the developer.
 */
interface TokenStorage {
    /**
     * Used to access all [Entry]s in storage.
     *
     *  @return all [Entry] in storage.
     */
    suspend fun entries(): List<Entry>

    /**
     *  Add a new entry to storage.
     *
     *  @param id the unique identifier related to a [TokenStorage.Entry].
     */
    suspend fun add(id: String)

    /**
     *  Remove an existing entry from storage.
     *
     *  @param id the unique identifier related to a [TokenStorage.Entry].
     */
    suspend fun remove(id: String)

    /**
     *  Replace an existing [Entry] in storage with an updated [Entry].
     *
     *  @param updatedEntry the new [Entry] to store.
     */
    suspend fun replace(updatedEntry: Entry)

    /**
     *  Represents the data to store in [TokenStorage].
     */
    class Entry(
        /**
         * The unique identifier for this [TokenStorage] entry.
         */
        val identifier: String,
        /**
         *  The [Token] associated with the [TokenStorage] entry.
         */
        val token: Token?,
        /**
         *  The tags associated with the [TokenStorage] entry.
         */
        val tags: Map<String, String>,
    ) {
        override fun equals(other: Any?): Boolean {
            if (other === this) {
                return true
            }
            if (other !is Entry) {
                return false
            }
            return other.identifier == identifier &&
                other.token == token &&
                other.tags == tags
        }

        override fun hashCode(): Int {
            return Objects.hash(
                identifier,
                token,
                tags,
            )
        }
    }
}
