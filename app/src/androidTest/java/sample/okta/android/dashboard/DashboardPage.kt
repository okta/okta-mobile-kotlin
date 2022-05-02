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
package sample.okta.android.dashboard

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withChild
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withTagKey
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import sample.okta.android.R

internal class DashboardPage {
    init {
        waitUntilExists()
    }

    private fun waitUntilExists(): DashboardPage {
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val selector = UiSelector().resourceIdMatches(".*credential_name")
        if (!uiDevice.findObject(selector).waitForExists(10_000)) {
            throw AssertionError("Dashboard page does not exist.")
        }
        return this
    }

    fun assertIsDefaultCredential(): DashboardPage {
        onView(withId(R.id.credential_name)).check(matches(withText("Default")))
        return this
    }

    fun assertHasClaim(claim: String, value: String): DashboardPage {
        onView(
            allOf(
                withParent(withChild(withText(claim))),
                withId(R.id.text_view_value),
                withTagKey(R.id.claim, equalTo(claim)),
                withText(value)
            )
        ).perform(scrollTo()).check(matches(withText(value)))
        return this
    }
}
