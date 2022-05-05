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
package sample.okta.android.launch

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import sample.okta.android.R
import sample.okta.android.browser.BrowserPage
import sample.okta.android.deviceauthorization.DeviceAuthorizationPage
import sample.okta.android.resourceowner.ResourceOwnerPage

internal object LaunchPage {
    fun goToResourceOwnerPage(): ResourceOwnerPage {
        onView(withId(R.id.login_with_resource_owner_flow)).perform(click())
        return ResourceOwnerPage()
    }

    fun goToBrowserPage(): BrowserPage {
        onView(withId(R.id.login_with_browser_button)).perform(click())
        return BrowserPage()
    }

    fun goToDeviceAuthorizationPage(): DeviceAuthorizationPage {
        onView(withId(R.id.login_with_device_authorization_flow)).perform(click())
        return DeviceAuthorizationPage()
    }
}
