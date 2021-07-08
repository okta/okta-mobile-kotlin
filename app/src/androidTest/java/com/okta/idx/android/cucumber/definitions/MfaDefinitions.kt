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
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.okta.idx.android.R
import com.okta.idx.android.cucumber.hooks.SharedState
import com.okta.idx.android.infrastructure.ERROR_TEXT_VIEW
import com.okta.idx.android.infrastructure.EndToEndCredentials
import com.okta.idx.android.infrastructure.PHONE_EDIT_TEXT
import com.okta.idx.android.infrastructure.SELECT_BUTTON
import com.okta.idx.android.infrastructure.SKIP_BUTTON
import com.okta.idx.android.infrastructure.USERNAME_EDIT_TEXT
import com.okta.idx.android.infrastructure.espresso.authenticatorViewInteraction
import com.okta.idx.android.infrastructure.espresso.selectAuthenticator
import com.okta.idx.android.infrastructure.espresso.waitForElement
import io.cucumber.java.en.And
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

class MfaDefinitions {
    @When("^she fills in her correct username for mfa$")
    fun she_fills_in_her_correct_username_for_mfa() {
        waitForElement(USERNAME_EDIT_TEXT)

        onView(withId(R.id.username_edit_text)).perform(replaceText(SharedState.a18NProfile!!.emailAddress))
    }

    @And("^she fills in her correct password for mfa$")
    fun she_fills_in_her_correct_password_for_mfa() {
        onView(withId(R.id.password_edit_text)).perform(replaceText(EndToEndCredentials["/cucumber/password"]))
    }

    @Then("^she is presented with an option to select Email to verify$")
    fun she_is_presented_with_an_option_to_select_email_to_verify() {
        waitForElement(SELECT_BUTTON)
        authenticatorViewInteraction("Email").check(matches(isDisplayed()))
    }

    @When("^she selects Email$")
    fun she_selects_email() {
        waitForElement(SELECT_BUTTON)
        selectAuthenticator("Email")
    }

    @Then("^she is presented with a list of factors$")
    fun she_is_presented_with_a_list_of_factors() {
        waitForElement(SELECT_BUTTON)
    }

    @When("^she selects Phone from the list$")
    fun she_selects_phone_from_the_list() {
        selectAuthenticator("Phone")
    }

    @And("^she inputs a valid phone number$")
    fun she_inputs_a_valid_phone_number() {
        waitForElement(PHONE_EDIT_TEXT)

        val phoneNumber = SharedState.a18NProfile!!.phoneNumber
        onView(withId(R.id.phone_edit_text)).perform(replaceText(phoneNumber))
    }

    @And("^submits the enrollment form$")
    fun she_submits_the_enrollment_form() {
        onView(withId(R.id.submit_button)).perform(click())
    }

    @Then("^she sees a list of phone modes$")
    fun she_sees_a_list_of_phone_modes() {
        waitForElement(SELECT_BUTTON)
    }

    @When("^she selects SMS$")
    fun she_selects_sms() {
        selectAuthenticator("SMS")
    }

    @And("^she submits the phone mode form$")
    fun she_submits_the_phone_mode_form() {
        onView(withId(R.id.submit_button)).perform(click())
    }

    @Then("^she is presented with an option to select SMS to verify$")
    fun she_is_presented_with_an_option_to_select_SMS_to_verify() {
        waitForElement(SELECT_BUTTON)
        authenticatorViewInteraction("SMS").check(matches(isDisplayed()))
    }

    @When("^she selects SMS from the list$")
    fun she_selects_SMS_from_the_list() {
        selectAuthenticator("SMS")
    }

    @And("^she inputs an invalid phone number$")
    fun she_inputs_an_invalid_phone_number() {
        waitForElement(PHONE_EDIT_TEXT)

        onView(withId(R.id.phone_edit_text)).perform(replaceText("123"))
    }

    @Then("^she should see a message \"Unable to initiate factor enrollment: Invalid Phone Number\"$")
    fun she_should_see_a_message_invalid_phone_number() {
        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("Invalid Phone Number.")))
    }

    @When("^she selects \"Skip\" on SMS$")
    fun she_selects_skip_on_sms() {
        waitForElement(SKIP_BUTTON)
        onView(withId(R.id.skip_button)).perform(click())
    }
}
