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
package com.okta.authfoundation

import com.okta.authfoundation.client.OidcClock
import com.okta.authfoundation.credential.TokenStorage
import com.okta.authfoundation.events.EventCoordinator
import okhttp3.Call
import okhttp3.OkHttpClient
import java.util.Collections

object OktaSdk {
    var okHttpClient: Call.Factory by NoSetAfterGetWithLazyDefaultFactory { OkHttpClient() }

    var eventCoordinator: EventCoordinator by NoSetAfterGetWithLazyDefaultFactory { EventCoordinator(emptyList()) }

    var clock: OidcClock by NoSetAfterGetWithLazyDefaultFactory { defaultClock() }

    var storage: TokenStorage by NoSetAfterGetWithLazyDefaultFactory { defaultStorage() }

    private fun defaultClock(): OidcClock {
        return object : OidcClock {
            override fun currentTimeMillis(): Long {
                return System.currentTimeMillis()
            }
        }
    }

    private fun defaultStorage(): TokenStorage {
        return object : TokenStorage {
            private val entries = Collections.synchronizedList(mutableListOf<TokenStorage.Entry>())

            override suspend fun entries(): List<TokenStorage.Entry> {
                return entries
            }

            override suspend fun add(entry: TokenStorage.Entry) {
                entries += entry
            }

            override suspend fun remove(entry: TokenStorage.Entry) {
                entries -= entry
            }

            override suspend fun replace(existingEntry: TokenStorage.Entry, updatedEntry: TokenStorage.Entry) {
                val index = entries.indexOf(existingEntry)
                entries[index] = updatedEntry
            }
        }
    }
}
