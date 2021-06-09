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
import com.okta.idx.android.infrastructure.CONFIRMED_PASSWORD_EDIT_TEXT
import com.okta.idx.android.infrastructure.ERROR_TEXT_VIEW
import com.okta.idx.android.infrastructure.ID_TOKEN_TYPE_TEXT_VIEW
import com.okta.idx.android.infrastructure.SELECT_BUTTON
import com.okta.idx.android.infrastructure.espresso.selectAuthenticator
import com.okta.idx.android.infrastructure.espresso.waitForElement
import com.okta.idx.android.infrastructure.network.testBodyFromFile
import com.okta.idx.android.network.mock.RequestMatchers.bodyWithJsonPath
import com.okta.idx.android.network.mock.RequestMatchers.path
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelfServicePasswordRecoveryTest : BaseMainActivityTest() {
    // Mary resets her password
    @Test fun scenario_3_1_1() {
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
        networkRule.enqueue(
            path("idp/idx/challenge/answer"),
            bodyWithJsonPath("/credentials/passcode") {
                it.textValue().equals("123456")
            }) { response ->
            response.testBodyFromFile("$mockPrefix/answerEmailCode.json")
        }
        networkRule.enqueue(
            path("idp/idx/challenge/answer"),
            bodyWithJsonPath("/credentials/passcode") {
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

        goToLogin()

        onView(withId(R.id.forgot_password_button)).perform(click())
        onView(withId(R.id.username_edit_text)).perform(replaceText("Mary@example.com"))
        onView(withId(R.id.forgot_password_button)).perform(click())

        waitForElement(SELECT_BUTTON)
        selectAuthenticator("Email")

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

    // Mary tries to reset a password with the wrong email
    @Test fun scenario_3_1_2() {
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

        goToLogin()

        onView(withId(R.id.forgot_password_button)).perform(click())
        onView(withId(R.id.username_edit_text)).perform(replaceText("Mary@unknown.com"))
        onView(withId(R.id.forgot_password_button)).perform(click())

        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("There is no account with the Username Mary@unknown.com.")))
    }
}
