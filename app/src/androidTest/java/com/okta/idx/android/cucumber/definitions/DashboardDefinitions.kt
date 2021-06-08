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
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.okta.idx.android.R
import com.okta.idx.android.infrastructure.ID_TOKEN_TYPE_TEXT_VIEW
import com.okta.idx.android.infrastructure.espresso.waitForElement
import io.cucumber.java.en.And
import io.cucumber.java.en.Then
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.isEmptyOrNullString
import org.hamcrest.Matchers.not

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
        onView(withId(R.id.id_token)).check(
            matches(
                allOf(
                    isDisplayed(),
                    withText(not(isEmptyOrNullString()))
                )
            )
        )
    }

    @And("^the refresh_token is stored in session$") fun refresh_token_stored() {
        onView(withId(R.id.refresh_token)).check(
            matches(
                allOf(
                    isDisplayed(),
                    withText(not(isEmptyOrNullString()))
                )
            )
        )
    }
}
