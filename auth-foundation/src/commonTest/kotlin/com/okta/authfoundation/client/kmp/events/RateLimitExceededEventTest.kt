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
package com.okta.authfoundation.client.kmp.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RateLimitExceededEventTest {
    @Test
    fun constructor_SetsAllFields() {
        val headers = mapOf("Retry-After" to listOf("30"))
        val event =
            RateLimitExceededEvent(
                requestUrl = "https://example.okta.com/token",
                responseHeaders = headers,
                retryAfterSeconds = 30L
            )
        assertEquals("https://example.okta.com/token", event.requestUrl)
        assertEquals(headers, event.responseHeaders)
        assertEquals(30L, event.retryAfterSeconds)
    }

    @Test
    fun retryAfterSeconds_NullWhenHeaderAbsent() {
        val event =
            RateLimitExceededEvent(
                requestUrl = "https://example.okta.com/token",
                responseHeaders = emptyMap(),
                retryAfterSeconds = null
            )
        assertNull(event.retryAfterSeconds)
    }
}
