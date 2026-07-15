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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Browser
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.core.net.toUri
import com.okta.authfoundation.events.EventCoordinator
import com.okta.webauthenticationui.events.CustomizeBrowserEvent
import com.okta.webauthenticationui.events.CustomizeCustomTabsEvent
import okhttp3.HttpUrl

/**
 * Default [WebAuthenticationProvider] implementation that launches the OIDC redirect flow using
 * Chrome Custom Tabs.
 *
 * Browser selection and tab appearance are configured via constructor parameters rather than event
 * handlers. Use [preferredBrowsers] to control which browser is chosen, [queryIntentServicesFlags]
 * to adjust the package-manager query, and [customizeTabsIntent] to style the Chrome Custom Tab
 * (toolbar color, animations, close button icon, etc.).
 *
 * ```kotlin
 * val provider = DefaultWebAuthenticationProvider(
 *     preferredBrowsers = listOf("com.android.chrome"),
 *     customizeTabsIntent = { context, builder ->
 *         builder.setToolbarColor(ContextCompat.getColor(context, R.color.brand_primary))
 *     }
 * )
 * val webAuth = WebAuthentication(client, provider)
 * ```
 */
class DefaultWebAuthenticationProvider
    @JvmOverloads
    constructor(
        private val eventCoordinator: EventCoordinator = EventCoordinator(emptyList()),
        /**
         * The list of browser package names to prefer when selecting which Chrome Custom Tabs
         * implementation is used. Checked in order; the first installed match wins.
         *
         * Defaults to Chrome Stable, Chrome System, and Chrome Beta.
         */
        val preferredBrowsers: List<String> = DEFAULT_PREFERRED_BROWSERS,
        /**
         * Flags passed to [android.content.pm.PackageManager.queryIntentServices] when querying for Chrome Custom
         * Tabs intent services.
         */
        val queryIntentServicesFlags: Int = 0,
        /**
         * Optional callback to customize the [androidx.browser.customtabs.CustomTabsIntent.Builder] before the Chrome Custom
         * Tab is launched. Use this to set toolbar color, animations, close button icon, and any
         * other [androidx.browser.customtabs.CustomTabsIntent.Builder] options.
         *
         * ```kotlin
         * DefaultWebAuthenticationProvider(
         *     customizeTabsIntent = { context, builder ->
         *         builder.setToolbarColor(ContextCompat.getColor(context, R.color.brand_primary))
         *     }
         * )
         * ```
         */
        private val customizeTabsIntent: ((context: Context, builder: CustomTabsIntent.Builder) -> Unit)? = null,
    ) : WebAuthenticationProvider {
        companion object {
            const val X_OKTA_USER_AGENT = "X-Okta-User-Agent-Extended"

            val USER_AGENT_HEADER = "web-authentication-ui/${Build.VERSION.SDK_INT} com.okta.webauthenticationui/2.0.0"

            private const val CHROME_STABLE = "com.android.chrome"
            private const val CHROME_SYSTEM = "com.google.android.apps.chrome"
            private const val CHROME_BETA = "com.android.chrome.beta"

            /** The default preferred browser list: Chrome Stable, Chrome System, Chrome Beta. */
            val DEFAULT_PREFERRED_BROWSERS: List<String> = listOf(CHROME_STABLE, CHROME_SYSTEM, CHROME_BETA)
        }

        override fun launch(
            context: Context,
            url: HttpUrl,
        ): Exception? {
            val intentBuilder: CustomTabsIntent.Builder = CustomTabsIntent.Builder()
            customizeTabsIntent?.invoke(context, intentBuilder)
            @Suppress("DEPRECATION")
            eventCoordinator.sendEvent(CustomizeCustomTabsEvent(context, intentBuilder))
            val tabsIntent: CustomTabsIntent = intentBuilder.build()

            val packageBrowser = getBrowser(context)
            if (packageBrowser != null) {
                tabsIntent.intent.setPackage(packageBrowser)
            }

            val headers = Bundle()
            headers.putString(X_OKTA_USER_AGENT, USER_AGENT_HEADER)
            tabsIntent.intent.putExtra(Browser.EXTRA_HEADERS, headers)

            try {
                tabsIntent.launchUrl(context, url.toString().toUri())
                return null
            } catch (e: ActivityNotFoundException) {
                return e
            }
        }

        private fun getBrowser(context: Context): String? {
            // Initialize the event from the constructor params so existing EventCoordinator
            // handlers that mutate CustomizeBrowserEvent continue to work as before.
            // New callers should use the constructor params directly instead of handling the event.
            @Suppress("DEPRECATION")
            val event = CustomizeBrowserEvent(preferredBrowsers = preferredBrowsers.toMutableList())
            @Suppress("DEPRECATION")
            event.queryIntentServicesFlags = queryIntentServicesFlags
            @Suppress("DEPRECATION")
            eventCoordinator.sendEvent(event)

            val pm: PackageManager = context.packageManager
            val serviceIntent = Intent()
            serviceIntent.action = CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION
            @Suppress("DEPRECATION")
            val resolveInfoList = pm.queryIntentServices(serviceIntent, event.queryIntentServicesFlags)
            val customTabsBrowsersPackages = mutableSetOf<String>()
            for (info in resolveInfoList) {
                customTabsBrowsersPackages.add(info.serviceInfo.packageName)
            }

            @Suppress("DEPRECATION")
            for (browser in event.preferredBrowsers) {
                if (customTabsBrowsersPackages.contains(browser)) {
                    return browser
                }
            }

            return customTabsBrowsersPackages.firstOrNull()
        }
    }
