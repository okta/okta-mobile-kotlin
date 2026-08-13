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
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import okhttp3.HttpUrl

/**
 * Optional capability for [WebAuthenticationProvider] implementations that can launch the OIDC
 * redirect flow via Chrome's Auth Tab (`androidx.browser.auth.AuthTabIntent`) instead of standard
 * Custom Tabs.
 *
 * Kept internal: [RedirectCoordinator] prefers this over [WebAuthenticationProvider.launchCustomTab] when a
 * provider implements it, but never needs to know whether a given launch actually used Auth Tab or
 * fell back to Custom Tabs — that decision belongs entirely to the implementation of [launchAuthTab].
 */
internal interface AuthTabWebAuthenticationProvider {
    /**
     * Launches the OIDC redirect flow via Auth Tab, or falls back to
     * [WebAuthenticationProvider.launchCustomTab] if the resolved browser doesn't support it.
     *
     * @param context the Android [android.app.Activity] [Context] which is used to display the flow.
     * @param url to authorize url the instance should display.
     * @param redirectUrl the redirect URI registered for this flow, used to configure Auth Tab's
     * redirect matching (scheme, or host+path for `https`).
     * @param launcher the launcher returned by `AuthTabIntent.registerActivityResultLauncher`, used to
     * launch the Auth Tab and receive its result.
     *
     * @return [Result.success] if the flow launched successfully, or [Result.failure] with the
     * launch exception if it fails.
     */
    fun launchAuthTab(
        context: Context,
        url: HttpUrl,
        redirectUrl: String,
        launcher: ActivityResultLauncher<Intent>,
    ): Result<Unit>
}
