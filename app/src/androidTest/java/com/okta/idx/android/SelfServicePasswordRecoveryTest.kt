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
import androidx.test.espresso.matcher.ViewMatchers.withChild
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.okta.idx.android.infrastructure.network.NetworkRule
import com.okta.idx.android.infrastructure.network.testBodyFromFile
import com.okta.idx.android.network.mock.OktaMockWebServer
import com.okta.idx.android.network.mock.RequestMatchers.bodyWithJsonPath
import com.okta.idx.android.network.mock.RequestMatchers.path
import org.hamcrest.Matchers.allOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelfServicePasswordRecoveryTest {
    companion object {
        private const val ERROR_TEXT_VIEW = "com.okta.idx.android:id/error_text_view"
        private const val ID_TOKEN_TYPE_TEXT_VIEW = "com.okta.idx.android:id/token_type"
        private const val CODE_EDIT_TEXT = "com.okta.idx.android:id/code_edit_text"
        private const val CONFIRMED_PASSWORD_EDIT_TEXT = "com.okta.idx.android:id/confirmed_password_edit_text"
        private const val SELECT_BUTTON = "com.okta.idx.android:id/select_button"
        private const val USERNAME_EDIT_TEXT = "com.okta.idx.android:id/username_edit_text"
    }

    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    @get:Rule val networkRule = NetworkRule()

    private lateinit var uiDevice: UiDevice

    @Before fun setup() {
        OktaMockWebServer.dispatcher.consumeResponses = true
        uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    private fun waitForElement(resourceId: String) {
        uiDevice.findObject(UiSelector().resourceId(resourceId)).waitForExists(10_000)
    }

    @Test fun scenario_3_1_1_Mary_resets_her_password_from_login_page() {
        val mockPrefix = "scenario_3_1_1"
        networkRule.enqueue(path("oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("$mockPrefix/interact.json")
        }
        networkRule.enqueue(path("idp/idx/introspect")) { response ->
            response.testBodyFromFile("$mockPrefix/introspect.json")
        }
        networkRule.enqueue(path("idp/idx/recover")) { response ->
            response.testBodyFromFile("$mockPrefix/recover.json")
        }
        networkRule.enqueue(path("idp/idx/identify")) { response ->
            response.testBodyFromFile("$mockPrefix/identify.json")
        }
        networkRule.enqueue(path("idp/idx/challenge")) { response ->
            response.testBodyFromFile("$mockPrefix/challenge.json")
        }
        networkRule.enqueue(path("idp/idx/challenge/answer"), bodyWithJsonPath("/credentials/passcode"){
            it.textValue().equals("123456")
        }) { response ->
            response.testBodyFromFile("$mockPrefix/answerEmailCode.json")
        }
        networkRule.enqueue(path("idp/idx/challenge/answer"), bodyWithJsonPath("/credentials/passcode"){
            it.textValue().equals("abc123")
        }) { response ->
            response.testBodyFromFile("$mockPrefix/answerNewPassword.json")
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

        onView(withId(R.id.forgot_password_button)).perform(click())
        onView(withId(R.id.username_edit_text)).perform(replaceText("Mary@example.com"))
        onView(withId(R.id.forgot_password_button)).perform(click())

        waitForElement(SELECT_BUTTON)
        onView(allOf(withParent(withChild(withText("Email"))), withId(R.id.select_button))).perform(click())

        waitForElement(CODE_EDIT_TEXT)
        onView(withId(R.id.code_edit_text)).perform(replaceText("123456"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(CONFIRMED_PASSWORD_EDIT_TEXT)
        onView(withId(R.id.password_edit_text)).perform(replaceText("abc123"))
        onView(withId(R.id.confirmed_password_edit_text)).perform(replaceText("abc123"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ID_TOKEN_TYPE_TEXT_VIEW)
        onView(withText("Token Type:")).check(matches(isDisplayed()))
        onView(withText("Bearer")).check(matches(isDisplayed()))
    }

    @Test fun scenario_3_1_2_Mary_tries_to_reset_a_password_with_the_wrong_email() {
        val mockPrefix = "scenario_3_1_2"
        networkRule.enqueue(path("oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("$mockPrefix/interact.json")
        }
        networkRule.enqueue(path("idp/idx/introspect")) { response ->
            response.testBodyFromFile("$mockPrefix/introspect.json")
        }
        networkRule.enqueue(path("idp/idx/recover")) { response ->
            response.testBodyFromFile("$mockPrefix/recover.json")
        }
        networkRule.enqueue(path("idp/idx/identify")) { response ->
            response.testBodyFromFile("$mockPrefix/identify.json")
        }

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.login_button)).perform(click())
        waitForElement(USERNAME_EDIT_TEXT)

        onView(withId(R.id.forgot_password_button)).perform(click())
        onView(withId(R.id.username_edit_text)).perform(replaceText("Mary@unknown.com"))
        onView(withId(R.id.forgot_password_button)).perform(click())

        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("There is no account with the Username Mary@unknown.com.")))
    }
}
