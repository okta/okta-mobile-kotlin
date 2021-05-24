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
import com.okta.idx.android.infrastructure.espresso.waitForElement
import com.okta.idx.android.infrastructure.network.NetworkRule
import com.okta.idx.android.infrastructure.network.testBodyFromFile
import com.okta.idx.android.network.mock.OktaMockWebServer
import com.okta.idx.android.network.mock.RequestMatchers.path
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BasicLoginWithPasswordTest {
    companion object {
        private const val ID_TOKEN_TYPE_TEXT_VIEW = "com.okta.idx.android:id/token_type"
        private const val ERROR_TEXT_VIEW = "com.okta.idx.android:id/error_text_view"
        private const val USERNAME_EDIT_TEXT = "com.okta.idx.android:id/username_edit_text"
    }

    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    @get:Rule val networkRule = NetworkRule()

    @Before fun setup() {
        OktaMockWebServer.dispatcher.consumeResponses = true
    }

    @Test fun scenario_1_1_1_Mary_logs_in_with_a_Password() {
        val mockPrefix = "scenario_1_1_1"
        networkRule.enqueue(path("oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("$mockPrefix/interact.json")
        }
        networkRule.enqueue(path("idp/idx/introspect")) { response ->
            response.testBodyFromFile("$mockPrefix/introspect.json")
        }
        networkRule.enqueue(path("idp/idx/identify")) { response ->
            response.testBodyFromFile("$mockPrefix/identify.json")
        }
        networkRule.enqueue(path("idp/idx/challenge/answer")) { response ->
            response.testBodyFromFile("$mockPrefix/answer.json")
        }
        networkRule.enqueue(path("oauth2/v1/token")) { response ->
            response.testBodyFromFile("$mockPrefix/tokens.json")
        }
        networkRule.enqueue(path("oauth2/default/v1/userinfo")) { response ->
            response.testBodyFromFile("$mockPrefix/userinfo.json")
        }

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.login_button)).perform(click())
        waitForElement(USERNAME_EDIT_TEXT)

        onView(withId(R.id.username_edit_text)).perform(replaceText("Mary@example.com"))
        onView(withId(R.id.password_edit_text)).perform(replaceText("superSecret"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ID_TOKEN_TYPE_TEXT_VIEW)
        onView(withText("Token Type:")).check(matches(isDisplayed()))
        onView(withText("Bearer")).check(matches(isDisplayed()))
    }

    @Test fun scenario_1_1_2_Mary_doesn_t_know_her_username() {
        val mockPrefix = "scenario_1_1_2"
        networkRule.enqueue(path("oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("$mockPrefix/interact.json")
        }
        networkRule.enqueue(path("idp/idx/introspect")) { response ->
            response.testBodyFromFile("$mockPrefix/introspect.json")
        }
        networkRule.enqueue(path("idp/idx/identify")) { response ->
            response.testBodyFromFile("$mockPrefix/identify.json")
        }
        networkRule.enqueue(path("idp/idx/challenge/answer")) { response ->
            response.testBodyFromFile("$mockPrefix/answer.json")
        }

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.login_button)).perform(click())
        waitForElement(USERNAME_EDIT_TEXT)

        onView(withId(R.id.username_edit_text)).perform(replaceText("mary@unknown.com"))
        onView(withId(R.id.password_edit_text)).perform(replaceText("superSecret"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("There is no account with the Username mary@unknown.com.")))
    }

    @Test fun scenario_1_1_3_Mary_doesn_t_know_her_password() {
        val mockPrefix = "scenario_1_1_3"
        networkRule.enqueue(path("oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("$mockPrefix/interact.json")
        }
        networkRule.enqueue(path("idp/idx/introspect")) { response ->
            response.testBodyFromFile("$mockPrefix/introspect.json")
        }
        networkRule.enqueue(path("idp/idx/identify")) { response ->
            response.testBodyFromFile("$mockPrefix/identify.json")
        }
        networkRule.enqueue(path("idp/idx/challenge/answer")) { response ->
            response.testBodyFromFile("$mockPrefix/answer.json")
            response.setResponseCode(400)
            response.addHeader("content-type", "application/json")
        }

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.login_button)).perform(click())
        waitForElement(USERNAME_EDIT_TEXT)

        onView(withId(R.id.username_edit_text)).perform(replaceText("mary@example.com"))
        onView(withId(R.id.password_edit_text)).perform(replaceText("wrongo"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("Password is incorrect")))
    }

    @Test fun scenario_1_1_4_Mary_is_not_assigned_to_the_application() {
        val mockPrefix = "scenario_1_1_4"
        networkRule.enqueue(path("oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("$mockPrefix/interact.json")
        }
        networkRule.enqueue(path("idp/idx/introspect")) { response ->
            response.testBodyFromFile("$mockPrefix/introspect.json")
        }
        networkRule.enqueue(path("idp/idx/identify")) { response ->
            response.testBodyFromFile("$mockPrefix/identify.json")
        }
        networkRule.enqueue(path("idp/idx/challenge/answer")) { response ->
            response.testBodyFromFile("$mockPrefix/answer.json")
            response.setResponseCode(400)
            response.addHeader("content-type", "application/json")
        }

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.login_button)).perform(click())
        waitForElement(USERNAME_EDIT_TEXT)

        onView(withId(R.id.username_edit_text)).perform(replaceText("mary@example.com"))
        onView(withId(R.id.password_edit_text)).perform(replaceText("superSecret"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("User is not assigned to this application")))
    }

    @Test fun scenario_1_1_5_Marys_account_is_suspended() {
        val mockPrefix = "scenario_1_1_5"
        networkRule.enqueue(path("oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("$mockPrefix/interact.json")
        }
        networkRule.enqueue(path("idp/idx/introspect")) { response ->
            response.testBodyFromFile("$mockPrefix/introspect.json")
        }
        networkRule.enqueue(path("idp/idx/identify")) { response ->
            response.testBodyFromFile("$mockPrefix/identify.json")
        }
        networkRule.enqueue(path("idp/idx/challenge/answer")) { response ->
            response.testBodyFromFile("$mockPrefix/answer.json")
            response.setResponseCode(400)
            response.addHeader("content-type", "application/json")
        }

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.login_button)).perform(click())
        waitForElement(USERNAME_EDIT_TEXT)

        onView(withId(R.id.username_edit_text)).perform(replaceText("mary@example.com"))
        onView(withId(R.id.password_edit_text)).perform(replaceText("superSecret"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("Authentication failed")))
    }

    @Test fun scenario_1_1_6_Marys_account_is_locked() {
        val mockPrefix = "scenario_1_1_6"
        networkRule.enqueue(path("oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("$mockPrefix/interact.json")
        }
        networkRule.enqueue(path("idp/idx/introspect")) { response ->
            response.testBodyFromFile("$mockPrefix/introspect.json")
        }
        networkRule.enqueue(path("idp/idx/identify")) { response ->
            response.testBodyFromFile("$mockPrefix/identify.json")
        }
        networkRule.enqueue(path("idp/idx/challenge/answer")) { response ->
            response.testBodyFromFile("$mockPrefix/answer.json")
            response.setResponseCode(400)
            response.addHeader("content-type", "application/json")
        }

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.login_button)).perform(click())
        waitForElement(USERNAME_EDIT_TEXT)

        onView(withId(R.id.username_edit_text)).perform(replaceText("mary@example.com"))
        onView(withId(R.id.password_edit_text)).perform(replaceText("superSecret"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("This factor is suspended for your account due to too many failed attempts")))
    }

    @Test fun scenario_1_1_7_Marys_account_is_locked() {
        val mockPrefix = "scenario_1_1_7"
        networkRule.enqueue(path("oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("$mockPrefix/interact.json")
        }
        networkRule.enqueue(path("idp/idx/introspect")) { response ->
            response.testBodyFromFile("$mockPrefix/introspect.json")
        }
        networkRule.enqueue(path("idp/idx/identify")) { response ->
            response.testBodyFromFile("$mockPrefix/identify.json")
        }
        networkRule.enqueue(path("idp/idx/challenge/answer")) { response ->
            response.testBodyFromFile("$mockPrefix/answer.json")
            response.setResponseCode(400)
            response.addHeader("content-type", "application/json")
        }

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.login_button)).perform(click())
        waitForElement(USERNAME_EDIT_TEXT)

        onView(withId(R.id.username_edit_text)).perform(replaceText("mary@example.com"))
        onView(withId(R.id.password_edit_text)).perform(replaceText("superSecret"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("Authentication failed")))
    }
}
