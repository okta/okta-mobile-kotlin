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
import com.okta.idx.android.infrastructure.ID_TOKEN_TYPE_TEXT_VIEW
import com.okta.idx.android.infrastructure.SIGN_OUT_BUTTON
import com.okta.idx.android.infrastructure.USERNAME_EDIT_TEXT
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
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    @get:Rule val networkRule = NetworkRule()

    @Before fun setup() {
        OktaMockWebServer.dispatcher.consumeResponses = true
    }

    // Mary logs in with a Password
    @Test fun scenario_1_1_1() {
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

    // Mary doesn't know her username
    @Test fun scenario_1_1_2() {
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

    // Mary doesn't know her password
    @Test fun scenario_1_1_3() {
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

    // Mary is not assigned to the application
    @Test fun scenario_1_1_4() {
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

    // Mary's account is suspended
    @Test fun scenario_1_1_5() {
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

    // Mary's account is locked
    @Test fun scenario_1_1_6() {
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

    // Mary's account is deactivated
    @Test fun scenario_1_1_7() {
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

    // Mary clicks on the "Forgot Password Link"
    @Test fun scenario_1_1_8() {
        val mockPrefix = "scenario_1_1_8"
        networkRule.enqueue(path("oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("$mockPrefix/interact.json")
        }
        networkRule.enqueue(path("idp/idx/introspect")) { response ->
            response.testBodyFromFile("$mockPrefix/introspect.json")
        }

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.login_button)).perform(click())
        waitForElement(USERNAME_EDIT_TEXT)

        onView(withId(R.id.forgot_password_button)).perform(click())

        waitForElement(SIGN_OUT_BUTTON)
        onView(withId(R.id.title_text_view)).check(matches(withText("Forgot Password")))
    }
}
