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

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import com.okta.authfoundation.events.Event
import com.okta.authfoundation.events.EventHandler
import com.okta.webauthenticationui.WebAuthentication

/**
 * Emitted via [EventHandler.onEvent] when [WebAuthentication.login] or [WebAuthentication.logoutOfBrowser] is invoked.
 *
 * This can be used to customize the [CustomTabsIntent.Builder] before being displayed to the user.
 */
class CustomizeCustomTabsEvent internal constructor(
    /**
     * The context being used to launch the Chrome Custom Tabs.
     */
    val context: Context,

    /**
     * The [CustomTabsIntent.Builder] reference available for mutation to customize the Chrome Custom Tabs before being displayed to
     * the user.
     */
    val intentBuilder: CustomTabsIntent.Builder,
) : Event
