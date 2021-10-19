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
import com.okta.idx.android.infrastructure.EndToEndCredentials
import com.okta.idx.android.infrastructure.PROGRESS_BAR_VIEW
import com.okta.idx.android.infrastructure.espresso.selectAuthenticator
import com.okta.idx.android.infrastructure.espresso.waitForElementToBeGone
import com.okta.idx.android.infrastructure.espresso.waitForElementWithText
import io.cucumber.java.en.And
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.hamcrest.CoreMatchers.containsString

class RegistrationDefinitions {
    @When("^she fills out her First Name$")
    fun she_fills_out_her_first_name() {
        waitForElementWithText("Restart")
        val firstName = EndToEndCredentials["/cucumber/firstName"]
        onView(withHint("First name")).perform(replaceText(firstName))
    }

    @And("^she fills out her Last Name$")
    fun she_fills_out_her_last_name() {
        var profileSuffix = "self-service-registration"
        SharedState.a18NProfile?.let { profileSuffix = it.profileId }
        onView(withHint("Last name")).perform(replaceText("e2e-$profileSuffix"))
    }

    @And("^she fills out her Email$")
    fun she_fills_out_her_email() {
        val email = SharedState.a18NProfile!!.emailAddress
        onView(withHint("Email")).perform(replaceText(email))
    }

    @And("^she fills out her Random Property$")
    fun she_fills_out_her_random_property() {
        onView(withHint("Random")).perform(replaceText("Not blank!"))
    }

    @And("^she submits the registration form$")
    fun she_submits_the_registration_form() {
        onView(withText("Sign Up")).perform(click())
    }

    @Then("^she sees a list of required factors to setup$")
    fun she_sees_a_list_of_required_factors_to_setup() {
        waitForElementWithText("Choose")
    }

    @When("^she selects Password$")
    fun she_selects_password() {
        selectAuthenticator("Password")
        onView(withText("Choose")).perform(click())
    }

    @Then("^she sees a page to setup password$")
    fun she_sees_a_page_to_setup_password() {
        waitForElementWithText("Enter password")
    }

    @When("^she fills out her Password$")
    fun she_fills_out_her_password() {
        val password = EndToEndCredentials["/cucumber/newPassword"]
        onView(withHint("Enter password")).perform(replaceText(password))
    }

    @Then("^she sees the list of optional factors$")
    fun she_sees_the_list_of_optional_factors() {
        waitForElementWithText("Email")
    }

    @And("^she fills out her Email with an invalid email format$")
    fun she_fills_out_her_email_with_an_invalid_email_format() {
        onView(withHint("Email")).perform(replaceText("e2e-ssr@acme"))
    }

    @Then("^she sees an error message \"'Email' must be in the form of an email address, Provided value for property 'Email' does not match required pattern\"$")
    fun she_sees_invalid_email_format_error_message() {
        waitForElementToBeGone(PROGRESS_BAR_VIEW)
        onView(
            withText(containsString("'Email' must be in the form of an email address"))
        ).check(matches(isDisplayed()))
        onView(
            withText(containsString("Provided value for property 'Email' does not match required pattern"))
        ).check(matches(isDisplayed()))
    }
}
