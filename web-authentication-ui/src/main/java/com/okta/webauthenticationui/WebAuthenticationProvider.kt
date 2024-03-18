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
package com.okta.webauthenticationui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Browser
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import com.okta.authfoundation.events.EventCoordinator
import com.okta.webauthenticationui.events.CustomizeBrowserEvent
import com.okta.webauthenticationui.events.CustomizeCustomTabsEvent
import okhttp3.HttpUrl

/**
 * Used to launch the OIDC redirect flow associated with a [WebAuthentication].
 */
interface WebAuthenticationProvider {
    /**
     * Launches the OIDC redirect flow associated with a [WebAuthentication].
     *
     * @param context the Android [Activity] [Context] which is used to display the flow.
     * @param url the url the instance should display.
     *
     * @return the exception causing the launch to fail.
     */
    fun launch(context: Context, url: HttpUrl): Exception?
}

internal class DefaultWebAuthenticationProvider(
    private val eventCoordinator: EventCoordinator,
) : WebAuthenticationProvider {
    companion object {
        const val X_OKTA_USER_AGENT = "X-Okta-User-Agent-Extended"

        val USER_AGENT_HEADER = "web-authentication-ui/${Build.VERSION.SDK_INT} com.okta.webauthenticationui/2.0.0"
    }

    override fun launch(context: Context, url: HttpUrl): Exception? {
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
            tabsIntent.launchUrl(context, Uri.parse(url.toString()))
            return null
        } catch (e: ActivityNotFoundException) {
            return e
        }
    }

    private fun getBrowser(context: Context): String? {
        val event = CustomizeBrowserEvent()
        eventCoordinator.sendEvent(event)

        val pm: PackageManager = context.packageManager
        val serviceIntent = Intent()
        serviceIntent.action = CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION
        val resolveInfoList = pm.queryIntentServices(serviceIntent, event.queryIntentServicesFlags)
        val customTabsBrowsersPackages = mutableSetOf<String>()
        for (info in resolveInfoList) {
            customTabsBrowsersPackages.add(info.serviceInfo.packageName)
        }

        for (browser in event.preferredBrowsers) {
            if (customTabsBrowsersPackages.contains(browser)) {
                return browser
            }
        }

        return customTabsBrowsersPackages.firstOrNull()
    }
}
