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

@InternalAuthFoundationApi
object SdkDefaults {
    var getOkHttpClientFactory: () -> (() -> Call.Factory) = { { OkHttpClient() } }
    var getIoDispatcher: () -> CoroutineContext = { Dispatchers.IO }
    var getComputeDispatcher: () -> CoroutineContext = { Dispatchers.Default }
    var getEventCoordinator: () -> EventCoordinator = { EventCoordinator(emptyList()) }
    var getClock: () -> OidcClock = { OidcClock { Instant.now().epochSecond } }
    var getIdTokenValidator: () -> IdTokenValidator = { DefaultIdTokenValidator() }
    var getAccessTokenValidator: () -> AccessTokenValidator = { DefaultAccessTokenValidator() }
    var getDeviceSecretValidator: () -> DeviceSecretValidator = { DefaultDeviceSecretValidator() }
    var getCacheFactory: () -> suspend () -> Cache = { { SharedPreferencesCache.getInstance() } }
    var getTokenStorageFactory: () -> suspend () -> TokenStorage = { { RoomTokenStorage.getInstance() } }
    var getTokenEncryptionHandler: () -> TokenEncryptionHandler = { DefaultTokenEncryptionHandler() }
    var getCookieJar: () -> CookieJar = { CookieJar.NO_COOKIES }
    var getLoginCancellationDebounceTime: () -> Duration = { 0.seconds }
}
