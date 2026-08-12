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
package com.okta.webauthenticationui

import android.content.Context

/**
 * Selects which browser package should host the Chrome Custom Tab used for the OIDC redirect flow.
 *
 * The default implementation used by [DefaultWebAuthenticationProvider] queries for installed
 * Custom Tabs providers, confirms each candidate can actually handle `http(s)` links, and prefers
 * [DefaultWebAuthenticationProvider.preferredBrowsers] in order. Implement this interface (and pass
 * it to [DefaultWebAuthenticationProvider]'s constructor) to fully control browser selection, e.g.
 * for testing or device-specific overrides.
 */
fun interface BrowserSelector {
    /**
     * Returns the package name of the browser to restrict the Custom Tab launch to.
     *
     * A failed [Result] (e.g. [NoBrowserFoundException]) means no specific browser was selected —
     * [DefaultWebAuthenticationProvider] then attempts to launch the Custom Tab without a package
     * restriction, letting Android resolve the default handler. If that unrestricted attempt also
     * fails, the original failure cause (e.g. [NoBrowserFoundException]) is surfaced from
     * [DefaultWebAuthenticationProvider.launchAuthTab] instead of the resulting
     * [android.content.ActivityNotFoundException], since it is the more specific and actionable
     * cause.
     */
    fun selectBrowser(context: Context): Result<String>
}

/**
 * Failure cause returned by [BrowserSelector.selectBrowser] when no usable browser was found.
 *
 * Surfaced from [DefaultWebAuthenticationProvider.launchAuthTab] — and, in turn, as the failure cause of a
 * [WebAuthentication] login/logout call — when no browser could be selected and the fallback,
 * unrestricted launch attempt also failed to resolve to an activity. This is distinct from
 * [WebAuthentication.FlowCancelledException], which means a browser opened but the user (or the
 * browser itself) closed it before completing the redirect.
 */
class NoBrowserFoundException internal constructor() : Exception("No usable browser package was found on this device.")
