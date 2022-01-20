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
import com.okta.authfoundation.client.OidcStorage
import com.okta.authfoundation.events.EventCoordinator
import okhttp3.Call
import okhttp3.OkHttpClient

object OktaSdk {
    var okHttpClient: Call.Factory by NoSetAfterGetWithLazyDefaultFactory { OkHttpClient() }

    var eventCoordinator: EventCoordinator by NoSetAfterGetWithLazyDefaultFactory { EventCoordinator(emptyList()) }

    var clock: OidcClock by NoSetAfterGetWithLazyDefaultFactory { defaultClock() }

    var storage: OidcStorage by NoSetAfterGetWithLazyDefaultFactory { defaultStorage() }

    private fun defaultClock(): OidcClock {
        return object : OidcClock {
            override fun currentTimeMillis(): Long {
                return System.currentTimeMillis()
            }
        }
    }

    private fun defaultStorage(): OidcStorage {
        val map = mutableMapOf<String, String>()
        return object : OidcStorage {
            override suspend fun save(key: String, value: String) {
                map[key] = value
            }

            override suspend fun get(key: String): String? {
                return map[key]
            }

            override suspend fun delete(key: String) {
                map.remove(key)
            }
        }
    }
}
