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
import com.okta.idx.android.cucumber.hooks.SharedState
import com.okta.idx.android.infrastructure.CONFIRMED_PASSWORD_EDIT_TEXT
import com.okta.idx.android.infrastructure.ERROR_TEXT_VIEW
import com.okta.idx.android.infrastructure.EndToEndCredentials
import com.okta.idx.android.infrastructure.SELECT_BUTTON
import com.okta.idx.android.infrastructure.USERNAME_EDIT_TEXT
import com.okta.idx.android.infrastructure.espresso.selectAuthenticator
import com.okta.idx.android.infrastructure.espresso.waitForElement
import io.cucumber.java.en.And
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

class PasswordRecoveryDefinitions {
    @When("^she inputs her correct Email$") fun she_inputs_her_correct_email() {
        waitForElement(USERNAME_EDIT_TEXT)

        val emailAddress = SharedState.a18NProfile!!.emailAddress
        onView(withId(R.id.username_edit_text)).perform(replaceText(emailAddress))
    }

    @And("^she submits the recovery form$") fun she_submits_the_recovery_form() {
        onView(withId(R.id.forgot_password_button)).perform(click())
    }

    @Then("^she sees the list of authenticators$")
    fun she_sees_the_list_of_authenticators() {
        waitForElement(SELECT_BUTTON)
    }

    @When("^she selects Email authenticator$")
    fun she_selects_email_authenticator() {
        selectAuthenticator("Email")
    }

    @Then("^she sees a page to set her password$")
    fun she_sees_a_page_to_set_her_password() {
        waitForElement(CONFIRMED_PASSWORD_EDIT_TEXT)
    }

    @When("^she fills a password that fits within the password policy$")
    fun she_fills_a_password_that_fits_within_the_password_policy() {
        val password = EndToEndCredentials["/cucumber/newPassword"]
        onView(withId(R.id.password_edit_text)).perform(replaceText(password))
    }

    @And("^she confirms that password$")
    fun she_confirms_that_password() {
        val password = EndToEndCredentials["/cucumber/newPassword"]
        onView(withId(R.id.confirmed_password_edit_text)).perform(replaceText(password))
    }

    @And("^she submits the form$")
    fun she_submits_the_form() {
        onView(withId(R.id.submit_button)).perform(click())
    }

    @When("^she inputs an Email that doesn't exist$")
    fun she_inputs_an_email_that_does_not_exist() {
        onView(withId(R.id.username_edit_text)).perform(replaceText("mary@unknown.com"))
    }

    @Then("^she sees a message \"There is no account with the Username mary@unknown.com.\"$")
    fun she_sees_a_message_no_account() {
        waitForElement(ERROR_TEXT_VIEW)

        val message = "There is no account with the Username mary@unknown.com."
        onView(withId(R.id.error_text_view)).check(matches(withText(message)))
    }
}
