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
package com.okta.testhelpers

import com.okta.authfoundation.credential.TokenStorage
import java.util.Collections

class InMemoryTokenStorage : TokenStorage {
    private val entries = Collections.synchronizedMap(mutableMapOf<String, TokenStorage.Entry>())

    override suspend fun entries(): List<TokenStorage.Entry> {
        return entries.values.toList()
    }

    override suspend fun add(id: String) {
        entries[id] = TokenStorage.Entry(id, null, emptyMap())
    }

    override suspend fun remove(id: String) {
        entries.remove(id)
    }

    override suspend fun replace(updatedEntry: TokenStorage.Entry) {
        entries[updatedEntry.identifier] = updatedEntry
    }
}
