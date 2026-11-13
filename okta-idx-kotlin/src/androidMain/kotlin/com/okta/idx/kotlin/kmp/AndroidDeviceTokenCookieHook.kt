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

/**
 * The `android` target's default [DeviceTokenCookieHook], backed by the existing (frozen, relocated
 * unchanged) [DeviceTokenProvider] — the same Android-encrypted device-token store the legacy
 * implementation uses.
 */
@OptIn(InternalAuthFoundationApi::class)
internal object AndroidDeviceTokenCookieHook : DeviceTokenCookieHook {
    override suspend fun deviceToken(): String = DeviceTokenProvider.instance.getDeviceToken()
}
