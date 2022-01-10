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
package com.okta.oidc.browserredirect

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Browser
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.okta.oidc.browserredirect.events.CustomizeBrowserEvent
import com.okta.oidc.browserredirect.events.CustomizeCustomTabsEvent
import com.okta.oidc.kotlin.BuildConfig
import com.okta.oidc.kotlin.events.EventCoordinator
import okhttp3.HttpUrl

interface WebAuthenticationProvider {
    fun launch(context: Context, url: HttpUrl): Boolean
}

internal class DefaultWebAuthenticationProvider(
    private val eventCoordinator: EventCoordinator,
) : WebAuthenticationProvider {
    companion object {
        const val X_OKTA_USER_AGENT = "X-Okta-User-Agent-Extended"

        // TODO: Don't hardcode version.
        val USER_AGENT_HEADER = "okta-oidc-android/${Build.VERSION.SDK_INT} ${BuildConfig.LIBRARY_PACKAGE_NAME}/2.0.0"
    }

    override fun launch(context: Context, url: HttpUrl): Boolean {
        val intentBuilder: CustomTabsIntent.Builder = CustomTabsIntent.Builder()
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
            // TODO: How to handle passing cancel back to the caller (user pressed back button in browser)?
            tabsIntent.launchUrl(context, Uri.parse(url.toString()))
            return true
        } catch (e: ActivityNotFoundException) {
            return false
        }
    }

    private fun getBrowser(context: Context): String? {
        val event = CustomizeBrowserEvent()
        eventCoordinator.sendEvent(event)

        val pm: PackageManager = context.packageManager
        val serviceIntent = Intent()
        serviceIntent.action = CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION
        val resolveInfoList = pm.queryIntentServices(serviceIntent, event.queryIntentServicesFlags)
        val customTabsBrowsersPackages: MutableList<String> = ArrayList()
        for (info in resolveInfoList) {
            customTabsBrowsersPackages.add(info.serviceInfo.packageName)
        }

        for (browser in event.preferredBrowsers) {
            for (browserPackage in customTabsBrowsersPackages) {
                if (browserPackage.contains(browser)) {
                    return browserPackage
                }
            }
        }

        return customTabsBrowsersPackages.firstOrNull()
    }
}
