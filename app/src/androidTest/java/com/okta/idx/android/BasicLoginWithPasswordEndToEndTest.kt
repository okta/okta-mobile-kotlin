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
import com.okta.idx.android.infrastructure.ERROR_TEXT_VIEW
import com.okta.idx.android.infrastructure.EndToEndCredentials
import com.okta.idx.android.infrastructure.FORGOT_PASSWORD_BUTTON
import com.okta.idx.android.infrastructure.ID_TOKEN_TYPE_TEXT_VIEW
import com.okta.idx.android.infrastructure.USERNAME_EDIT_TEXT
import com.okta.idx.android.infrastructure.espresso.waitForElement
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BasicLoginWithPasswordEndToEndTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    // Mary logs in with a Password
    @Test fun scenario_1_1_1() {
        val prefix = "/scenarios/scenario_1_1_1"

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.login_button)).perform(click())
        waitForElement(USERNAME_EDIT_TEXT)

        onView(withId(R.id.username_edit_text)).perform(replaceText(EndToEndCredentials["$prefix/username"]))
        onView(withId(R.id.password_edit_text)).perform(replaceText(EndToEndCredentials["$prefix/password"]))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ID_TOKEN_TYPE_TEXT_VIEW)
        onView(withText("Token Type:")).check(matches(isDisplayed()))
        onView(withText("Bearer")).check(matches(isDisplayed()))
    }

    // Mary doesn't know her username
    @Test fun scenario_1_1_2() {
        val prefix = "/scenarios/scenario_1_1_2"

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.login_button)).perform(click())
        waitForElement(USERNAME_EDIT_TEXT)

        val username = EndToEndCredentials["$prefix/username"]

        onView(withId(R.id.username_edit_text)).perform(replaceText(username))
        onView(withId(R.id.password_edit_text)).perform(replaceText(EndToEndCredentials["$prefix/password"]))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("There is no account with the Username $username.")))
    }

    // Mary doesn't know her password
    @Test fun scenario_1_1_3() {
        val prefix = "/scenarios/scenario_1_1_3"

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.login_button)).perform(click())
        waitForElement(USERNAME_EDIT_TEXT)

        onView(withId(R.id.username_edit_text)).perform(replaceText(EndToEndCredentials["$prefix/username"]))
        onView(withId(R.id.password_edit_text)).perform(replaceText(EndToEndCredentials["$prefix/password"]))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("Authentication failed")))
    }

    // Mary is not assigned to the application
    @Test fun scenario_1_1_4() {
        val prefix = "/scenarios/scenario_1_1_4"

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.login_button)).perform(click())
        waitForElement(USERNAME_EDIT_TEXT)

        onView(withId(R.id.username_edit_text)).perform(replaceText(EndToEndCredentials["$prefix/username"]))
        onView(withId(R.id.password_edit_text)).perform(replaceText(EndToEndCredentials["$prefix/password"]))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("User is not assigned to this application")))
    }

    // Mary's account is suspended
    @Test fun scenario_1_1_5() {
        val prefix = "/scenarios/scenario_1_1_5"

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.login_button)).perform(click())
        waitForElement(USERNAME_EDIT_TEXT)

        onView(withId(R.id.username_edit_text)).perform(replaceText(EndToEndCredentials["$prefix/username"]))
        onView(withId(R.id.password_edit_text)).perform(replaceText(EndToEndCredentials["$prefix/password"]))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("Authentication failed")))
    }

    // Mary's account is locked
    @Test fun scenario_1_1_6() {
        val prefix = "/scenarios/scenario_1_1_6"

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.login_button)).perform(click())
        waitForElement(USERNAME_EDIT_TEXT)

        onView(withId(R.id.username_edit_text)).perform(replaceText(EndToEndCredentials["$prefix/username"]))
        onView(withId(R.id.password_edit_text)).perform(replaceText(EndToEndCredentials["$prefix/password"]))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("Authentication failed")))
    }

    // Mary's account is deactivated
    @Test fun scenario_1_1_7() {
        val prefix = "/scenarios/scenario_1_1_7"

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.login_button)).perform(click())
        waitForElement(USERNAME_EDIT_TEXT)

        onView(withId(R.id.username_edit_text)).perform(replaceText(EndToEndCredentials["$prefix/username"]))
        onView(withId(R.id.password_edit_text)).perform(replaceText(EndToEndCredentials["$prefix/password"]))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("User is not assigned to this application")))
    }

    // Mary clicks on the "Forgot Password Link"
    @Test fun scenario_1_1_8() {
        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.login_button)).perform(click())

        waitForElement(FORGOT_PASSWORD_BUTTON)
        onView(withId(R.id.forgot_password_button)).perform(click())

        onView(withId(R.id.username_edit_text)).check(matches(isDisplayed()))
        onView(withId(R.id.forgot_password_button)).check(matches(isDisplayed()))
    }
}
