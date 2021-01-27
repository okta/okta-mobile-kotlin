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
package com.okta.idx.android

import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsernamePasswordTest {
    companion object {
        private const val ID_USERNAME = "com.okta.idx.android:id/username_edit_text"
        private const val ID_TOKEN_TYPE = "com.okta.idx.android:id/token_type"
    }

    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private lateinit var uiDevice: UiDevice

    @Before fun setup() {
        uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    private fun waitForElement(resourceId: String) {
        uiDevice.findObject(UiSelector().resourceId(resourceId)).waitForExists(10_000)
    }

    @Test fun testUsernameAndPassword() {
        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.username_password_button)).perform(click())

        waitForElement(ID_USERNAME)
        onView(withId(R.id.username_edit_text)).perform(replaceText("test@okta.com"))
        onView(withId(R.id.password_edit_text)).perform(replaceText("secret"))
        onView(withId(R.id.sign_in_button)).perform(click())

        waitForElement(ID_TOKEN_TYPE)
        onView(withText("Token Type:")).check(matches(isDisplayed()))
        onView(withText("Bearer")).check(matches(isDisplayed()))
    }
}
