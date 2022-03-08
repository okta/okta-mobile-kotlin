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

import com.okta.authfoundation.client.AccessTokenValidator
import com.okta.authfoundation.client.DefaultAccessTokenValidator
import com.okta.authfoundation.client.DefaultIdTokenValidator
import com.okta.authfoundation.client.IdTokenValidator
import com.okta.authfoundation.client.OidcClock
import com.okta.authfoundation.events.EventCoordinator
import okhttp3.Call
import okhttp3.OkHttpClient
import java.time.Instant

/**
 *  The defaults used in various classes throughout the rest of the SDK.
 *
 *  Properties can be set until they're accessed.
 *  If properties are attempted to be set after they've been accessed, an IllegalStateException will be thrown to prevent using
 *  incorrect defaults.
 */
object AuthFoundationDefaults {
    /** The default Call.Factory. */
    var okHttpClientFactory: () -> Call.Factory by NoSetAfterGetWithLazyDefaultFactory { { OkHttpClient() } }

    /** The default EventCoordinator. */
    var eventCoordinator: EventCoordinator by NoSetAfterGetWithLazyDefaultFactory { EventCoordinator(emptyList()) }

    /** The default OidcClock. */
    var clock: OidcClock by NoSetAfterGetWithLazyDefaultFactory { defaultClock() }

    /** The default IdTokenValidator. */
    var idTokenValidator: IdTokenValidator by NoSetAfterGetWithLazyDefaultFactory { DefaultIdTokenValidator() }

    /** The default AccessTokenValidator. */
    var accessTokenValidator: AccessTokenValidator by NoSetAfterGetWithLazyDefaultFactory { DefaultAccessTokenValidator() }

    private fun defaultClock(): OidcClock {
        return object : OidcClock {
            override fun currentTimeMillis(): Long {
                return Instant.now().epochSecond
            }
        }
    }
}
