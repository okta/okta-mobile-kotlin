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
class MfaEmailTest {
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

    @Test fun testMfaEmail() {
        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.mfa_button)).perform(click())

        waitForElement(ID_SUBMIT)
        onView(withId(R.id.username_edit_text)).perform(replaceText("test@okta.com"))
        onView(withId(R.id.submit_button)).perform(click())
        waitForElement(ID_SUBMIT)
        onView(withText("Email")).perform(click())
        onView(withId(R.id.submit_button)).perform(click())
        waitForElement(ID_SUBMIT)
        onView(withId(R.id.passcode_edit_text)).perform(replaceText("1234"))
        onView(withId(R.id.submit_button)).perform(click())
        waitForElement(ID_SUBMIT)
        onView(withId(R.id.passcode_edit_text)).perform(replaceText("secret"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ID_TOKEN_TYPE)
        onView(withText("Token Type:")).check(matches(isDisplayed()))
        onView(withText("Bearer")).check(matches(isDisplayed()))
    }

    @Test fun testMfaEmailErrors() {
        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.mfa_button)).perform(click())

        waitForElement(ID_SUBMIT)
        onView(withId(R.id.submit_button)).perform(click())
        onView(withText("Field is required.")).check(matches(isDisplayed()))
        onView(withId(R.id.username_edit_text)).perform(replaceText("test@okta.com"))
        onView(withId(R.id.submit_button)).perform(click())
        waitForElement(ID_SUBMIT)
        onView(withId(R.id.submit_button)).perform(click())
        onView(withText("Field is required.")).check(matches(isDisplayed()))
        onView(withText("Email")).perform(click())
        onView(withId(R.id.submit_button)).perform(click())
        waitForElement(ID_SUBMIT)
        onView(withId(R.id.submit_button)).perform(click())
        onView(withText("Field is required.")).check(matches(isDisplayed()))
        onView(withId(R.id.passcode_edit_text)).perform(replaceText("1234"))
        onView(withId(R.id.submit_button)).perform(click())
        waitForElement(ID_SUBMIT)
        onView(withId(R.id.submit_button)).perform(click())
        onView(withText("Field is required.")).check(matches(isDisplayed()))
        onView(withId(R.id.passcode_edit_text)).perform(replaceText("secret"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ID_TOKEN_TYPE)
        onView(withText("Token Type:")).check(matches(isDisplayed()))
        onView(withText("Bearer")).check(matches(isDisplayed()))
    }

    @Test fun testCancel() {
        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.mfa_button)).perform(click())

        waitForElement(ID_SUBMIT)
        onView(withId(R.id.username_edit_text)).perform(replaceText("test@okta.com"))
        onView(withId(R.id.submit_button)).perform(click())
        waitForElement(ID_SUBMIT)
        onView(withText("Email")).check(matches(isDisplayed()))
        onView(withId(R.id.cancel_button)).perform(click())
        waitForElement(ID_SUBMIT)
        onView(withId(R.id.username_edit_text)).perform(replaceText("test@okta.com"))
        onView(withId(R.id.submit_button)).perform(click())
        waitForElement(ID_SUBMIT)
        onView(withText("Email")).check(matches(isDisplayed()))
    }

    @Test fun testBackendError() {
        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.mfa_button)).perform(click())

        waitForElement(ID_SUBMIT)
        onView(withId(R.id.username_edit_text)).perform(replaceText("force-error"))
        onView(withId(R.id.submit_button)).perform(click())
        waitForElement(ID_SUBMIT)
        onView(withText("You do not have permission to perform the requested action."))
            .check(matches(isDisplayed()))
    }

    @Test fun testNetworkError() {
        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.mfa_button)).perform(click())

        waitForElement(ID_SUBMIT)
        onView(withId(R.id.username_edit_text)).perform(replaceText("force-network-error"))
        onView(withId(R.id.submit_button)).perform(click())
        waitForElement(ID_SUBMIT)
        onView(withText("An error occurred.")).check(matches(isDisplayed()))
    }
}
