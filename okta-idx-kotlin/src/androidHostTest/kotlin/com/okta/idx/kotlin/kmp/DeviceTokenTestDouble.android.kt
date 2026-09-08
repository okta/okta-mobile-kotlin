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

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.client.DeviceTokenProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll

/**
 * `android`'s real [AndroidDeviceTokenCookieHook] delegates to [DeviceTokenProvider.instance], which
 * persists via an AES key backed by the real AndroidKeyStore — unavailable under Robolectric. Stub the
 * singleton so shared `commonTest` assertions don't depend on device-hardware-backed crypto.
 */
@OptIn(InternalAuthFoundationApi::class)
actual fun setUpDeviceTokenTestDouble() {
    mockkObject(DeviceTokenProvider.Companion)
    val fakeDeviceTokenProvider = mockk<DeviceTokenProvider>()
    coEvery { fakeDeviceTokenProvider.getDeviceToken() } returns "commontest-device-token"
    every { DeviceTokenProvider.instance } returns fakeDeviceTokenProvider
}

actual fun tearDownDeviceTokenTestDouble() {
    unmockkAll()
}
