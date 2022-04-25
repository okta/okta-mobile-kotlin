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
package com.okta.idx.kotlin.client

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.okta.idx.kotlin.infrastructure.network.NetworkRule
import com.okta.idx.kotlin.infrastructure.network.RequestMatchers.body
import com.okta.idx.kotlin.infrastructure.network.RequestMatchers.path
import com.okta.idx.kotlin.infrastructure.testBodyFromFile
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IdxRedirectResultTest {
    @get:Rule val networkRule = NetworkRule()

    private fun createClient(): IdxFlow {
        val flowContext = IdxFlowContext("abcd", "bcde", "cdef", "test.okta.com/login")
        return IdxFlow(networkRule.createOidcClient(), flowContext)
    }

    @Test fun testRedirectResultInvalidUrl(): Unit = runBlocking {
        val uri = Uri.parse("test.okta.com/login")
        val client = createClient()
        val result = client.evaluateRedirectUri(uri) as IdxRedirectResult.Error
        assertThat(result.errorMessage).isEqualTo("Unable to handle redirect url.")
        assertThat(result.exception).isNull()
    }

    @Test fun testRedirectResultMismatchRedirectUri(): Unit = runBlocking {
        val uri = Uri.parse("test.hacker.com/login")
        val client = createClient()
        val result = client.evaluateRedirectUri(uri) as IdxRedirectResult.Error
        assertThat(result.errorMessage).isEqualTo("IDP redirect failed due not matching the configured redirect uri.")
        assertThat(result.exception).isNull()
    }

    @Test fun testRedirectResultInteractionRequired(): Unit = runBlocking {
        networkRule.enqueue(path("/idp/idx/introspect")) { response ->
            response.testBodyFromFile("client/selectAuthenticatorAuthenticateRemediationResponse.json")
        }

        val uri = Uri.parse("test.okta.com/login?state=cdef&error=interaction_required&error_description=Your+client+is+configured+to+use+the+interaction+code+flow+and+user+interaction+is+required+to+complete+the+request.")
        val client = createClient()
        val redirectResult = client.evaluateRedirectUri(uri) as IdxRedirectResult.InteractionRequired
        assertThat(redirectResult.response.remediations.first().name).isEqualTo("select-authenticator-authenticate")
    }

    @Test fun testRedirectResultInteractionRequiredFailsWithMismatchState(): Unit = runBlocking {
        val uri = Uri.parse("test.okta.com/login?state=mismatch&error=interaction_required&error_description=Your+client+is+configured+to+use+the+interaction+code+flow+and+user+interaction+is+required+to+complete+the+request.")
        val client = createClient()
        val redirectResult = client.evaluateRedirectUri(uri) as IdxRedirectResult.Error
        assertThat(redirectResult.errorMessage).isEqualTo("IDP redirect failed due to state mismatch.")
        assertThat(redirectResult.exception).isNull()
    }

    @Test fun testRedirectResultInteractionRequiredIntrospectError(): Unit = runBlocking {
        networkRule.enqueue(path("/idp/idx/introspect")) { response ->
            response.socketPolicy = SocketPolicy.DISCONNECT_AT_START
        }

        val uri = Uri.parse("test.okta.com/login?state=cdef&error=interaction_required&error_description=Your+client+is+configured+to+use+the+interaction+code+flow+and+user+interaction+is+required+to+complete+the+request.")
        val client = createClient()
        val redirectResult = client.evaluateRedirectUri(uri) as IdxRedirectResult.Error
        assertThat(redirectResult.errorMessage).isEqualTo("Failed to resume.")
        assertThat(redirectResult.exception).isNotNull()
    }

    @Test fun testRedirectResultInteractionCode(): Unit = runBlocking {
        val body = "grant_type=interaction_code&client_id=test&interaction_code=exampleInteractionCode&code_verifier=abcd"
        networkRule.enqueue(path("/oauth2/default/v1/token"), body(body)) { response ->
            response.testBodyFromFile("client/tokenResponse.json")
        }

        val uri = Uri.parse("test.okta.com/login?interaction_code=exampleInteractionCode&state=cdef")
        val client = createClient()
        val tokenResult = client.evaluateRedirectUri(uri) as IdxRedirectResult.Tokens
        assertThat(tokenResult.response.accessToken).isEqualTo("eyJraWQiOiJBaE1qU3VMQWdBTDJ1dHVVY2lFRWJ2R1JUbi1GRkt1Y2tVTDJibVZMVmp3IiwiYWxnIjoiUlMyNTYifQ.eyJ2ZXIiOjEsImp0aSI6IkFULm01N1NsVUpMRUQyT1RtLXVrUFBEVGxFY0tialFvYy1wVGxVdm5ha0k3T1Eub2FyNjFvOHVVOVlGVnBYcjYybzQiLCJpc3MiOiJodHRwczovL2Zvby5wcmV2aWV3LmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6ImFwaTovL2RlZmF1bHQiLCJpYXQiOjE2MDg1NjcwMTgsImV4cCI6MTYwODU3MDYxOCwiY2lkIjoiMG9henNtcHhacFZFZzRjaFMybzQiLCJ1aWQiOiIwMHUxMGt2dkZDMDZHT21odTJvNSIsInNjcCI6WyJvcGVuaWQiLCJwcm9maWxlIiwib2ZmbGluZV9hY2Nlc3MiXSwic3ViIjoiZm9vQG9rdGEuY29tIn0.lg2T8dKVfic_JU6qzNBqDuw3RFUq7Da5UO37eY3W-cOOb9UqijxGYj7d-z8qK1UJjRRcDg-rTMzYQbKCLVxjBw")
    }

    @Test fun testRedirectResultInteractionCodeErrorParamShowsErrorDescription(): Unit = runBlocking {
        val uri = Uri.parse("test.okta.com/login?error=foo&error_description=Server%20Error")
        val client = createClient()
        val redirectResult = client.evaluateRedirectUri(uri) as IdxRedirectResult.Error
        assertThat(redirectResult.errorMessage).isEqualTo("Server Error")
    }

    @Test fun testRedirectResultInteractionCodeErrorParamShowsDefaultError(): Unit = runBlocking {
        val uri = Uri.parse("test.okta.com/login?error=foo")
        val client = createClient()
        val redirectResult = client.evaluateRedirectUri(uri) as IdxRedirectResult.Error
        assertThat(redirectResult.errorMessage).isEqualTo("An error occurred.")
    }

    @Test fun testRedirectResultInteractionCodeStateMismatch(): Unit = runBlocking {
        val uri = Uri.parse("test.okta.com/login?interaction_code=exampleInteractionCode&state=mismatch")
        val client = createClient()
        val redirectResult = client.evaluateRedirectUri(uri) as IdxRedirectResult.Error
        assertThat(redirectResult.errorMessage).isEqualTo("IDP redirect failed due to state mismatch.")
        assertThat(redirectResult.exception).isNull()
    }

    @Test fun testRedirectResultInteractionCodeExchangeTokensError(): Unit = runBlocking {
        val body = "grant_type=interaction_code&client_id=test&interaction_code=exampleInteractionCode&code_verifier=abcd"
        networkRule.enqueue(path("/oauth2/default/v1/token"), body(body)) { response ->
            response.socketPolicy = SocketPolicy.DISCONNECT_AT_START
        }

        val uri = Uri.parse("test.okta.com/login?interaction_code=exampleInteractionCode&state=cdef")
        val client = createClient()
        val result = client.evaluateRedirectUri(uri) as IdxRedirectResult.Error
        assertThat(result.errorMessage).isEqualTo("Failed to exchangeCodes.")
        assertThat(result.exception).isNotNull()
    }
}
