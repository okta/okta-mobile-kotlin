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
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.okta.idx.android.cucumber.hooks.SharedState
import com.okta.idx.android.infrastructure.ACTION_BUTTON_VIEW
import com.okta.idx.android.infrastructure.espresso.selectAuthenticator
import com.okta.idx.android.infrastructure.espresso.waitForElement
import com.okta.idx.android.infrastructure.espresso.waitForElementWithText
import io.cucumber.java.en.And
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

class UnlockDefinitions {
    @When("^she sees a link to unlock her account$")
    fun she_sees_a_link_to_unlock_her_account() {
        waitForElement(ACTION_BUTTON_VIEW)
        waitForElementWithText("Unlock Account")
    }

    @And("^she clicks the link to unlock her account$")
    fun she_clicks_the_link_to_unlock_her_account() {
        onView(withText("Unlock Account")).perform(click())
    }

    @Then("^she sees a page to input her user name$")
    fun she_sees_a_page_to_input_her_user_name_and_select_email_Phone_or_okta_verify_to_unlock_her_account() {
        waitForElementWithText("Restart")
    }

    @When("^she inputs her email$")
    fun she_inputs_her_email() {
        onView(withHint("Username")).perform(replaceText(SharedState.a18NProfile!!.emailAddress))
    }

    @Then("^she should see a terminal page in the new tab in the same browser that says \"Flow continued in a new tab.\"$")
    fun she_should_see_a_terminal_page() {
        waitForElementWithText("Flow continued in a new tab.")
    }

    @When("^she selects Email for unlocking")
    fun she_selects_email_for_unlocking() {
        selectAuthenticator("Email")
    }

    @Then("^she should see a terminal page that says \"Your account is now unlocked!\"$")
    fun she_should_see_a_terminal_page_that_says_account_successfully_unlocked() {
        waitForElementWithText("Account successfully unlocked!<br>Verify your account with a security method to continue.")
    }
}
