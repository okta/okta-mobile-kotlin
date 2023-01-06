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
import com.okta.idx.android.infrastructure.EndToEndCredentials
import com.okta.idx.android.infrastructure.espresso.clickButtonWithText
import com.okta.idx.android.infrastructure.espresso.clickButtonWithTextMatching
import com.okta.idx.android.infrastructure.espresso.fillInEditText
import com.okta.idx.android.infrastructure.espresso.selectAuthenticator
import com.okta.idx.android.infrastructure.espresso.waitForElementWithText
import com.okta.idx.android.infrastructure.execShellCommand
import io.cucumber.java.Before
import io.cucumber.java.en.And
import io.cucumber.java.en.Then
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

internal class SocialDefinitions {
    @Before("@logOutOfSocialIdP", order = 0)
    fun logOutOfSocialIdP() {
        execShellCommand("pm clear com.android.chrome")
        Thread.sleep(2.seconds.inWholeMilliseconds)

        val application = ApplicationProvider.getApplicationContext<Application>()
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://okta.com"))
        val buttonTimeout = 1.seconds.inWholeMilliseconds
        browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        application.startActivity(browserIntent)

        try {
            clickButtonWithText("Use without an account", buttonTimeout)
        } catch (e: Throwable) {
            Timber.e(e, "Error calling Use without an account")
        }

        try {
            clickButtonWithText("Accept & continue", buttonTimeout)
        } catch (e: Throwable) {
            Timber.e(e, "Error Calling accept and continue")
        }

        try {
            clickButtonWithTextMatching("No [t|T]hanks", buttonTimeout)
        } catch (e: Throwable) {
            Timber.e(e, "Error Calling No thanks")
        }

        Thread.sleep(1.seconds.inWholeMilliseconds)
        execShellCommand("am force-stop com.android.chrome")
        Thread.sleep(1.seconds.inWholeMilliseconds)
    }

    @And("^logs in to Okta OIDC$") fun logs_in_to_okta_oidc() {
        fillInEditText("input28", EndToEndCredentials["/cucumber/socialEmail"])
        fillInEditText("input36", EndToEndCredentials["/cucumber/socialPassword"])
        clickButtonWithText("Sign in")
    }

    @And("^logs in to Okta OIDC with MFA User$") fun logs_in_to_okta_oidc_with_mfa_user() {
        fillInEditText("input28", EndToEndCredentials["/cucumber/socialEmailMfa"])
        fillInEditText("input36", EndToEndCredentials["/cucumber/socialPasswordMfa"])
        clickButtonWithText("Sign in")
    }

    @Then("^Mary should see a page to select an authenticator for MFA$")
    fun mary_should_see_a_page_to_select_an_authenticator_for_mfa() {
        selectAuthenticator("Email")
        waitForElementWithText("Choose")
    }
}
