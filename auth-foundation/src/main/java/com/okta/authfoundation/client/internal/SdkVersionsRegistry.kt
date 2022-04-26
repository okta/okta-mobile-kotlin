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

import android.os.Build
import com.okta.authfoundation.InternalAuthFoundationApi
import java.util.Collections

@InternalAuthFoundationApi
object SdkVersionsRegistry {
    private val sdkVersions: MutableSet<String> = Collections.synchronizedSet(mutableSetOf<String>())

    @Volatile internal var userAgent: String = ""
        private set

    init {
        reset()
    }

    fun register(sdkVersion: String) {
        if (sdkVersions.add(sdkVersion)) {
            regenerateUserAgent()
        }
    }

    private fun regenerateUserAgent() {
        userAgent = "${sdkVersions.sorted().joinToString(separator = " ")} Android/${Build.VERSION.SDK_INT}"
    }

    internal fun reset() {
        sdkVersions.clear()
        sdkVersions.add(SDK_VERSION)
        regenerateUserAgent()
    }
}
