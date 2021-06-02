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
import com.okta.idx.android.infrastructure.CODE_EDIT_TEXT
import com.okta.idx.android.infrastructure.CONFIRMED_PASSWORD_EDIT_TEXT
import com.okta.idx.android.infrastructure.ERROR_TEXT_VIEW
import com.okta.idx.android.infrastructure.FIRST_NAME_EDIT_TEXT
import com.okta.idx.android.infrastructure.ID_TOKEN_TYPE_TEXT_VIEW
import com.okta.idx.android.infrastructure.PHONE_EDIT_TEXT
import com.okta.idx.android.infrastructure.SELECT_BUTTON
import com.okta.idx.android.infrastructure.espresso.selectAuthenticator
import com.okta.idx.android.infrastructure.espresso.waitForElement
import com.okta.idx.android.infrastructure.network.NetworkRule
import com.okta.idx.android.infrastructure.network.testBodyFromFile
import com.okta.idx.android.network.mock.OktaMockWebServer
import com.okta.idx.android.network.mock.RequestMatchers.bodyWithJsonPath
import com.okta.idx.android.network.mock.RequestMatchers.path
import org.hamcrest.CoreMatchers.allOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelfServiceRegistrationTestEndToEnd {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    // Mary signs up with an invalid Email
    @Test fun scenario_4_1_3() {
        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.self_service_registration_button)).perform(click())
        waitForElement(FIRST_NAME_EDIT_TEXT)

        onView(withId(R.id.first_name_edit_text)).perform(replaceText("Mary"))
        onView(withId(R.id.last_name_edit_text)).perform(replaceText("Jo"))
        onView(withId(R.id.primary_email_edit_text)).perform(replaceText("Mary@example"))
        onView(withId(R.id.register_button)).perform(click())

        waitForElement(ERROR_TEXT_VIEW)
        onView(
            allOf(
                withId(R.id.error_text_view),
                withText("'Email' must be in the form of an email address")
            )
        ).check(
            matches(isDisplayed())
        )
        onView(
            allOf(
                withId(R.id.error_text_view),
                withText("Provided value for property 'Email' does not match required pattern")
            )
        ).check(
            matches(isDisplayed())
        )
    }
}
