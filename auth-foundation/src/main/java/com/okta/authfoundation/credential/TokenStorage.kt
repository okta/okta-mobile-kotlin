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

import com.okta.authfoundation.OktaSdkDefaults

/**
 * Interface used to customize the way tokens are stored, updated, and removed throughout the lifecycle of an application.
 *
 * A default implementation is provided, but for advanced use-cases, you may implement this protocol yourself and assign an instance to the [OktaSdkDefaults.storage] property.
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
     *  Add a new [Entry] to storage.
     *
     *  @param entry the [Entry] to add.
     */
    suspend fun add(entry: Entry)

    /**
     *  Remove an existing [Entry] from storage.
     *
     *  @param entry the [Entry] to remove.
     */
    suspend fun remove(entry: Entry)

    /**
     *  Replace an existing [Entry] in storage with a new [Entry].
     *
     *  @param existingEntry the existing [Entry] in storage.
     *  @param updatedEntry the new [Entry] to store.
     */
    suspend fun replace(existingEntry: Entry, updatedEntry: Entry)

    /**
     *  Represents the data to store in [TokenStorage].
     */
    data class Entry(
        /**
         *  The [Token] associated with the [TokenStorage] entry.
         */
        val token: Token,
        /**
         *  The metadata associated with the [TokenStorage] entry.
         */
        val metadata: Map<String, String>,
    )
}
