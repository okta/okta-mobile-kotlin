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
package sample.okta.android.deviceauthorization

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId
import sample.okta.android.R
import sample.okta.android.dashboard.DashboardPage
import sample.okta.android.test.getText
import sample.okta.android.test.waitForTextMatching

internal class DeviceAuthorizationPage {
    init {
        waitForTextMatching("To sign in, visit .*")
    }

    fun expectDashboard(): DashboardPage {
        return DashboardPage()
    }

    fun getCode(): String {
        val directions = onView(withId(R.id.directions_text_view)).getText()
        return directions.substringAfterLast(" ")
    }

    fun getUrl(): String {
        val directions = onView(withId(R.id.directions_text_view)).getText()
        return directions.substringAfter("visit ").substringBefore(" ")
    }
}
