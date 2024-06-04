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

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.net.Uri
import android.provider.Browser
import androidx.browser.customtabs.CustomTabsService
import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.events.Event
import com.okta.authfoundation.events.EventCoordinator
import com.okta.authfoundation.events.EventHandler
import com.okta.testhelpers.RecordingEventHandler
import com.okta.webauthenticationui.events.CustomizeBrowserEvent
import com.okta.webauthenticationui.events.CustomizeCustomTabsEvent
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowResolveInfo

@RunWith(RobolectricTestRunner::class)
class DefaultWebAuthenticationProviderTest {
    @Test fun testLaunch() {
        val activity = Robolectric.buildActivity(Activity::class.java)
        val webAuthenticationProvider = DefaultWebAuthenticationProvider(EventCoordinator(emptyList()))
        assertThat(webAuthenticationProvider.launch(activity.get(), "https://example.com/not_used".toHttpUrl())).isNull()
        val activityShadow = shadowOf(activity.get())
        val cctActivity = activityShadow.nextStartedActivity
        assertThat(cctActivity.action).isEqualTo("android.intent.action.VIEW")
        val headers = cctActivity.extras!!.getBundle(Browser.EXTRA_HEADERS)
        assertThat(headers!!.getString(DefaultWebAuthenticationProvider.X_OKTA_USER_AGENT)).isEqualTo(DefaultWebAuthenticationProvider.USER_AGENT_HEADER)
        assertThat(cctActivity.`package`).isNull()
    }

    @Test fun testLaunchCallsEventHandler() {
        val activity = Robolectric.buildActivity(Activity::class.java)
        val eventHandler = RecordingEventHandler()
        val webAuthenticationProvider = DefaultWebAuthenticationProvider(EventCoordinator(eventHandler))
        assertThat(webAuthenticationProvider.launch(activity.get(), "https://example.com/not_used".toHttpUrl())).isNull()

        assertThat(eventHandler.size).isEqualTo(2)

        assertThat(eventHandler[0]).isInstanceOf(CustomizeCustomTabsEvent::class.java)
        assertThat((eventHandler[0] as CustomizeCustomTabsEvent).context).isNotNull()
        assertThat((eventHandler[0] as CustomizeCustomTabsEvent).intentBuilder).isNotNull()

        assertThat(eventHandler[1]).isInstanceOf(CustomizeBrowserEvent::class.java)
        assertThat((eventHandler[1] as CustomizeBrowserEvent).preferredBrowsers).hasSize(3)
        assertThat((eventHandler[1] as CustomizeBrowserEvent).queryIntentServicesFlags).isEqualTo(0)
    }

    @Test fun testLaunchWithEnabledBrowsers() {
        installCustomTabsProvider("com.android.chrome.beta")
        installCustomTabsProvider("com.android.chrome")
        ShadowResolveInfo.newResolveInfo(
            "Chrome",
            "com.android.chrome",
            "org.chromium.chrome.browser.customtabs.CustomTabsConnectionService"
        )
        val activity = Robolectric.buildActivity(Activity::class.java)
        val webAuthenticationProvider = DefaultWebAuthenticationProvider(EventCoordinator(emptyList()))
        assertThat(webAuthenticationProvider.launch(activity.get(), "https://example.com/not_used".toHttpUrl())).isNull()
        val activityShadow = shadowOf(activity.get())
        val cctActivity = activityShadow.nextStartedActivity
        assertThat(cctActivity.action).isEqualTo("android.intent.action.VIEW")
        assertThat(cctActivity.`package`).isEqualTo("com.android.chrome")
    }

    @Test fun testLaunchWithPreferredBrowsers() {
        installCustomTabsProvider("com.android.chrome.beta")
        installCustomTabsProvider("my.custom.preferred.browser")
        installCustomTabsProvider("com.android.chrome")
        val eventHandler = object : EventHandler {
            override fun onEvent(event: Event) {
                if (event is CustomizeBrowserEvent) {
                    event.preferredBrowsers.clear()
                    event.preferredBrowsers.add("my.custom.preferred.browser")
                }
            }
        }
        val webAuthenticationProvider = DefaultWebAuthenticationProvider(EventCoordinator(eventHandler))
        val activity = Robolectric.buildActivity(Activity::class.java)
        assertThat(webAuthenticationProvider.launch(activity.get(), "https://example.com/not_used".toHttpUrl())).isNull()
        val activityShadow = shadowOf(activity.get())
        val cctActivity = activityShadow.nextStartedActivity
        assertThat(cctActivity.action).isEqualTo("android.intent.action.VIEW")
        assertThat(cctActivity.`package`).isEqualTo("my.custom.preferred.browser")
    }

    @Test fun testLaunchCausesActivityNotFound() {
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        val activity = Robolectric.buildActivity(Activity::class.java)
        val webAuthenticationProvider = DefaultWebAuthenticationProvider(EventCoordinator(emptyList()))
        val exception = webAuthenticationProvider.launch(activity.get(), "https://example.com/not_used".toHttpUrl())
        assertThat(exception).isInstanceOf(ActivityNotFoundException::class.java)
    }

    // Copyright 2019 Google Inc. All Rights Reserved.
    // https://chromium.googlesource.com/custom-tabs-client/+/refs/heads/main/customtabs/junit/src/android/support/customtabs/trusted/TwaProviderPickerTest.java
    private fun installBrowser(packageName: String) {
        val intent = Intent()
            .setData(Uri.parse("http://"))
            .setAction(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val resolveInfo = ResolveInfo()
        resolveInfo.activityInfo = ActivityInfo()
        resolveInfo.activityInfo.packageName = packageName
        val packageManager = shadowOf(RuntimeEnvironment.getApplication().packageManager)
        @Suppress("DEPRECATION")
        packageManager.addResolveInfoForIntent(intent, resolveInfo)
    }

    private fun installCustomTabsProvider(packageName: String) {
        installBrowser(packageName)
        val intent = Intent().setAction(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION)
        val resolveInfo = ResolveInfo()
        resolveInfo.serviceInfo = ServiceInfo()
        resolveInfo.serviceInfo.packageName = packageName
        val packageManager = shadowOf(RuntimeEnvironment.getApplication().packageManager)
        @Suppress("DEPRECATION")
        packageManager.addResolveInfoForIntent(intent, resolveInfo)
    }
}
