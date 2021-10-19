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
import com.okta.idx.android.infrastructure.espresso.authenticatorViewInteraction
import com.okta.idx.android.infrastructure.espresso.first
import com.okta.idx.android.infrastructure.espresso.selectAuthenticator
import com.okta.idx.android.infrastructure.espresso.waitForElement
import com.okta.idx.android.infrastructure.espresso.waitForElementToBeGone
import com.okta.idx.android.infrastructure.espresso.waitForElementWithText
import io.cucumber.java.en.And
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

class MfaDefinitions {
    @When("^she fills in her correct username for mfa$")
    fun she_fills_in_her_correct_username_for_mfa() {
        waitForElementToBeGone(PROGRESS_BAR_VIEW)
        waitForElement(ACTION_BUTTON_VIEW)

        onView(withHint("Username")).perform(replaceText(SharedState.a18NProfile!!.emailAddress))
    }

    @And("^she fills in her correct password for mfa$")
    fun she_fills_in_her_correct_password_for_mfa() {
        onView(withHint("Password")).perform(replaceText(EndToEndCredentials["/cucumber/password"]))
    }

    @Then("^she is presented with an option to select Email to verify$")
    fun she_is_presented_with_an_option_to_select_email_to_verify() {
        authenticatorViewInteraction("Email").check(matches(isDisplayed()))
    }

    @When("^she selects Email$")
    fun she_selects_email() {
        selectAuthenticator("Email")
        onView(withText("Choose")).perform(click())
    }

    @Then("^she is presented with a list of factors$")
    fun she_is_presented_with_a_list_of_factors() {
        waitForElementWithText("Choose")
    }

    @When("^she selects Phone from the list$")
    fun she_selects_phone_from_the_list() {
        selectAuthenticator("Phone")
    }

    @And("^she inputs a valid phone number$")
    fun she_inputs_a_valid_phone_number() {
        waitForElementWithText("Phone")

        val phoneNumber = SharedState.a18NProfile!!.phoneNumber
        onView(withHint("Phone number")).perform(replaceText(phoneNumber))
    }

    @And("^submits the enrollment form$")
    fun she_submits_the_enrollment_form() {
        onView(first(withText("Continue"))).perform(click())
    }

    @And("^she selects SMS$")
    fun she_selects_sms() {
        selectAuthenticator("SMS")
    }

    @And("^she submits the phone mode form$")
    fun she_submits_the_phone_mode_form() {
        onView(withText("Continue")).perform(click())
    }

    @Then("^she is presented with an option to select SMS to verify$")
    fun she_is_presented_with_an_option_to_select_SMS_to_verify() {
        authenticatorViewInteraction("SMS").check(matches(isDisplayed()))
    }

    @When("^she selects SMS from the list$")
    fun she_selects_SMS_from_the_list() {
        selectAuthenticator("SMS")
    }

    @And("^she inputs an invalid phone number$")
    fun she_inputs_an_invalid_phone_number() {
        waitForElementWithText("Phone")

        onView(withHint("Phone")).perform(replaceText("123"))
    }

    @Then("^she should see a message \"Unable to initiate factor enrollment: Invalid Phone Number\"$")
    fun she_should_see_a_message_invalid_phone_number() {
        waitForElement(ERROR_TEXT_VIEW)
        onView(withText("Invalid Phone Number.")).check(matches(isDisplayed()))
    }

    @When("^she selects \"Skip\" on SMS$")
    fun she_selects_skip_on_sms() {
        waitForElementWithText("Skip")
        onView(withText("Skip")).perform(click())
    }

    @When("^she selects skip$")
    fun she_selects_skip() {
        waitForElementWithText("Skip")
        onView(withText("Skip")).perform(click())
    }

    @And("^She submits the form by clicking the Choose button$")
    fun she_submits_the_form_by_clicking_the_button() {
        val buttonText = "Choose"
        waitForElementWithText(buttonText)
        onView(withText(buttonText)).perform(click())
    }
}
