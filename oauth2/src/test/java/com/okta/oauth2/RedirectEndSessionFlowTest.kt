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
package com.okta.oauth2

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.okta.oauth2.RedirectEndSessionFlow.Companion.createRedirectEndSessionFlow
import com.okta.oauth2.events.CustomizeLogoutUrlEvent
import com.okta.testhelpers.OktaRule
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RedirectEndSessionFlowTest {
    @get:Rule val oktaRule = OktaRule()

    @Test fun testStart(): Unit = runBlocking {
        val redirectEndSessionFlow = oktaRule.createOidcClient().createRedirectEndSessionFlow()
        assertThat(oktaRule.eventHandler).isEmpty()
        val result = redirectEndSessionFlow.start(
            idToken = "exampleIdToken",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
        )

        val context = result as RedirectEndSessionFlow.ResumeResult.Context

        assertThat(context.state).isEqualTo("25c1d684-8d30-42e3-acc0-b74b35fd47b4")
        val expectedUrlEnding = "/oauth2/default/v1/logout?id_token_hint=exampleIdToken&post_logout_redirect_uri=unitTest%3A%2Flogout&state=25c1d684-8d30-42e3-acc0-b74b35fd47b4"
        assertThat(context.url.toString()).endsWith(expectedUrlEnding)

        assertThat(oktaRule.eventHandler).hasSize(1)
        assertThat(oktaRule.eventHandler[0]).isInstanceOf(CustomizeLogoutUrlEvent::class.java)
        assertThat((oktaRule.eventHandler[0] as CustomizeLogoutUrlEvent).httpUrlBuilder.build().toString()).endsWith(expectedUrlEnding)
    }

    @Test fun testResume() {
        val redirectEndSessionFlow = oktaRule.createOidcClient().createRedirectEndSessionFlow()
        val flowContext = RedirectEndSessionFlow.ResumeResult.Context(
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            url = "https://example.okta.com/not_used".toHttpUrl(),
        )
        val result = redirectEndSessionFlow.resume(
            uri = Uri.parse("unitTest:/logout?state=${flowContext.state}"),
            flowContext = flowContext,
        )
        assertThat(result).isInstanceOf(RedirectEndSessionFlow.Result.Success::class.java)
    }

    @Test fun testResumeSchemeMismatch() {
        val redirectEndSessionFlow = oktaRule.createOidcClient().createRedirectEndSessionFlow()
        val flowContext = RedirectEndSessionFlow.ResumeResult.Context(
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            url = "https://example.okta.com/not_used".toHttpUrl(),
        )
        val result = redirectEndSessionFlow.resume(
            uri = Uri.parse("wrong:/logout?state=${flowContext.state}"),
            flowContext = flowContext,
        )
        assertThat(result).isInstanceOf(RedirectEndSessionFlow.Result.RedirectSchemeMismatch::class.java)
    }

    @Test fun testResumeStateMismatch() {
        val redirectEndSessionFlow = oktaRule.createOidcClient().createRedirectEndSessionFlow()
        val flowContext = RedirectEndSessionFlow.ResumeResult.Context(
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            url = "https://example.okta.com/not_used".toHttpUrl(),
        )
        val result = redirectEndSessionFlow.resume(
            uri = Uri.parse("unitTest:/logout?state=MISMATCH"),
            flowContext = flowContext,
        )
        assertThat(result).isInstanceOf(RedirectEndSessionFlow.Result.Error::class.java)
        val errorResult = result as RedirectEndSessionFlow.Result.Error
        assertThat(errorResult.message).isEqualTo("Failed due to state mismatch.")
    }

    @Test fun testResumeError() {
        val redirectEndSessionFlow = oktaRule.createOidcClient().createRedirectEndSessionFlow()
        val flowContext = RedirectEndSessionFlow.ResumeResult.Context(
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            url = "https://example.okta.com/not_used".toHttpUrl(),
        )
        val result = redirectEndSessionFlow.resume(
            uri = Uri.parse("unitTest:/logout?error=foo"),
            flowContext = flowContext,
        )
        assertThat(result).isInstanceOf(RedirectEndSessionFlow.Result.Error::class.java)
        val errorResult = result as RedirectEndSessionFlow.Result.Error
        assertThat(errorResult.message).isEqualTo("An error occurred.")
    }

    @Test fun testResumeErrorDescription() {
        val redirectEndSessionFlow = oktaRule.createOidcClient().createRedirectEndSessionFlow()
        val flowContext = RedirectEndSessionFlow.ResumeResult.Context(
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            url = "https://example.okta.com/not_used".toHttpUrl(),
        )
        val result = redirectEndSessionFlow.resume(
            uri = Uri.parse("unitTest:/logout?error=foo&error_description=Invalid%20Client%20Id"),
            flowContext = flowContext,
        )
        assertThat(result).isInstanceOf(RedirectEndSessionFlow.Result.Error::class.java)
        val errorResult = result as RedirectEndSessionFlow.Result.Error
        assertThat(errorResult.message).isEqualTo("Invalid Client Id")
    }
}
