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
import com.okta.idx.android.infrastructure.CONFIRMED_PASSWORD_EDIT_TEXT
import com.okta.idx.android.infrastructure.ERROR_TEXT_VIEW
import com.okta.idx.android.infrastructure.EndToEndCredentials
import com.okta.idx.android.infrastructure.FIRST_NAME_EDIT_TEXT
import com.okta.idx.android.infrastructure.SELECT_BUTTON
import com.okta.idx.android.infrastructure.espresso.selectAuthenticator
import com.okta.idx.android.infrastructure.espresso.waitForElement
import io.cucumber.java.en.And
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.hamcrest.CoreMatchers.allOf

class RegistrationDefinitions {
    @When("^she fills out her First Name$")
    fun she_fills_out_her_first_name() {
        waitForElement(FIRST_NAME_EDIT_TEXT)
        val firstName = EndToEndCredentials["/cucumber/firstName"]
        onView(withId(R.id.first_name_edit_text)).perform(replaceText(firstName))
    }

    @And("^she fills out her Last Name$")
    fun she_fills_out_her_last_name() {
        var profileSuffix = "self-service-registration"
        SharedState.a18NProfile?.let { profileSuffix = it.profileId }
        onView(withId(R.id.last_name_edit_text)).perform(replaceText("e2e-$profileSuffix"))
    }

    @And("^she fills out her Email$")
    fun she_fills_out_her_email() {
        val email = SharedState.a18NProfile!!.emailAddress
        onView(withId(R.id.primary_email_edit_text)).perform(replaceText(email))
    }

    @And("^she submits the registration form$")
    fun she_submits_the_registration_form() {
        onView(withId(R.id.register_button)).perform(click())
    }

    @Then("^she sees a list of required factors to setup$")
    fun she_sees_a_list_of_required_factors_to_setup() {
        waitForElement(SELECT_BUTTON)
    }

    @When("^she selects Password$")
    fun she_selects_password() {
        selectAuthenticator("Password")
    }

    @Then("^she sees a page to setup password$")
    fun she_sees_a_page_to_setup_password() {
        waitForElement(CONFIRMED_PASSWORD_EDIT_TEXT)
    }

    @When("^she fills out her Password$")
    fun she_fills_out_her_password() {
        val password = EndToEndCredentials["/cucumber/newPassword"]
        onView(withId(R.id.password_edit_text)).perform(replaceText(password))
    }

    @And("^she confirms her Password$")
    fun she_confirms_her_password() {
        val password = EndToEndCredentials["/cucumber/newPassword"]
        onView(withId(R.id.confirmed_password_edit_text)).perform(replaceText(password))
    }

    @Then("^she sees the list of optional factors$")
    fun she_sees_the_list_of_optional_factors() {
        waitForElement(SELECT_BUTTON)
    }

    @And("^she fills out her Email with an invalid email format$")
    fun she_fills_out_her_email_with_an_invalid_email_format() {
        onView(withId(R.id.primary_email_edit_text)).perform(replaceText("e2e-ssr@acme"))
    }

    @Then("^she sees an error message \"'Email' must be in the form of an email address, Provided value for property 'Email' does not match required pattern\"$")
    fun she_sees_invalid_email_format_error_message() {
        waitForElement(ERROR_TEXT_VIEW)
        onView(
            allOf(
                withId(R.id.error_text_view),
                withText("'Email' must be in the form of an email address")
            )
        ).check(matches(isDisplayed()))
        onView(
            allOf(
                withId(R.id.error_text_view),
                withText("Provided value for property 'Email' does not match required pattern")
            )
        ).check(matches(isDisplayed()))
    }
}
