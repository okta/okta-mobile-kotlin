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
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.okta.idx.android.dynamic.R
import com.okta.idx.android.infrastructure.ACTION_BUTTON_VIEW
import com.okta.idx.android.infrastructure.ERROR_TEXT_VIEW
import com.okta.idx.android.infrastructure.EndToEndCredentials
import com.okta.idx.android.infrastructure.LAUNCH_TITLE_TEXT_VIEW
import com.okta.idx.android.infrastructure.PROGRESS_BAR_VIEW
import com.okta.idx.android.infrastructure.espresso.waitForElement
import com.okta.idx.android.infrastructure.espresso.waitForElementToBeGone
import com.okta.idx.android.infrastructure.espresso.waitForElementWithText
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

class LoginDefinitions {
    @When("^she fills in her correct username$") fun enter_correct_username() {
        waitForElementToBeGone(PROGRESS_BAR_VIEW)
        waitForElement(ACTION_BUTTON_VIEW)

        onView(withHint("Username")).perform(replaceText(EndToEndCredentials["/cucumber/username"]))
    }

    @And("^she fills in her correct password$") fun enter_correct_password() {
        onView(withHint("Password")).perform(replaceText(EndToEndCredentials["/cucumber/password"]))
    }

    @And("^she submits the Login form$") fun clicks_login_button() {
        onView(withText("Sign In")).perform(click())
    }

    @When("^she fills in her incorrect username$") fun enter_incorrect_username() {
        waitForElementToBeGone(PROGRESS_BAR_VIEW)
        waitForElement(ACTION_BUTTON_VIEW)

        onView(withHint("Username")).perform(replaceText(EndToEndCredentials["/cucumber/invalidUsername"]))
    }

    @Then("^she should see a \"There is no account with username\" message on the Login form$")
    fun no_account_user_error() {
        waitForElement(ERROR_TEXT_VIEW)
        val invalidUsername = EndToEndCredentials["/cucumber/invalidUsername"]
        onView(withId(R.id.error_text_view)).check(matches(withText("There is no account with the Username $invalidUsername.")))
    }

    @When("^she fills in her incorrect password$") fun enter_incorrect_password() {
        onView(withHint("Password")).perform(replaceText(EndToEndCredentials["/cucumber/invalidPassword"]))
    }

    @Then("^she should see the message \"Authentication failed\"$")
    fun authentication_failed_message() {
        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("Authentication failed")))
    }

    @When("^she clicks on the \"Forgot Password Link\"$")
    fun clicks_forgot_password_link() {
        waitForElementToBeGone(PROGRESS_BAR_VIEW)
        waitForElementWithText("Recover")

        onView(withText("Recover")).perform(click())
    }

    @Then("^she is redirected to the Self Service Password Reset View$")
    fun redirect_to_sspr_view() {
        waitForElement(ACTION_BUTTON_VIEW)
        waitForElementWithText("Continue")
        onView(withHint("Username")).check(matches(isDisplayed()))
    }

    @Then("^she clicks the \"Login with Okta OIDC IdP\" button$")
    fun she_clicks_the_login_with_okta_oidc_button() {
        waitForElementWithText("Login with Okta OIDC IdP")

        onView(withText("Login with Okta OIDC IdP")).perform(click())
    }

    @Given("^Mary navigates to the Self Service Registration View$")
    fun navigate_to_self_service_registration_view() {
        waitForElement(LAUNCH_TITLE_TEXT_VIEW)
        onView(withId(R.id.login_button)).perform(click())
        waitForElement(ACTION_BUTTON_VIEW)
        onView(withText("Sign Up")).perform(click())
    }
}
