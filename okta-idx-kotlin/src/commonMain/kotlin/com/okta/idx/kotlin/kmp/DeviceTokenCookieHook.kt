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
 * An injectable seam for attaching a device-token identifier (the `DT` cookie) to IDX requests.
 *
 * The legacy Android implementation hard-wires this behavior via an OkHttp `CookieJar` backed by an
 * Android-encrypted device-token store (see `research.md`). Since there is nothing sensible to back
 * this with on a plain JVM target, the default here is a no-op — explicit and documented, not a
 * silent omission (FR-005). The `android` target supplies a real implementation backed by the
 * existing `DeviceTokenProvider` (see `AndroidDeviceTokenCookieHook`).
 */
interface DeviceTokenCookieHook {
    /** Returns the device-token value to send with IDX requests, or `null` to send none. */
    suspend fun deviceToken(): String?

    companion object {
        /** The default, platform-neutral hook: never supplies a device token. */
        val NoOp: DeviceTokenCookieHook =
            object : DeviceTokenCookieHook {
                override suspend fun deviceToken(): String? = null
            }
    }
}
