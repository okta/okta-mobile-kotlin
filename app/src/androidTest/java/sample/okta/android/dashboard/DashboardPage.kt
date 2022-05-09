/*
 * Copyright 2022-Present Okta, Inc.
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
package sample.okta.android.dashboard

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withChild
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withTagKey
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import sample.okta.android.R
import sample.okta.android.test.waitForResourceId
import sample.okta.android.test.waitForText
import sample.okta.android.test.waitForView

internal class DashboardPage {
    init {
        waitForResourceId(".*credential_name")
    }

    fun assertIsDefaultCredential(): DashboardPage {
        onView(withId(R.id.credential_name)).check(matches(withText("Default")))
        return this
    }

    fun assertIsTokenExchangeCredential(): DashboardPage {
        waitForText("TokenExchange")
        onView(withId(R.id.credential_name)).check(matches(withText("TokenExchange")))
        return this
    }

    fun assertHasScope(scope: String): DashboardPage {
        onView(withId(R.id.scope)).perform(scrollTo())
        for (s in scope.split(" ")) {
            onView(withId(R.id.scope)).check(matches(withText(containsString(s))))
        }
        return this
    }

    fun assertMissingScope(scope: String): DashboardPage {
        onView(withId(R.id.scope)).perform(scrollTo())
        for (s in scope.split(" ")) {
            onView(allOf(withId(R.id.scope), withText(containsString(s)))).check(doesNotExist())
        }
        return this
    }

    fun assertHasClaim(claim: String, value: String): DashboardPage {
        waitForView(withId(R.id.claims_title), 10_000)
        onView(
            allOf(
                withParent(withChild(withText(claim))),
                withId(R.id.text_view_value),
                withTagKey(R.id.claim, equalTo(claim)),
                withText(value)
            )
        ).perform(scrollTo()).check(matches(withText(value)))
        return this
    }

    fun logOut(): DashboardPage {
        onView(withId(R.id.logout_web_button)).perform(scrollTo(), click())
        return this
    }

    fun goToTokenExchangeFlow(): DashboardPage {
        onView(withId(R.id.token_exchange_button)).perform(scrollTo(), click())
        return this
    }

    fun verifyLastRequestInfo(statusText: String): DashboardPage {
        onView(withId(R.id.last_request_info)).perform(scrollTo())
        waitForText(statusText)
        onView(withId(R.id.last_request_info)).check(matches(withText(statusText)))
        return this
    }
}
