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
package com.okta.idx.android.cucumber.definitions

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.okta.idx.android.R
import com.okta.idx.android.infrastructure.ERROR_TEXT_VIEW
import com.okta.idx.android.infrastructure.EndToEndCredentials
import com.okta.idx.android.infrastructure.SIGN_OUT_BUTTON
import com.okta.idx.android.infrastructure.USERNAME_EDIT_TEXT
import com.okta.idx.android.infrastructure.espresso.selectAuthenticator
import com.okta.idx.android.infrastructure.espresso.waitForElement
import io.cucumber.java.en.And
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

class LoginDefinitions {
    @When("^she fills in her correct username$") fun enter_correct_username() {
        waitForElement(USERNAME_EDIT_TEXT)

        onView(withId(R.id.username_edit_text)).perform(replaceText(EndToEndCredentials["/cucumber/username"]))
    }

    @And("^she fills in her correct password$") fun enter_correct_password() {
        onView(withId(R.id.password_edit_text)).perform(replaceText(EndToEndCredentials["/cucumber/password"]))
    }

    @And("^she submits the Login form$") fun clicks_login_button() {
        onView(withId(R.id.submit_button)).perform(click())
    }

    @When("^she fills in her incorrect username$") fun enter_incorrect_username() {
        waitForElement(USERNAME_EDIT_TEXT)

        onView(withId(R.id.username_edit_text)).perform(replaceText(EndToEndCredentials["/cucumber/invalidUsername"]))
    }

    @Then("^she should see a \"There is no account with username\" message on the Login form$")
    fun no_account_user_error() {
        waitForElement(ERROR_TEXT_VIEW)
        val invalidUsername = EndToEndCredentials["/cucumber/invalidUsername"]
        onView(withId(R.id.error_text_view)).check(matches(withText("There is no account with the Username $invalidUsername.")))
    }

    @When("^she fills in her incorrect password$") fun enter_incorrect_password() {
        onView(withId(R.id.password_edit_text)).perform(replaceText(EndToEndCredentials["/cucumber/invalidPassword"]))
    }

    @Then("^she should see the message \"Authentication failed\"$")
    fun authentication_failed_message() {
        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("Authentication failed")))
    }

    @When("^she clicks on the \"Forgot Password Link\"$")
    fun clicks_forgot_password_link() {
        waitForElement(USERNAME_EDIT_TEXT)

        onView(withId(R.id.forgot_password_button)).perform(click())
    }

    @Then("^she is redirected to the Self Service Password Reset View$")
    fun redirect_to_sspr_view() {
        waitForElement(SIGN_OUT_BUTTON)
        onView(withId(R.id.title_text_view)).check(matches(withText("Forgot Password")))
    }

    @Then("^she clicks the \"Login with Facebook\" button$")
    fun she_clicks_the_login_with_facebook_button() {
        waitForElement(USERNAME_EDIT_TEXT)

        selectAuthenticator("Login With Facebook")
    }
}
