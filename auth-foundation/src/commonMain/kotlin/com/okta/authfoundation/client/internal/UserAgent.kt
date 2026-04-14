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

import com.okta.authfoundation.SDK_VERSION

/**
 * Returns the platform-specific suffix for the user agent string (e.g., "Android/33", "JVM/17.0.1").
 */
internal expect fun platformUserAgentSuffix(): String

/**
 * Builds the user agent string for all HTTP requests made by the SDK.
 *
 * Format: `okta-auth-foundation-kotlin/<version> <platform>`
 */
internal object UserAgent {
    val value: String = "$SDK_VERSION ${platformUserAgentSuffix()}"
}
