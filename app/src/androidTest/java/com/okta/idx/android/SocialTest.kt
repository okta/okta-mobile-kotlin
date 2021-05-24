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
package com.okta.idx.android

import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.okta.idx.android.directauth.TestingGlobals
import com.okta.idx.android.infrastructure.espresso.selectAuthenticator
import com.okta.idx.android.infrastructure.espresso.waitForElement
import com.okta.idx.android.infrastructure.network.NetworkRule
import com.okta.idx.android.infrastructure.network.testBodyFromFile
import com.okta.idx.android.network.mock.OktaMockWebServer
import com.okta.idx.android.network.mock.RequestMatchers.path
import org.hamcrest.Matchers.allOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SocialTest {
    companion object {
        private const val USERNAME_EDIT_TEXT = "com.okta.idx.android:id/username_edit_text"
        private const val ID_TOKEN_TYPE_TEXT_VIEW = "com.okta.idx.android:id/token_type"
    }

    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    @get:Rule val networkRule = NetworkRule()

    @Before fun setup() {
        OktaMockWebServer.dispatcher.consumeResponses = true
        Intents.init()

        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_VIEW)
        intentFilter.addDataScheme("https")
        val am = Instrumentation.ActivityMonitor(intentFilter, null, true)
        InstrumentationRegistry.getInstrumentation().addMonitor(am)
    }

    // Mary Logs in with Social IDP
    @Test fun scenario_5_1_1() {
        val mockPrefix = "scenario_5_1_1"
        networkRule.enqueue(path("oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("$mockPrefix/interact.json")
        }
        networkRule.enqueue(path("idp/idx/introspect")) { response ->
            response.testBodyFromFile("$mockPrefix/introspect.json")
        }
        networkRule.enqueue(path("oauth2/default/v1/token")) { response ->
            response.testBodyFromFile("$mockPrefix/tokens.json")
        }
        networkRule.enqueue(path("oauth2/default/v1/userinfo")) { response ->
            response.testBodyFromFile("$mockPrefix/userinfo.json")
        }

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.login_button)).perform(click())
        waitForElement(USERNAME_EDIT_TEXT)
        selectAuthenticator("Login With Google")

        intended(
            allOf(
                hasAction(Intent.ACTION_VIEW),
                hasData("https://foo.oktapreview.com/oauth2/ausko2zk1B3kDU2d65d6/v1/authorize?client_id=0oal2s4yhspmifyt65d6&request_uri=urn:okta:bGNlQkY4NzltNXRWeHNheUlOVVJwOWN2Rk1DSElfS0JQVUlSaE5LWlQtTTowb2Fyc2Q5dWZmUjh0alNBTDVkNg")
            )
        )

        val state = TestingGlobals.CURRENT_PROCEED_CONTEXT.get().clientContext?.state
        // Send the callback.
        val redirectIntent = Intent(
            MainActivity.SOCIAL_REDIRECT_ACTION,
            Uri.parse("com.okta.sample.android:/login?interaction_code=o8NoWT5_t7NsDy_L625JWuIT_AOOuhtDZIiiqfB6qIQ&state=$state")
        )
        activityRule.scenario.onActivity { it.onNewIntent(redirectIntent) }

        waitForElement(ID_TOKEN_TYPE_TEXT_VIEW)
        onView(withText("Token Type:")).check(matches(isDisplayed()))
        onView(withText("Bearer")).check(matches(isDisplayed()))
    }
}
