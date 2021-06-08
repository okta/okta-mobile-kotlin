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
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.okta.idx.android.R
import com.okta.idx.android.infrastructure.CLAIM_KEY_TEXT_VIEW
import com.okta.idx.android.infrastructure.EndToEndCredentials
import com.okta.idx.android.infrastructure.ID_TOKEN_TYPE_TEXT_VIEW
import com.okta.idx.android.infrastructure.LAUNCH_TITLE_TEXT_VIEW
import com.okta.idx.android.infrastructure.USERNAME_EDIT_TEXT
import com.okta.idx.android.infrastructure.espresso.waitForElement
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

class LaunchDefinitions {
    @Given("^Mary navigates to the Basic Login View$") fun navigate_to_basic_login_view() {
        waitForElement(LAUNCH_TITLE_TEXT_VIEW)
        onView(withId(R.id.login_button)).perform(click())
    }

    @Given("^Mary navigates to the Self Service Registration View$")
    fun navigate_to_self_service_registration_view() {
        waitForElement(LAUNCH_TITLE_TEXT_VIEW)
        onView(withId(R.id.self_service_registration_button)).perform(click())
    }

    @Given("^Mary has an unauthenticated session$")
    fun mary_has_an_unauthenticated_session() {
        waitForElement(LAUNCH_TITLE_TEXT_VIEW)
    }

    @When("^Mary navigates to the Root View$")
    fun mary_navigate_to_the_root_view() {
        // Nothing to do, that's hwo the app is launched.
    }

    @Then("^the Root Page shows links to the Entry Points as defined in https://oktawiki.atlassian.net/l/c/Pw7DVm1t$")
    fun the_root_page_show_entry_points() {
        waitForElement(LAUNCH_TITLE_TEXT_VIEW)
        onView(withId(R.id.login_button)).check(matches(isDisplayed()))
        onView(withId(R.id.self_service_registration_button)).check(matches(isDisplayed()))
    }

    @Given("^Mary has an authenticated session$")
    fun mary_has_an_authenticated_session() {
        waitForElement(LAUNCH_TITLE_TEXT_VIEW)
        onView(withId(R.id.login_button)).perform(click())
        waitForElement(USERNAME_EDIT_TEXT)

        onView(withId(R.id.username_edit_text)).perform(replaceText(EndToEndCredentials["/cucumber/username"]))
        onView(withId(R.id.password_edit_text)).perform(replaceText(EndToEndCredentials["/cucumber/password"]))
        onView(withId(R.id.submit_button)).perform(click())

        waitForElement(ID_TOKEN_TYPE_TEXT_VIEW)
        onView(withId(R.id.sign_out_button)).perform(scrollTo())
        waitForElement(CLAIM_KEY_TEXT_VIEW)
    }

    @Then("^she is redirected back to the Root View$")
    fun she_is_redirect_back_to_the_root_view() {
        waitForElement(LAUNCH_TITLE_TEXT_VIEW)
    }

    @And("^Mary sees login, registration buttons$")
    fun mary_sees_login_and_registration_buttons() {
        onView(withId(R.id.login_button)).check(matches(isDisplayed()))
        onView(withId(R.id.self_service_registration_button)).check(matches(isDisplayed()))
    }
}
