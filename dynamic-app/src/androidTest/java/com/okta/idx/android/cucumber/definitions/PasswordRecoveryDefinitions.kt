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
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.okta.idx.android.cucumber.hooks.SharedState
import com.okta.idx.android.infrastructure.ACTION_BUTTON_VIEW
import com.okta.idx.android.infrastructure.ERROR_TEXT_VIEW
import com.okta.idx.android.infrastructure.EndToEndCredentials
import com.okta.idx.android.infrastructure.PROGRESS_BAR_VIEW
import com.okta.idx.android.infrastructure.espresso.selectAuthenticator
import com.okta.idx.android.infrastructure.espresso.waitForElement
import com.okta.idx.android.infrastructure.espresso.waitForElementToBeGone
import com.okta.idx.android.infrastructure.espresso.waitForElementWithText
import io.cucumber.java.en.And
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

class PasswordRecoveryDefinitions {
    @When("^she inputs her correct Email$") fun she_inputs_her_correct_email() {
        waitForElementToBeGone(PROGRESS_BAR_VIEW)
        waitForElement(ACTION_BUTTON_VIEW)

        val emailAddress = SharedState.a18NProfile!!.emailAddress
        onView(withHint("Username")).perform(replaceText(emailAddress))
    }

    @And("^she submits the recovery form$") fun she_submits_the_recovery_form() {
        onView(withText("Continue")).perform(click())
    }

    @Then("^she sees the list of authenticators$")
    fun she_sees_the_list_of_authenticators() {
        waitForElementWithText("Choose")
    }

    @When("^she selects Email authenticator$")
    fun she_selects_email_authenticator() {
        selectAuthenticator("Email")
        onView(withText("Choose")).perform(click())
    }

    @Then("^she sees a page to set her password$")
    fun she_sees_a_page_to_set_her_password() {
        waitForElementToBeGone(PROGRESS_BAR_VIEW)
        waitForElement(ACTION_BUTTON_VIEW)
        onView(withHint("New password")).check(matches(isDisplayed()))
    }

    @When("^she fills a password that fits within the password policy$")
    fun she_fills_a_password_that_fits_within_the_password_policy() {
        val password = EndToEndCredentials["/cucumber/newPassword"]
        onView(withHint("New password")).perform(replaceText(password))
    }

    @And("^she submits the form$")
    fun she_submits_the_form() {
        onView(withText("Continue")).perform(click())
    }

    @When("^she inputs an Email that doesn't exist$")
    fun she_inputs_an_email_that_does_not_exist() {
        onView(withHint("Username")).perform(replaceText("mary@unknown.com"))
    }

    @Then("^she sees a message \"There is no account with the Username mary@unknown.com.\"$")
    fun she_sees_a_message_no_account() {
        waitForElement(ERROR_TEXT_VIEW)

        val message = "There is no account with the Username mary@unknown.com."
        onView(withText(message)).check(matches(isDisplayed()))
    }
}
