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
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.RequestMatchers
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
        val redirectEndSessionFlow = RedirectEndSessionFlow()
        assertThat(oktaRule.eventHandler).isEmpty()
        val result = redirectEndSessionFlow.start(
            idToken = "exampleIdToken",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            redirectUrl = "unitTest:/logout",
        )

        val context = (result as OAuth2ClientResult.Success<RedirectEndSessionFlow.Context>).result

        assertThat(context.state).isEqualTo("25c1d684-8d30-42e3-acc0-b74b35fd47b4")
        val expectedUrlEnding = "/oauth2/default/v1/logout?id_token_hint=exampleIdToken&post_logout_redirect_uri=unitTest%3A%2Flogout&state=25c1d684-8d30-42e3-acc0-b74b35fd47b4"
        assertThat(context.url.toString()).endsWith(expectedUrlEnding)
    }

    @Test fun testStartWithNoEndpoints() = runBlocking {
        oktaRule.enqueue(RequestMatchers.path("/.well-known/openid-configuration")) { response ->
            response.setResponseCode(503)
        }
        val redirectEndSessionFlow = RedirectEndSessionFlow(oktaRule.configuration)
        assertThat(oktaRule.eventHandler).isEmpty()
        val result = redirectEndSessionFlow.start(
            idToken = "exampleIdToken",
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            redirectUrl = "unitTest:/logout",
        )

        val context = (result as OAuth2ClientResult.Error<RedirectEndSessionFlow.Context>)
        assertThat(context.exception).isInstanceOf(OAuth2ClientResult.Error.OidcEndpointsNotAvailableException::class.java)
        assertThat(context.exception).hasMessageThat().isEqualTo("OIDC Endpoints not available.")
    }

    @Test fun testResume() {
        val redirectEndSessionFlow = RedirectEndSessionFlow()
        val flowContext = RedirectEndSessionFlow.Context(
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            url = "https://example.okta.com/not_used".toHttpUrl(),
            redirectUrl = "unitTest:/logout",
        )
        val result = redirectEndSessionFlow.resume(
            uri = Uri.parse("unitTest:/logout?state=${flowContext.state}"),
            flowContext = flowContext,
        )
        assertThat(result).isInstanceOf(OAuth2ClientResult.Success::class.java)
    }

    @Test fun testResumeSchemeMismatch() {
        val redirectEndSessionFlow = RedirectEndSessionFlow()
        val flowContext = RedirectEndSessionFlow.Context(
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            url = "https://example.okta.com/not_used".toHttpUrl(),
            redirectUrl = "unitTest:/logout",
        )
        val result = redirectEndSessionFlow.resume(
            uri = Uri.parse("wrong:/logout?state=${flowContext.state}"),
            flowContext = flowContext,
        )
        val errorResult = result as OAuth2ClientResult.Error<Unit>
        assertThat(errorResult.exception).isInstanceOf(RedirectEndSessionFlow.RedirectSchemeMismatchException::class.java)
    }

    @Test fun testResumeStateMismatch() {
        val redirectEndSessionFlow = RedirectEndSessionFlow()
        val flowContext = RedirectEndSessionFlow.Context(
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            url = "https://example.okta.com/not_used".toHttpUrl(),
            redirectUrl = "unitTest:/logout",
        )
        val result = redirectEndSessionFlow.resume(
            uri = Uri.parse("unitTest:/logout?state=MISMATCH"),
            flowContext = flowContext,
        )
        assertThat(result).isInstanceOf(OAuth2ClientResult.Error::class.java)
        val errorResult = result as OAuth2ClientResult.Error<Unit>
        assertThat(errorResult.exception).isInstanceOf(RedirectEndSessionFlow.ResumeException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("Failed due to state mismatch.")
    }

    @Test fun testResumeError() {
        val redirectEndSessionFlow = RedirectEndSessionFlow()
        val flowContext = RedirectEndSessionFlow.Context(
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            url = "https://example.okta.com/not_used".toHttpUrl(),
            redirectUrl = "unitTest:/logout",
        )
        val result = redirectEndSessionFlow.resume(
            uri = Uri.parse("unitTest:/logout?error=foo"),
            flowContext = flowContext,
        )
        assertThat(result).isInstanceOf(OAuth2ClientResult.Error::class.java)
        val errorResult = result as OAuth2ClientResult.Error<Unit>
        assertThat(errorResult.exception).isInstanceOf(RedirectEndSessionFlow.ResumeException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("An error occurred.")
    }

    @Test fun testResumeErrorDescription() {
        val redirectEndSessionFlow = RedirectEndSessionFlow()
        val flowContext = RedirectEndSessionFlow.Context(
            state = "25c1d684-8d30-42e3-acc0-b74b35fd47b4",
            url = "https://example.okta.com/not_used".toHttpUrl(),
            redirectUrl = "unitTest:/logout",
        )
        val result = redirectEndSessionFlow.resume(
            uri = Uri.parse("unitTest:/logout?error=foo&error_description=Invalid%20Client%20Id"),
            flowContext = flowContext,
        )
        assertThat(result).isInstanceOf(OAuth2ClientResult.Error::class.java)
        val errorResult = result as OAuth2ClientResult.Error<Unit>
        assertThat(errorResult.exception).isInstanceOf(RedirectEndSessionFlow.ResumeException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("Invalid Client Id")
    }
}
