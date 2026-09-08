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
package com.okta.idx.kotlin.kmp

/**
 * Provides information about the user being authenticated.
 */
class IdxUser internal constructor(
    /** Unique identifier for this user. */
    val id: String,
    /** Username for this user, if available. */
    val username: String?,
    /** Profile information for this user, if available. */
    val profile: Profile?,
) {
    /**
     * Profile information about the user being authenticated, when the server provides it.
     *
     * [timeZone] and [locale] are the raw IANA time zone ID and locale tag strings the server
     * returned, rather than a platform date/locale type (unlike the legacy `com.okta.idx.kotlin.dto.IdxUser`,
     * which exposes `java.util.TimeZone`/`java.util.Locale` — not available outside the JVM/Android).
     */
    class Profile internal constructor(
        /** The user's given (first) name, if available. */
        val firstName: String?,
        /** The user's family (last) name, if available. */
        val lastName: String?,
        /** The user's configured time zone ID (e.g. `"America/Los_Angeles"`), if available. */
        val timeZone: String?,
        /** The user's configured locale tag (e.g. `"en_US"`), if available. */
        val locale: String?,
    )
}
