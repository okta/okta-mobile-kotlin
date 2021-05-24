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
import com.okta.idx.android.infrastructure.espresso.selectAuthenticator
import com.okta.idx.android.infrastructure.espresso.waitForElement
import com.okta.idx.android.infrastructure.network.NetworkRule
import com.okta.idx.android.infrastructure.network.testBodyFromFile
import com.okta.idx.android.network.mock.OktaMockWebServer
import com.okta.idx.android.network.mock.RequestMatchers.bodyWithJsonPath
import com.okta.idx.android.network.mock.RequestMatchers.path
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelfServiceRegistrationTest {
    companion object {
        private const val ID_TOKEN_TYPE_TEXT_VIEW = "com.okta.idx.android:id/token_type"
        private const val CODE_EDIT_TEXT = "com.okta.idx.android:id/code_edit_text"
        private const val CONFIRMED_PASSWORD_EDIT_TEXT =
            "com.okta.idx.android:id/confirmed_password_edit_text"
        private const val SELECT_BUTTON = "com.okta.idx.android:id/select_button"
        private const val FIRST_NAME_EDIT_TEXT = "com.okta.idx.android:id/first_name_edit_text"
        private const val PHONE_EDIT_TEXT = "com.okta.idx.android:id/phone_edit_text"
    }

    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    @get:Rule val networkRule = NetworkRule()

    @Before fun setup() {
        OktaMockWebServer.dispatcher.consumeResponses = true
    }

    // Mary signs up for an account with Password, sets up required Email factor, then skips
    // optional SMS.
    @Test fun scenario_4_1_1() {
        val mockPrefix = "scenario_4_1_1"
        networkRule.enqueue(path("oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("$mockPrefix/interact.json")
        }
        networkRule.enqueue(path("idp/idx/introspect")) { response ->
            response.testBodyFromFile("$mockPrefix/introspect.json")
        }
        networkRule.enqueue(path("idp/idx/enroll")) { response ->
            response.testBodyFromFile("$mockPrefix/enroll.json")
        }
        networkRule.enqueue(path("idp/idx/enroll/new")) { response ->
            response.testBodyFromFile("$mockPrefix/enrollNew.json")
        }
        networkRule.enqueue(
            path("idp/idx/credential/enroll"),
            bodyWithJsonPath("/authenticator/id") {
                it.textValue() == "autkx2th9yt4eRdhM5d6"
            }) { response ->
            response.testBodyFromFile("$mockPrefix/credentialEnrollPassword.json")
        }
        networkRule.enqueue(
            path("idp/idx/challenge/answer"),
            bodyWithJsonPath("/credentials/passcode") {
                it.textValue() == "Abcd1234"
            }) { response ->
            response.testBodyFromFile("$mockPrefix/challengeAnswerPassword.json")
        }
        networkRule.enqueue(
            path("idp/idx/credential/enroll"),
            bodyWithJsonPath("/authenticator/id") {
                it.textValue() == "autkx2thaMq4XkX2I5d6"
            }) { response ->
            response.testBodyFromFile("$mockPrefix/credentialEnrollEmail.json")
        }
        networkRule.enqueue(
            path("idp/idx/challenge/answer"),
            bodyWithJsonPath("/credentials/passcode") {
                it.textValue() == "471537"
            }) { response ->
            response.testBodyFromFile("$mockPrefix/challengeAnswerEmail.json")
        }
        networkRule.enqueue(path("idp/idx/skip")) { response ->
            response.testBodyFromFile("$mockPrefix/skip.json")
        }
        networkRule.enqueue(path("oauth2/v1/token")) { response ->
            response.testBodyFromFile("$mockPrefix/tokens.json")
        }
        networkRule.enqueue(path("oauth2/default/v1/userinfo")) { response ->
            response.testBodyFromFile("$mockPrefix/userinfo.json")
        }

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.self_service_registration_button)).perform(click())
        waitForElement(FIRST_NAME_EDIT_TEXT)

        onView(withId(R.id.first_name_edit_text)).perform(replaceText("Mary"))
        onView(withId(R.id.last_name_edit_text)).perform(replaceText("Jo"))
        onView(withId(R.id.primary_email_edit_text)).perform(replaceText("Mary@example.com"))
        onView(withId(R.id.register_button)).perform(click())

        waitForElement(SELECT_BUTTON)
        selectAuthenticator("Password")

        waitForElement(CONFIRMED_PASSWORD_EDIT_TEXT)
        onView(withId(R.id.password_edit_text)).perform(replaceText("Abcd1234"))
        onView(withId(R.id.confirmed_password_edit_text)).perform(replaceText("Abcd1234"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(SELECT_BUTTON)
        selectAuthenticator("Email")

        waitForElement(CODE_EDIT_TEXT)
        onView(withId(R.id.code_edit_text)).perform(replaceText("471537"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(SELECT_BUTTON)
        onView(withId(R.id.skip_button)).perform(click())

        waitForElement(ID_TOKEN_TYPE_TEXT_VIEW)
        onView(withText("Token Type:")).check(matches(isDisplayed()))
        onView(withText("Bearer")).check(matches(isDisplayed()))
    }

    // Mary signs up for an account with Password, setups up required Email factor, AND sets up
    // optional SMS
    @Test fun scenario_4_1_2() {
        val mockPrefix = "scenario_4_1_2"
        networkRule.enqueue(path("oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("$mockPrefix/interact.json")
        }
        networkRule.enqueue(path("idp/idx/introspect")) { response ->
            response.testBodyFromFile("$mockPrefix/introspect.json")
        }
        networkRule.enqueue(path("idp/idx/enroll")) { response ->
            response.testBodyFromFile("$mockPrefix/enroll.json")
        }
        networkRule.enqueue(path("idp/idx/enroll/new")) { response ->
            response.testBodyFromFile("$mockPrefix/enrollNew.json")
        }
        networkRule.enqueue(
            path("idp/idx/credential/enroll"),
            bodyWithJsonPath("/authenticator/id") {
                it.textValue() == "autkx2th9yt4eRdhM5d6"
            }) { response ->
            response.testBodyFromFile("$mockPrefix/credentialEnrollPassword.json")
        }
        networkRule.enqueue(
            path("idp/idx/challenge/answer"),
            bodyWithJsonPath("/credentials/passcode") {
                it.textValue() == "Abcd1234"
            }) { response ->
            response.testBodyFromFile("$mockPrefix/challengeAnswerPassword.json")
        }
        networkRule.enqueue(
            path("idp/idx/credential/enroll"),
            bodyWithJsonPath("/authenticator/id") {
                it.textValue() == "autkx2thaMq4XkX2I5d6"
            }) { response ->
            response.testBodyFromFile("$mockPrefix/credentialEnrollEmail.json")
        }
        networkRule.enqueue(
            path("idp/idx/challenge/answer"),
            bodyWithJsonPath("/credentials/passcode") {
                it.textValue() == "471537"
            }) { response ->
            response.testBodyFromFile("$mockPrefix/challengeAnswerEmail.json")
        }
        networkRule.enqueue(
            path("idp/idx/credential/enroll"),
            bodyWithJsonPath("/authenticator/id") {
                it.textValue() == "autkx2thbuHB4hZa75d6"
            }) { response ->
            response.testBodyFromFile("$mockPrefix/credentialEnrollPhone.json")
        }
        networkRule.enqueue(
            path("idp/idx/credential/enroll"),
            bodyWithJsonPath("/authenticator/id") {
                it.textValue() == "autkx2thbuHB4hZa75d6"
            }, bodyWithJsonPath("/authenticator/methodType") {
                it.textValue() == "sms"
            }) { response ->
            response.testBodyFromFile("$mockPrefix/credentialEnrollSms.json")
        }
        networkRule.enqueue(
            path("idp/idx/credential/enroll"),
            bodyWithJsonPath("/authenticator/id") {
                it.textValue() == "autkx2thbuHB4hZa75d6"
            }, bodyWithJsonPath("/authenticator/methodType") {
                it.textValue() == "sms"
            }, bodyWithJsonPath("/authenticator/phoneNumber") {
                it.textValue() == "+14021234567"
            }) { response ->
            response.testBodyFromFile("$mockPrefix/credentialEnrollPhoneNumber.json")
        }
        networkRule.enqueue(
            path("idp/idx/challenge/answer"),
            bodyWithJsonPath("/credentials/passcode") {
                it.textValue() == "134165"
            }) { response ->
            response.testBodyFromFile("$mockPrefix/challengeAnswerPhone.json")
        }
        networkRule.enqueue(path("oauth2/v1/token")) { response ->
            response.testBodyFromFile("$mockPrefix/tokens.json")
        }
        networkRule.enqueue(path("oauth2/default/v1/userinfo")) { response ->
            response.testBodyFromFile("$mockPrefix/userinfo.json")
        }

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.self_service_registration_button)).perform(click())
        waitForElement(FIRST_NAME_EDIT_TEXT)

        onView(withId(R.id.first_name_edit_text)).perform(replaceText("Mary"))
        onView(withId(R.id.last_name_edit_text)).perform(replaceText("Jo"))
        onView(withId(R.id.primary_email_edit_text)).perform(replaceText("Mary@example.com"))
        onView(withId(R.id.register_button)).perform(click())

        waitForElement(SELECT_BUTTON)
        selectAuthenticator("Password")

        waitForElement(CONFIRMED_PASSWORD_EDIT_TEXT)
        onView(withId(R.id.password_edit_text)).perform(replaceText("Abcd1234"))
        onView(withId(R.id.confirmed_password_edit_text)).perform(replaceText("Abcd1234"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(SELECT_BUTTON)
        selectAuthenticator("Email")

        waitForElement(CODE_EDIT_TEXT)
        onView(withId(R.id.code_edit_text)).perform(replaceText("471537"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(SELECT_BUTTON)
        selectAuthenticator("Phone")

        waitForElement(SELECT_BUTTON)
        selectAuthenticator("SMS")

        waitForElement(PHONE_EDIT_TEXT)
        onView(withId(R.id.phone_edit_text)).perform(replaceText("+14021234567"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(CODE_EDIT_TEXT)
        onView(withId(R.id.code_edit_text)).perform(replaceText("134165"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ID_TOKEN_TYPE_TEXT_VIEW)
        onView(withText("Token Type:")).check(matches(isDisplayed()))
        onView(withText("Bearer")).check(matches(isDisplayed()))
    }
}
