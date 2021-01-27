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

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.hamcrest.CoreMatchers.allOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnrollTest {
    companion object {
        private const val ID_SUBMIT = "com.okta.idx.android:id/submit_button"
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

    @Test fun testEnroll() {
        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.enroll_button)).perform(click())

        waitForElement(ID_SUBMIT)
        onView(withText("Security Question")).perform(click())
        onView(withId(R.id.submit_button)).perform(click())
        waitForElement(ID_SUBMIT)
        onView(withText("Choose a security question")).perform(click())
        onView(withId(R.id.spinner)).perform(click())
        onView(withText("What is your favorite security question?")).perform(click())
        onView(withHint("Answer#Select")).perform(replaceText("1234"))
        SystemClock.sleep(1000) // The dialog takes some time to animate away.
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ID_TOKEN_TYPE)
        onView(withText("Token Type:")).check(matches(isDisplayed()))
        onView(withText("Bearer")).check(matches(isDisplayed()))
    }

    @Test fun testEnrollErrors() {
        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.enroll_button)).perform(click())

        waitForElement(ID_SUBMIT)
        onView(withId(R.id.submit_button)).perform(click())
        onView(withText("Field is required.")).check(matches(isDisplayed()))
        onView(withText("Security Question")).perform(click())
        onView(withId(R.id.submit_button)).perform(click())
        waitForElement(ID_SUBMIT)
        onView(withId(R.id.submit_button)).perform(click())
        onView(allOf(withId(R.id.radio_group_error_text_view), withText("Field is required.")))
            .check(matches(isDisplayed()))
        onView(withText("Choose a security question")).perform(click())
        onView(withId(R.id.submit_button)).perform(click())
        onView(allOf(withId(R.id.error_text_view), withText("Field is required."))).check(matches(isDisplayed()))
        onView(withId(R.id.spinner)).perform(click())
        onView(withText("What is your favorite security question?")).perform(click())
        SystemClock.sleep(1000) // The dialog takes some time to animate away.
        onView(withHint("Answer#Select")).perform(replaceText("1234"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ID_TOKEN_TYPE)
        onView(withText("Token Type:")).check(matches(isDisplayed()))
        onView(withText("Bearer")).check(matches(isDisplayed()))
    }

    @Test fun testEnrollCustom() {
        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.enroll_button)).perform(click())

        waitForElement(ID_SUBMIT)
        onView(withText("Security Question")).perform(click())
        onView(withId(R.id.submit_button)).perform(click())
        waitForElement(ID_SUBMIT)
        onView(withText("Create my own security question")).perform(click())
        onView(withHint("Create a security question")).perform(replaceText("1234"))
        onView(withHint("Answer#Custom")).perform(replaceText("1234"))
        SystemClock.sleep(1000) // The dialog takes some time to animate away.
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ID_TOKEN_TYPE)
        onView(withText("Token Type:")).check(matches(isDisplayed()))
        onView(withText("Bearer")).check(matches(isDisplayed()))
    }
}
