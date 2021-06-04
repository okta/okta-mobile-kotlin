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
package com.okta.idx.android.infrastructure

import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.okta.idx.android.MainActivity
import com.okta.idx.android.R
import com.okta.idx.android.infrastructure.espresso.waitForElement
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelfServicePasswordRecoveryEndToEndTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    // Mary tries to reset a password with the wrong email
    @Test
    fun scenario_3_1_2() {
        val prefix = "/scenarios/scenario_3_1_2"
        val username = EndToEndCredentials["$prefix/username"]

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(ViewMatchers.withId(R.id.login_button)).perform(ViewActions.click())
        waitForElement(USERNAME_EDIT_TEXT)

        onView(ViewMatchers.withId(R.id.forgot_password_button)).perform(ViewActions.click())
        onView(ViewMatchers.withId(R.id.username_edit_text)).perform(ViewActions.replaceText(username))
        onView(ViewMatchers.withId(R.id.forgot_password_button)).perform(ViewActions.click())

        waitForElement(ERROR_TEXT_VIEW)
        onView(
            ViewMatchers.withId(R.id.error_text_view)
        ).check(
            ViewAssertions.matches(ViewMatchers.withText("There is no account with the Username $username."))
        )
    }
}
