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

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.okta.idx.android.cucumber.hooks.EnrollSecurityQuestion
import com.okta.idx.android.cucumber.hooks.SharedState
import com.okta.idx.android.infrastructure.PROGRESS_BAR_VIEW
import com.okta.idx.android.infrastructure.a18n.A18NWrapper
import com.okta.idx.android.infrastructure.espresso.clickButtonWithText
import com.okta.idx.android.infrastructure.espresso.first
import com.okta.idx.android.infrastructure.espresso.waitForElementToBeGone
import com.okta.idx.android.infrastructure.espresso.waitForElementWithText
import com.okta.idx.android.infrastructure.execShellCommand
import io.cucumber.java.en.And
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import timber.log.Timber

internal class VerifyDefinitions {
    @Then("^the screen changes to receive an input for a code$")
    fun the_screen_changes_to_receive_an_input_for_a_code() {
        waitForElementWithText("Resend Code")
    }

    @Then("^she sees a page to input a code$")
    fun she_sees_a_page_to_input_a_code() {
        waitForElementWithText("Resend Code")
    }

    @When("^she fills in the correct code$")
    fun she_fills_in_the_correct_code() {
        val code = A18NWrapper.getCodeFromEmail(SharedState.a18NProfile!!, ::resendCode)
        Timber.i("Code: %s", code)
        onView(withHint("Enter code")).perform(replaceText(code))
    }

    @And("^she submits the verify form$")
    fun she_submits_the_verify_form() {
        onView(first(withText("Continue"))).perform(click())
    }

    @And("^she inputs the incorrect code from the email$")
    fun she_inputs_the_incorrect_code() {
        onView(withHint("Enter code")).perform(replaceText("123"))
    }

    @Then("^the sample shows an error message \"Invalid code. Try again.\" on the Sample App$")
    fun sample_shows_invalid_code() {
        waitForElementToBeGone(PROGRESS_BAR_VIEW)
        onView(withText("Invalid code. Try again.")).check(matches(isDisplayed()))
    }

    @When("^she inputs the correct code from the SMS$")
    fun she_inputs_the_correct_code_from_the_SMS() {
        val code = A18NWrapper.getCodeFromPhone(SharedState.a18NProfile!!, ::resendCode)
        Timber.i("Code: %s", code)
        onView(withHint("Enter code")).perform(replaceText(code))
    }

    @When("^she inputs the incorrect code from the phone$")
    fun she_inputs_the_incorrect_code_from_the_phone() {
        onView(withHint("Enter code")).perform(replaceText("123"))
    }

    @And("^she sees a field to re-enter another code$")
    fun she_sees_a_field_to_re_enter_another_code() {
        onView(withHint("Enter code")).check(matches(isDisplayed()))
    }

    @Then("^she sees a page to input her code$")
    fun she_sees_a_page_to_input_her_code() {
        waitForElementWithText("Resend Code")
    }

    @When("^she inputs the correct code from her email$")
    fun she_inputs_the_correct_code_from_her_email() {
        val code = A18NWrapper.getCodeFromEmail(SharedState.a18NProfile!!, ::resendCode)
        Timber.i("Code: %s", code)
        onView(withHint("Enter code")).perform(replaceText(code))
    }

    @Then("^she should see an input box for answering the security question$")
    fun she_should_see_an_input_box_for_answering_the_security_question() {
        waitForElementWithText("Security Question For MFA")
    }

    @When("^she enters the answer for the Security Question$")
    fun she_enters_the_answer_for_the_security_question() {
        onView(withHint("Answer")).perform(replaceText(EnrollSecurityQuestion.ANSWER))
    }

    @When("^she clicks the magic link from the email in her inbox$")
    fun she_clicks_the_magic_link_from_the_email_in_her_inbox() {
        val link = A18NWrapper.getMagicLinkFromEmail(SharedState.a18NProfile!!, ::resendCode)
        val application = ApplicationProvider.getApplicationContext<Application>()
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        application.startActivity(browserIntent)
    }

    @Then("she should see a page that says Did you just try to sign in?")
    fun she_should_see_a_page_saying_did_you_just_try_to_sign_in() {
        waitForElementWithText("Did you just try to sign in?")
    }

    @When("^she clicks \"Yes, it's me\"$")
    fun she_clicks_yes_its_me() {
        clickButtonWithText("Yes, it's me")
    }

    @And("^switches back to the app$")
    fun switches_back_to_the_app() {
        Thread.sleep(2000)
        execShellCommand("am force-stop com.android.chrome")
        Thread.sleep(2000)
    }

    private fun resendCode() {
        Timber.i("Resending the code.")
        onView(withText("Resend Code")).perform(click())
        waitForElementToBeGone(PROGRESS_BAR_VIEW)
    }
}
