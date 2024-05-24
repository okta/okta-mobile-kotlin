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
import com.okta.authfoundation.client.Cache
import com.okta.authfoundation.client.DefaultAccessTokenValidator
import com.okta.authfoundation.client.DefaultDeviceSecretValidator
import com.okta.authfoundation.client.DefaultIdTokenValidator
import com.okta.authfoundation.client.DeviceSecretValidator
import com.okta.authfoundation.client.IdTokenValidator
import com.okta.authfoundation.client.OidcClock
import com.okta.authfoundation.client.SharedPreferencesCache
import com.okta.authfoundation.credential.DefaultTokenEncryptionHandler
import com.okta.authfoundation.credential.RoomTokenStorage
import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.credential.TokenEncryptionHandler
import com.okta.authfoundation.credential.TokenStorage
import com.okta.authfoundation.events.EventCoordinator
import kotlinx.coroutines.Dispatchers
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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

    /** The CoroutineDispatcher which should be used for IO bound tasks. */
    var ioDispatcher: CoroutineContext by NoSetAfterGetWithLazyDefaultFactory { Dispatchers.IO }

    /** The CoroutineDispatcher which should be used for compute bound tasks. */
    var computeDispatcher: CoroutineContext by NoSetAfterGetWithLazyDefaultFactory { Dispatchers.Default }

    /** The default EventCoordinator. */
    var eventCoordinator: EventCoordinator by NoSetAfterGetWithLazyDefaultFactory { EventCoordinator(emptyList()) }

    /** The default OidcClock. */
    var clock: OidcClock by NoSetAfterGetWithLazyDefaultFactory { OidcClock { Instant.now().epochSecond } }

    /** The default IdTokenValidator. */
    var idTokenValidator: IdTokenValidator by NoSetAfterGetWithLazyDefaultFactory { DefaultIdTokenValidator() }

    /** The default AccessTokenValidator. */
    var accessTokenValidator: AccessTokenValidator by NoSetAfterGetWithLazyDefaultFactory { DefaultAccessTokenValidator() }

    /** The default DeviceSecretValidator. */
    var deviceSecretValidator: DeviceSecretValidator by NoSetAfterGetWithLazyDefaultFactory { DefaultDeviceSecretValidator() }

    /** The default function that returns a singleton instance of [Cache]. */
    var cacheFactory: suspend () -> Cache by NoSetAfterGetWithLazyDefaultFactory { { SharedPreferencesCache.getInstance() } }

    /** The default function that retuns a singleton instance of [TokenStorage]. */
    var tokenStorageFactory: suspend () -> TokenStorage by NoSetAfterGetWithLazyDefaultFactory { { RoomTokenStorage.getInstance() } }

    /** The default [TokenEncryptionHandler] for encrypting and decrypting stored [Token]s.*/
    var tokenEncryptionHandler: TokenEncryptionHandler by NoSetAfterGetWithLazyDefaultFactory { DefaultTokenEncryptionHandler() }

    /** The default [CookieJar]. By default, this is [CookieJar.NO_COOKIES]. */
    var cookieJar: CookieJar by NoSetAfterGetWithLazyDefaultFactory { CookieJar.NO_COOKIES }

    /** The default wait time until the web login flow is cancelled after receiving empty redirect response from the web browser.
     * This can resolve some issues caused by older devices when invalid redirect results are returned from the older browser. When this is set to a non-zero value, it introduces a
     * delay to all redirects when an error is received. */
    var loginCancellationDebounceTime: Duration by NoSetAfterGetWithLazyDefaultFactory { 0.seconds }

    object Encryption {
        /** The default keyAlias for the encryption key that will be used for encrypting the stored Token objects */
        var keyAlias: String by NoSetAfterGetWithLazyDefaultFactory { "com.okta.authfoundation.rsakey" }
    }
}
