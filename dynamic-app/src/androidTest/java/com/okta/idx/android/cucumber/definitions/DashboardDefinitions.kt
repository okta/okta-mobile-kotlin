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
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withChild
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withTagKey
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.okta.idx.android.dynamic.R
import com.okta.idx.android.infrastructure.EndToEndCredentials
import com.okta.idx.android.infrastructure.ID_TOKEN_TYPE_TEXT_VIEW
import com.okta.idx.android.infrastructure.espresso.waitForElement
import io.cucumber.java.en.And
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.isEmptyOrNullString
import org.hamcrest.Matchers.not
import javax.annotation.CheckReturnValue

class DashboardDefinitions {
    @Then("^she is redirected to the Root View$") fun redirected_to_root_view() {
        waitForElement(ID_TOKEN_TYPE_TEXT_VIEW)
        onView(withText("Token Type:")).check(matches(isDisplayed()))
        onView(withText("Bearer")).check(matches(isDisplayed()))
    }

    @And("^an application session is created$")
    fun an_application_session_is_created() {
        waitForElement(ID_TOKEN_TYPE_TEXT_VIEW)
        onView(withText("Token Type:")).check(matches(isDisplayed()))
        onView(withText("Bearer")).check(matches(isDisplayed()))
    }

    @And("^the access_token is stored in session$") fun access_token_stored() {
        onView(withId(R.id.access_token)).check(
            matches(
                allOf(
                    isDisplayed(),
                    withText(not(isEmptyOrNullString()))
                )
            )
        )
    }

    @And("^the id_token is stored in session$") fun id_token_stored() {
        onView(withId(R.id.id_token))
            .perform(scrollTo())
            .check(
                matches(
                    allOf(
                        isDisplayed(),
                        withText(not(isEmptyOrNullString()))
                    )
                )
            )
    }

    @And("^the refresh_token is stored in session$") fun refresh_token_stored() {
        onView(withId(R.id.refresh_token))
            .perform(scrollTo())
            .check(
                matches(
                    allOf(
                        isDisplayed(),
                        withText(not(isEmptyOrNullString()))
                    )
                )
            )
    }

    @Then("^Mary sees a table with the claims from the /userinfo response$")
    fun mary_sees_a_table_with_the_claims_from_the_userinfo_response() {
        claimViewInteraction("email", EndToEndCredentials["/cucumber/username"])
            .perform(scrollTo()).check(matches(isDisplayed()))
        claimViewInteraction("preferred_username", EndToEndCredentials["/cucumber/username"])
            .perform(scrollTo()).check(matches(isDisplayed()))
    }

    @And("^she does not see claims from /userinfo$")
    fun she_does_not_see_claims_from_userinfo() {
        claimViewInteraction("email", EndToEndCredentials["/cucumber/username"])
            .check(doesNotExist())
        claimViewInteraction("preferred_username", EndToEndCredentials["/cucumber/username"])
            .check(doesNotExist())
    }

    @And("^Mary sees a logout button$")
    fun mary_sees_a_logout_button() {
        onView(withId(R.id.sign_out_button)).perform(scrollTo())
    }

    @When("^Mary clicks the logout button$")
    fun when_mary_clicks_the_logout_button() {
        onView(withId(R.id.sign_out_button)).perform(scrollTo()).perform(click())
    }

    @And("^she sees a table with her profile info$")
    fun she_sees_a_table_with_her_profile_info() {
        onView(withId(R.id.claims_linear_layout))
            .check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))
    }

    @And("^the cell for the value of \"email\" is shown and contains her email$")
    fun the_cell_for_the_value_of_email_is_shown_and_contains_her_email() {
        Thread.sleep(1000)
        claimViewInteraction("email", EndToEndCredentials["/cucumber/socialEmail"])
            .perform(scrollTo())
            .check(matches(isDisplayed()))
    }

    @And("^the cell for the value of \"name\" is shown and contains her first name and last name$")
    fun the_name_cell_contains_first_and_last_name() {
        claimViewInteraction("name", EndToEndCredentials["/cucumber/socialName"])
            .perform(scrollTo())
            .check(matches(isDisplayed()))
    }

    @CheckReturnValue
    private fun claimViewInteraction(key: String, value: String): ViewInteraction {
        return onView(
            allOf(
                withParent(withChild(withText(key))),
                withId(R.id.text_view_value),
                withTagKey(R.id.claim, equalTo(key)),
                withText(value)
            )
        )
    }
}
