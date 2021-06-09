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
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.okta.idx.android.infrastructure.FIRST_NAME_EDIT_TEXT
import com.okta.idx.android.infrastructure.LAUNCH_TITLE_TEXT_VIEW
import com.okta.idx.android.infrastructure.USERNAME_EDIT_TEXT
import com.okta.idx.android.infrastructure.espresso.waitForElement
import com.okta.idx.android.infrastructure.network.NetworkRule
import com.okta.idx.android.network.mock.OktaMockWebServer
import org.junit.Before
import org.junit.Rule

open class BaseMainActivityTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    @get:Rule val networkRule = NetworkRule()

    @Before fun setup() {
        OktaMockWebServer.dispatcher.consumeResponses = true
    }

    fun goToLogin() {
        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitForElement(LAUNCH_TITLE_TEXT_VIEW)
        onView(withId(R.id.login_button)).perform(click())
        waitForElement(USERNAME_EDIT_TEXT)
    }

    fun goToSelfServiceRegistration() {
        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitForElement(LAUNCH_TITLE_TEXT_VIEW)
        onView(withId(R.id.self_service_registration_button)).perform(click())
        waitForElement(FIRST_NAME_EDIT_TEXT)
    }
}
