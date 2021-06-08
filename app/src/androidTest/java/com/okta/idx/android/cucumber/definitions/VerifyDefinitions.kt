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
import com.okta.idx.android.infrastructure.CODE_EDIT_TEXT
import com.okta.idx.android.infrastructure.ERROR_TEXT_VIEW
import com.okta.idx.android.infrastructure.a18n.A18NWrapper
import com.okta.idx.android.infrastructure.espresso.waitForElement
import io.cucumber.java.en.And
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import timber.log.Timber

internal class VerifyDefinitions {
    @Then("^the screen changes to receive an input for a code$")
    fun the_screen_changes_to_receive_an_input_for_a_code() {
        waitForElement(CODE_EDIT_TEXT)
    }

    @Then("^she sees a page to input a code$")
    fun she_sees_a_page_to_input_a_code() {
        waitForElement(CODE_EDIT_TEXT)
    }

    @When("^she fills in the correct code$")
    fun she_fills_in_the_correct_code() {
        val code = A18NWrapper.getCodeFromEmail(SharedState.a18NProfile!!)
        Timber.i("Code: %s", code)
        onView(withId(R.id.code_edit_text)).perform(replaceText(code))
    }

    @And("^she submits the verify form$")
    fun she_submits_the_verify_form() {
        onView(withId(R.id.submit_button)).perform(click())
    }

    @And("^she inputs the incorrect code from the email$")
    fun she_inputs_the_incorrect_code() {
        onView(withId(R.id.code_edit_text)).perform(replaceText("123"))
    }

    @Then("^the sample shows an error message \"Invalid code. Try again.\" on the Sample App$")
    fun sample_shows_invalid_code() {
        waitForElement(ERROR_TEXT_VIEW)
        onView(withId(R.id.error_text_view)).check(matches(withText("Invalid code. Try again.")))
    }

    @When("^she inputs the correct code from the SMS$")
    fun she_inputs_the_correct_code_from_the_SMS() {
        val code = A18NWrapper.getCodeFromPhone(SharedState.a18NProfile!!)
        Timber.i("Code: %s", code)
        onView(withId(R.id.code_edit_text)).perform(replaceText(code))
    }

    @When("^she inputs the incorrect code from the phone$")
    fun she_inputs_the_incorrect_code_from_the_phone() {
        onView(withId(R.id.code_edit_text)).perform(replaceText("123"))
    }

    @And("^she sees a field to re-enter another code$")
    fun she_sees_a_field_to_re_enter_another_code() {
        waitForElement(CODE_EDIT_TEXT)
    }

    @Then("^she sees a page to input her code$")
    fun she_sees_a_page_to_input_her_code() {
        waitForElement(CODE_EDIT_TEXT)
    }

    @When("^she inputs the correct code from her email$")
    fun she_inputs_the_correct_code_from_her_email() {
        val code = A18NWrapper.getCodeFromEmail(SharedState.a18NProfile!!)
        Timber.i("Code: %s", code)
        onView(withId(R.id.code_edit_text)).perform(replaceText(code))
    }
}
