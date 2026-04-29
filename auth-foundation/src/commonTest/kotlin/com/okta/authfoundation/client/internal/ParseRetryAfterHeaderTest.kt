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
package com.okta.authfoundation.client.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParseRetryAfterHeaderTest {
    // Fixed "now" for deterministic delay calculations: 2015-10-21T07:00:00Z = 1445410800
    private val now = 1445410800L

    @Test
    fun nullInput() {
        assertNull(parseRetryAfterHeader(null, now))
    }

    @Test
    fun blankInput() {
        assertNull(parseRetryAfterHeader("  ", now))
    }

    @Test
    fun delaySeconds() {
        assertEquals(120L, parseRetryAfterHeader("120", now))
    }

    @Test
    fun delaySecondsZero() {
        assertEquals(0L, parseRetryAfterHeader("0", now))
    }

    @Test
    fun unparseable() {
        assertNull(parseRetryAfterHeader("not-a-date", now))
    }

    // -----------------------------------------------------------------------
    // IMF-fixdate: "Wed, 21 Oct 2015 07:28:00 GMT" — RFC 7231 preferred format
    // -----------------------------------------------------------------------

    @Test
    fun imfFixdate() {
        // Target = 2015-10-21T07:28:00Z = 1445412480, delay = 1445412480 - 1445410800 = 1680
        assertEquals(1680L, parseRetryAfterHeader("Wed, 21 Oct 2015 07:28:00 GMT", now))
    }

    @Test
    fun imfFixdateInThePast() {
        // Target before "now" → delay clamped to 0
        assertEquals(0L, parseRetryAfterHeader("Wed, 21 Oct 2015 06:40:00 GMT", now))
    }

    @Test
    fun imfFixdateNoDayOfWeek() {
        // RFC_1123 allows omitting the day-of-week
        assertEquals(1680L, parseRetryAfterHeader("21 Oct 2015 07:28:00 GMT", now))
    }

    @Test
    fun imfFixdateWithUtcOffset() {
        // "+0000" offset is semantically the same as "GMT"
        assertEquals(1680L, parseRetryAfterHeader("Wed, 21 Oct 2015 07:28:00 +0000", now))
    }

    // -----------------------------------------------------------------------
    // RFC 850 (obsolete): "Wednesday, 21-Oct-15 07:28:00 GMT"
    // -----------------------------------------------------------------------

    @Test
    fun rfc850Date() {
        assertEquals(1680L, parseRetryAfterHeader("Wednesday, 21-Oct-15 07:28:00 GMT", now))
    }

    @Test
    fun rfc850DateYear00to69MapsTo2000s() {
        // 2-digit year 25 → 2025; target = 2025-10-21T07:28:00Z = 1761031680
        // Delay = 1761031680 - 1445410800 = 315620880
        assertEquals(315620880L, parseRetryAfterHeader("Wednesday, 21-Oct-25 07:28:00 GMT", now))
    }

    @Test
    fun rfc850DateYear70to99MapsTo1900s() {
        // 2-digit year 99 → 1999; target is in the past relative to "now" → 0
        assertEquals(0L, parseRetryAfterHeader("Wednesday, 21-Oct-99 07:28:00 GMT", now))
    }

    // -----------------------------------------------------------------------
    // ANSI C asctime (obsolete): "Wed Oct 21 07:28:00 2015"
    // -----------------------------------------------------------------------

    @Test
    fun asctimeDate() {
        assertEquals(1680L, parseRetryAfterHeader("Wed Oct 21 07:28:00 2015", now))
    }

    @Test
    fun asctimeDateSingleDigitDay() {
        // Single-digit day with leading space: "Wed Oct  1 07:28:00 2015"
        // 2015-10-01T07:28:00Z = 1443684480; before now → 0
        assertEquals(0L, parseRetryAfterHeader("Wed Oct  1 07:28:00 2015", now))
    }

    // -----------------------------------------------------------------------
    // parseHttpDate helper (tests epoch seconds directly, independent of "now")
    // -----------------------------------------------------------------------

    @Test
    fun parseHttpDateImfFixdate() {
        // 2015-10-21T07:28:00Z = 1445412480
        assertEquals(1445412480L, parseHttpDate("Wed, 21 Oct 2015 07:28:00 GMT"))
    }

    @Test
    fun parseHttpDateRfc850() {
        assertEquals(1445412480L, parseHttpDate("Wednesday, 21-Oct-15 07:28:00 GMT"))
    }

    @Test
    fun parseHttpDateAsctime() {
        assertEquals(1445412480L, parseHttpDate("Wed Oct 21 07:28:00 2015"))
    }

    @Test
    fun parseHttpDateInvalid() {
        assertNull(parseHttpDate("not-a-date"))
    }
}
