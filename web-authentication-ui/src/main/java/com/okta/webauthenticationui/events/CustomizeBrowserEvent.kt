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
package com.okta.webauthenticationui.events

import android.content.pm.PackageManager
import com.okta.authfoundation.events.Event
import com.okta.authfoundation.events.EventHandler
import com.okta.webauthenticationui.WebAuthentication

/**
 * Emitted via [EventHandler.onEvent] when [WebAuthentication.login] or [WebAuthentication.logoutOfBrowser] is invoked.
 *
 * This can be used to customize the browsers used for displaying the Chrome Custom Tabs to the user.
 */
class CustomizeBrowserEvent internal constructor(
    /**
     * Used when querying the package manager for Chrome Custom Tabs intent services.
     *
     * See [PackageManager.queryIntentServices] for additional details.
     */
    var queryIntentServicesFlags: Int = 0,

    /**
     * The list of browser package names to prefer when selecting which Chrome Custom Tabs intent service is used.
     */
    val preferredBrowsers: MutableList<String> = mutableListOf(CHROME_STABLE, CHROME_SYSTEM, CHROME_BETA)
) : Event {
    private companion object {
        private const val CHROME_STABLE = "com.android.chrome"
        private const val CHROME_SYSTEM = "com.google.android.apps.chrome"
        private const val CHROME_BETA = "com.android.chrome.beta"
    }
}
