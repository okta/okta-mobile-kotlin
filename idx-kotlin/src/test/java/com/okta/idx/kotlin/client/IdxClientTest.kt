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

import com.google.common.truth.Truth.assertThat
import com.okta.idx.kotlin.dto.IdxRemediation
import com.okta.idx.kotlin.dto.IdxResponse
import com.okta.idx.kotlin.dto.TokenResponse
import com.okta.idx.kotlin.dto.createRemediation
import com.okta.idx.kotlin.infrastructure.network.NetworkRule
import com.okta.idx.kotlin.infrastructure.network.RequestMatchers.body
import com.okta.idx.kotlin.infrastructure.network.RequestMatchers.path
import com.okta.idx.kotlin.infrastructure.testBodyFromFile
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Rule
import org.junit.Test

class IdxClientTest {
    @get:Rule val networkRule = NetworkRule()

    private fun getConfiguration() = IdxClientConfiguration(
        issuer = "https://test.okta.com/oauth2/default".toHttpUrl(),
        clientId = "test",
        scopes = setOf("openid", "email", "profile", "offline_access"),
        redirectUri = "test.okta.com/login",
        okHttpCallFactory = networkRule.okHttpClient(),
    )

    @Test fun testStart(): Unit = runBlocking {
        networkRule.enqueue(path("/oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("client/interactResponse.json")
        }

        val clientResult = IdxClient.start(getConfiguration()) as IdxClientResult.Success<IdxClient>
        assertThat(clientResult.result.clientContext.interactionHandle).isEqualTo("029ZAB")
    }

    @Test fun testResume(): Unit = runBlocking {
        networkRule.enqueue(path("/oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("client/interactResponse.json")
        }
        val introspectBody = """{"interactionHandle":"029ZAB"}"""
        networkRule.enqueue(path("/idp/idx/introspect"), body(introspectBody)) { response ->
            response.testBodyFromFile("client/identifyRemediationResponse.json")
        }

        val clientResult = IdxClient.start(getConfiguration()) as IdxClientResult.Success<IdxClient>
        val client = clientResult.result
        assertThat(client.clientContext.interactionHandle).isEqualTo("029ZAB")

        val resumeResult = client.resume() as IdxClientResult.Success<IdxResponse>
        assertThat(resumeResult.result.remediations).hasSize(4)
    }

    @Test fun testProceed(): Unit = runBlocking {
        networkRule.enqueue(path("/oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("client/interactResponse.json")
        }
        networkRule.enqueue(path("/idp/idx/introspect")) { response ->
            response.testBodyFromFile("client/identifyRemediationResponse.json")
        }
        val identifyBody = """{"identifier":"test@okta.com","credentials":{"passcode":"example"},"stateHandle":"029ZAB"}"""
        networkRule.enqueue(path("/idp/idx/identify"), body(identifyBody)) { response ->
            response.testBodyFromFile("client/successWithInteractionCodeResponse.json")
        }

        val clientResult = IdxClient.start(getConfiguration()) as IdxClientResult.Success<IdxClient>
        val client = clientResult.result
        assertThat(client.clientContext.interactionHandle).isEqualTo("029ZAB")

        val resumeResult = client.resume() as IdxClientResult.Success<IdxResponse>
        val resumeResponse = resumeResult.result
        assertThat(resumeResponse.remediations).hasSize(4)

        val identifyRemediation = resumeResponse.remediations[0]
        identifyRemediation["identifier"]?.value = "test@okta.com"
        identifyRemediation["credentials.passcode"]?.value = "example"
        val proceedResult = client.proceed(identifyRemediation) as IdxClientResult.Success<IdxResponse>
        assertThat(proceedResult.result.remediations[1].type).isEqualTo(IdxRemediation.Type.ISSUE)
    }

    @Test fun testProceedCopiesRemediationValues(): Unit = runBlocking {
        networkRule.enqueue(path("/oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("client/interactResponse.json")
        }
        networkRule.enqueue(path("/idp/idx/introspect")) { response ->
            response.testBodyFromFile("client/identifyRemediationResponse.json")
        }
        val identifyBody = """{"identifier":"test@okta.com","credentials":{"passcode":"example"},"stateHandle":"029ZAB"}"""
        networkRule.enqueue(path("/idp/idx/identify"), body(identifyBody)) { response ->
            response.testBodyFromFile("client/identifyRemediationResponse.json")
        }

        val clientResult = IdxClient.start(getConfiguration()) as IdxClientResult.Success<IdxClient>
        val client = clientResult.result
        assertThat(client.clientContext.interactionHandle).isEqualTo("029ZAB")

        val resumeResult = client.resume() as IdxClientResult.Success<IdxResponse>
        val resumeResponse = resumeResult.result
        assertThat(resumeResponse.remediations).hasSize(4)

        val identifyRemediation = resumeResponse.remediations[0]
        identifyRemediation["identifier"]?.value = "test@okta.com"
        identifyRemediation["credentials.passcode"]?.value = "example"
        val proceedResult = client.proceed(identifyRemediation) as IdxClientResult.Success<IdxResponse>
        val newIdentifyRemediation = proceedResult.result.remediations[0]
        assertThat(newIdentifyRemediation["identifier"]?.value).isEqualTo("test@okta.com")
        assertThat(newIdentifyRemediation["credentials.passcode"]?.value).isEqualTo("example")
    }

    @Test fun testExchangeCodes(): Unit = runBlocking {
        networkRule.enqueue(path("/oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("client/interactResponse.json")
        }
        networkRule.enqueue(path("/idp/idx/introspect")) { response ->
            response.testBodyFromFile("client/identifyRemediationResponse.json")
        }
        networkRule.enqueue(path("/idp/idx/identify")) { response ->
            response.testBodyFromFile("client/successWithInteractionCodeResponse.json")
        }
        networkRule.enqueue(path("/oauth2/v1/token")) { response ->
            response.testBodyFromFile("client/tokenResponse.json")
        }

        val clientResult = IdxClient.start(getConfiguration()) as IdxClientResult.Success<IdxClient>
        val client = clientResult.result
        assertThat(client.clientContext.interactionHandle).isEqualTo("029ZAB")

        val resumeResult = client.resume() as IdxClientResult.Success<IdxResponse>
        val resumeResponse = resumeResult.result
        assertThat(resumeResponse.remediations).hasSize(4)

        val identifyRemediation = resumeResponse.remediations[0]
        identifyRemediation["identifier"]?.value = "test@okta.com"
        identifyRemediation["credentials.passcode"]?.value = "example"
        val proceedResult = client.proceed(identifyRemediation) as IdxClientResult.Success<IdxResponse>
        val proceedResponse = proceedResult.result
        val issueRemediation = proceedResponse.remediations[1]
        assertThat(issueRemediation.type).isEqualTo(IdxRemediation.Type.ISSUE)

        val tokenResult = client.exchangeInteractionCodeForTokens(issueRemediation) as IdxClientResult.Success<TokenResponse>
        assertThat(tokenResult.result.accessToken).isEqualTo("eyJraWQiOiJBaE1qU3VMQWdBTDJ1dHVVY2lFRWJ2R1JUbi1GRkt1Y2tVTDJibVZMVmp3IiwiYWxnIjoiUlMyNTYifQ.eyJ2ZXIiOjEsImp0aSI6IkFULm01N1NsVUpMRUQyT1RtLXVrUFBEVGxFY0tialFvYy1wVGxVdm5ha0k3T1Eub2FyNjFvOHVVOVlGVnBYcjYybzQiLCJpc3MiOiJodHRwczovL2Zvby5wcmV2aWV3LmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6ImFwaTovL2RlZmF1bHQiLCJpYXQiOjE2MDg1NjcwMTgsImV4cCI6MTYwODU3MDYxOCwiY2lkIjoiMG9henNtcHhacFZFZzRjaFMybzQiLCJ1aWQiOiIwMHUxMGt2dkZDMDZHT21odTJvNSIsInNjcCI6WyJvcGVuaWQiLCJwcm9maWxlIiwib2ZmbGluZV9hY2Nlc3MiXSwic3ViIjoiZm9vQG9rdGEuY29tIn0.lg2T8dKVfic_JU6qzNBqDuw3RFUq7Da5UO37eY3W-cOOb9UqijxGYj7d-z8qK1UJjRRcDg-rTMzYQbKCLVxjBw")
    }

    @Test fun testExchangeCodeWithWrongRemediationType(): Unit = runBlocking {
        networkRule.enqueue(path("/oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("client/interactResponse.json")
        }

        val clientResult = IdxClient.start(getConfiguration()) as IdxClientResult.Success<IdxClient>
        assertThat(clientResult.result.clientContext.interactionHandle).isEqualTo("029ZAB")

        val client = clientResult.result
        val exchangeCodesResult = client.exchangeInteractionCodeForTokens(createRemediation(emptyList())) as IdxClientResult.Error<TokenResponse>
        assertThat(exchangeCodesResult.exception.message).isEqualTo("Invalid remediation.")
    }
}
