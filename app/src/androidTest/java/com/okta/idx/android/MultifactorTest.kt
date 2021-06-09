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

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.okta.idx.android.infrastructure.CODE_EDIT_TEXT
import com.okta.idx.android.infrastructure.ERROR_TEXT_VIEW
import com.okta.idx.android.infrastructure.ID_TOKEN_TYPE_TEXT_VIEW
import com.okta.idx.android.infrastructure.PHONE_EDIT_TEXT
import com.okta.idx.android.infrastructure.SELECT_BUTTON
import com.okta.idx.android.infrastructure.espresso.selectAuthenticator
import com.okta.idx.android.infrastructure.espresso.waitForElement
import com.okta.idx.android.infrastructure.network.testBodyFromFile
import com.okta.idx.android.network.mock.RequestMatchers.bodyWithJsonPath
import com.okta.idx.android.network.mock.RequestMatchers.path
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultifactorTest : BaseMainActivityTest() {
    // 2FA Login with Email
    @Test fun scenario_6_1_2() {
        val mockPrefix = "scenario_6_1_2"
        networkRule.enqueue(path("oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("$mockPrefix/interact.json")
        }
        networkRule.enqueue(path("idp/idx/introspect")) { response ->
            response.testBodyFromFile("$mockPrefix/introspect.json")
        }
        networkRule.enqueue(path("idp/idx/identify")) { response ->
            response.testBodyFromFile("$mockPrefix/identify.json")
        }
        networkRule.enqueue(path("idp/idx/challenge")) { response ->
            response.testBodyFromFile("$mockPrefix/challenge.json")
        }
        networkRule.enqueue(path("idp/idx/challenge/answer")) { response ->
            response.testBodyFromFile("$mockPrefix/challengeAnswer.json")
        }
        networkRule.enqueue(path("oauth2/v1/token")) { response ->
            response.testBodyFromFile("$mockPrefix/tokens.json")
        }
        networkRule.enqueue(path("oauth2/default/v1/userinfo")) { response ->
            response.testBodyFromFile("$mockPrefix/userinfo.json")
        }

        goToLogin()

        onView(withId(R.id.username_edit_text)).perform(replaceText("Mary@example.com"))
        onView(withId(R.id.password_edit_text)).perform(replaceText("superSecret"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(SELECT_BUTTON)
        selectAuthenticator("Email")

        waitForElement(CODE_EDIT_TEXT)
        onView(withId(R.id.code_edit_text)).perform(replaceText("471537"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ID_TOKEN_TYPE_TEXT_VIEW)
        onView(withText("Token Type:")).check(matches(isDisplayed()))
        onView(withText("Bearer")).check(matches(isDisplayed()))
    }

    // Mary enters a wrong verification code
    @Test fun scenario_6_1_3() {
        val mockPrefix = "scenario_6_1_3"
        networkRule.enqueue(path("oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("$mockPrefix/interact.json")
        }
        networkRule.enqueue(path("idp/idx/introspect")) { response ->
            response.testBodyFromFile("$mockPrefix/introspect.json")
        }
        networkRule.enqueue(path("idp/idx/identify")) { response ->
            response.testBodyFromFile("$mockPrefix/identify.json")
        }
        networkRule.enqueue(path("idp/idx/challenge")) { response ->
            response.testBodyFromFile("$mockPrefix/challenge.json")
        }
        networkRule.enqueue(path("idp/idx/challenge/answer")) { response ->
            response.testBodyFromFile("$mockPrefix/challengeAnswer.json")
            response.setResponseCode(401)
            response.addHeader("content-type", "application/json")
        }

        goToLogin()

        onView(withId(R.id.username_edit_text)).perform(replaceText("Mary@example.com"))
        onView(withId(R.id.password_edit_text)).perform(replaceText("superSecret"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(SELECT_BUTTON)
        selectAuthenticator("Email")

        waitForElement(CODE_EDIT_TEXT)
        onView(withId(R.id.code_edit_text)).perform(replaceText("471537"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("Invalid code. Try again.")))
    }

    // Enroll in SMS Factor prompt when authenticating
    @Test fun scenario_6_2_1() {
        val mockPrefix = "scenario_6_2_1"
        networkRule.enqueue(path("oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("$mockPrefix/interact.json")
        }
        networkRule.enqueue(path("idp/idx/introspect")) { response ->
            response.testBodyFromFile("$mockPrefix/introspect.json")
        }
        networkRule.enqueue(path("idp/idx/identify")) { response ->
            response.testBodyFromFile("$mockPrefix/identify.json")
        }
        networkRule.enqueue(path("idp/idx/challenge")) { response ->
            response.testBodyFromFile("$mockPrefix/challenge.json")
        }
        networkRule.enqueue(path("idp/idx/challenge/answer"),
            bodyWithJsonPath("/credentials/passcode") {
                it.textValue().equals("471537")
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

        goToLogin()

        onView(withId(R.id.username_edit_text)).perform(replaceText("Mary@example.com"))
        onView(withId(R.id.password_edit_text)).perform(replaceText("superSecret"))
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

    // 2FA Login with SMS
    @Test fun scenario_6_2_2() {
        val mockPrefix = "scenario_6_2_2"
        networkRule.enqueue(path("oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("$mockPrefix/interact.json")
        }
        networkRule.enqueue(path("idp/idx/introspect")) { response ->
            response.testBodyFromFile("$mockPrefix/introspect.json")
        }
        networkRule.enqueue(path("idp/idx/identify")) { response ->
            response.testBodyFromFile("$mockPrefix/identify.json")
        }
        networkRule.enqueue(
            path("idp/idx/challenge"),
            bodyWithJsonPath("/authenticator/id") {
                it.textValue() == "autkx2thbuHB4hZa75d6"
            },
        ) { response ->
            response.testBodyFromFile("$mockPrefix/challengePhone.json")
        }
        networkRule.enqueue(path("idp/idx/challenge"),
            bodyWithJsonPath("/authenticator/id") {
                it.textValue() == "autkx2thbuHB4hZa75d6"
            }, bodyWithJsonPath("/authenticator/methodType") {
                it.textValue() == "sms"
            }, bodyWithJsonPath("/authenticator/enrollmentId") {
                it.textValue() == "paeog6clbQQnUKrwV5d6"
            }) { response ->
            response.testBodyFromFile("$mockPrefix/challengeSms.json")
        }
        networkRule.enqueue(path("idp/idx/challenge/answer")) { response ->
            response.testBodyFromFile("$mockPrefix/challengeAnswer.json")
        }
        networkRule.enqueue(path("oauth2/v1/token")) { response ->
            response.testBodyFromFile("$mockPrefix/tokens.json")
        }
        networkRule.enqueue(path("oauth2/default/v1/userinfo")) { response ->
            response.testBodyFromFile("$mockPrefix/userinfo.json")
        }

        goToLogin()

        onView(withId(R.id.username_edit_text)).perform(replaceText("Mary@example.com"))
        onView(withId(R.id.password_edit_text)).perform(replaceText("superSecret"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(SELECT_BUTTON)
        selectAuthenticator("Phone")

        waitForElement(SELECT_BUTTON)
        selectAuthenticator("SMS")

        waitForElement(CODE_EDIT_TEXT)
        onView(withId(R.id.code_edit_text)).perform(replaceText("471537"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ID_TOKEN_TYPE_TEXT_VIEW)
        onView(withText("Token Type:")).check(matches(isDisplayed()))
        onView(withText("Bearer")).check(matches(isDisplayed()))
    }

    // Enroll with Invalid Phone Number
    @Test fun scenario_6_2_3() {
        val mockPrefix = "scenario_6_2_3"
        networkRule.enqueue(path("oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("$mockPrefix/interact.json")
        }
        networkRule.enqueue(path("idp/idx/introspect")) { response ->
            response.testBodyFromFile("$mockPrefix/introspect.json")
        }
        networkRule.enqueue(path("idp/idx/identify")) { response ->
            response.testBodyFromFile("$mockPrefix/identify.json")
        }
        networkRule.enqueue(path("idp/idx/challenge")) { response ->
            response.testBodyFromFile("$mockPrefix/challenge.json")
        }
        networkRule.enqueue(path("idp/idx/challenge/answer"),
            bodyWithJsonPath("/credentials/passcode") {
                it.textValue().equals("471537")
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
                it.textValue() == "+14021234567890123"
            }) { response ->
            response.testBodyFromFile("$mockPrefix/credentialEnrollPhoneNumber.json")
            response.setResponseCode(400)
            response.addHeader("content-type", "application/json")
        }

        goToLogin()

        onView(withId(R.id.username_edit_text)).perform(replaceText("Mary@example.com"))
        onView(withId(R.id.password_edit_text)).perform(replaceText("superSecret"))
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
        onView(withId(R.id.phone_edit_text)).perform(replaceText("+14021234567890123"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("Unable to initiate factor enrollment: Invalid Phone Number.")))
    }

    // Mary enters a wrong verification code on verify
    @Test fun scenario_6_2_4() {
        val mockPrefix = "scenario_6_2_4"
        networkRule.enqueue(path("oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("$mockPrefix/interact.json")
        }
        networkRule.enqueue(path("idp/idx/introspect")) { response ->
            response.testBodyFromFile("$mockPrefix/introspect.json")
        }
        networkRule.enqueue(path("idp/idx/identify")) { response ->
            response.testBodyFromFile("$mockPrefix/identify.json")
        }
        networkRule.enqueue(
            path("idp/idx/challenge"),
            bodyWithJsonPath("/authenticator/id") {
                it.textValue() == "autkx2thbuHB4hZa75d6"
            },
        ) { response ->
            response.testBodyFromFile("$mockPrefix/challengePhone.json")
        }
        networkRule.enqueue(path("idp/idx/challenge"),
            bodyWithJsonPath("/authenticator/id") {
                it.textValue() == "autkx2thbuHB4hZa75d6"
            }, bodyWithJsonPath("/authenticator/methodType") {
                it.textValue() == "sms"
            }, bodyWithJsonPath("/authenticator/enrollmentId") {
                it.textValue() == "paeog6clbQQnUKrwV5d6"
            }) { response ->
            response.testBodyFromFile("$mockPrefix/challengeSms.json")
        }
        networkRule.enqueue(path("idp/idx/challenge/answer")) { response ->
            response.testBodyFromFile("$mockPrefix/challengeAnswer.json")
            response.setResponseCode(401)
            response.addHeader("content-type", "application/json")
        }

        goToLogin()

        onView(withId(R.id.username_edit_text)).perform(replaceText("Mary@example.com"))
        onView(withId(R.id.password_edit_text)).perform(replaceText("superSecret"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(SELECT_BUTTON)
        selectAuthenticator("Phone")

        waitForElement(SELECT_BUTTON)
        selectAuthenticator("SMS")

        waitForElement(CODE_EDIT_TEXT)
        onView(withId(R.id.code_edit_text)).perform(replaceText("11"))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("Invalid code. Try again.")))
    }
}
