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
package com.okta.idx.kotlin.dto

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.client.OidcClientResult
import com.okta.idx.kotlin.client.IdxFlow
import com.okta.idx.kotlin.client.IdxFlow.Companion.createIdxFlow
import com.okta.idx.kotlin.infrastructure.network.NetworkRule
import com.okta.idx.kotlin.infrastructure.network.RequestMatchers.path
import com.okta.idx.kotlin.infrastructure.testBodyFromFile
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class IdxPollCapabilityTest {
    @get:Rule val networkRule = NetworkRule()

    @Test fun testAuthenticatorPoll(): Unit = runBlocking {
        networkRule.enqueue(path("/oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("client/interactResponse.json")
        }
        networkRule.enqueue(path("/idp/idx/introspect")) { response ->
            response.testBodyFromFile("client/challengeAuthenticatorRemediationResponse.json")
        }
        networkRule.enqueue(path("/idp/idx/challenge/poll")) { response ->
            response.testBodyFromFile("client/successWithInteractionCodeResponse.json")
        }
        networkRule.enqueue(path("/idp/idx/challenge/poll")) { response ->
            response.testBodyFromFile("client/challengeAuthenticatorRemediationResponseLongPoll.json")
        }

        val clientResult = networkRule.createOidcClient().createIdxFlow() as OidcClientResult.Success<IdxFlow>
        val client = clientResult.result
        val resumeResult = client.resume() as OidcClientResult.Success<IdxResponse>
        val resumeResponse = resumeResult.result

        val capability = resumeResponse.remediations[0].authenticators[0].capabilities.get<IdxPollAuthenticatorCapability>()!!
        val delays = mutableListOf<Long>()
        capability.delayFunction = { delays += it }
        val pollResult = capability.poll(client) as OidcClientResult.Success<IdxResponse>

        assertThat(pollResult.result.isLoginSuccessful).isTrue()
        assertThat(delays).containsExactly(4000L, 8000L)
    }

    @Test fun testAuthenticatorPollWithChange(): Unit = runBlocking {
        networkRule.enqueue(path("/oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("client/interactResponse.json")
        }
        networkRule.enqueue(path("/idp/idx/introspect")) { response ->
            response.testBodyFromFile("client/challengeAuthenticatorRemediationResponse.json")
        }
        networkRule.enqueue(path("/idp/idx/challenge/poll")) { response ->
            response.testBodyFromFile("client/selectAuthenticatorAuthenticateRemediationResponse.json")
        }

        val clientResult = networkRule.createOidcClient().createIdxFlow() as OidcClientResult.Success<IdxFlow>
        val client = clientResult.result
        val resumeResult = client.resume() as OidcClientResult.Success<IdxResponse>
        val resumeResponse = resumeResult.result

        val capability = resumeResponse.remediations[0].authenticators[0].capabilities.get<IdxPollAuthenticatorCapability>()!!
        val delays = mutableListOf<Long>()
        capability.delayFunction = { delays += it }
        val pollResult = capability.poll(client) as OidcClientResult.Success<IdxResponse>

        assertThat(pollResult.result.remediations.first().name).isEqualTo("select-authenticator-authenticate")
        assertThat(delays).containsExactly(4000L)
    }

    @Test fun testRemediationPoll(): Unit = runBlocking {
        networkRule.enqueue(path("/oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("client/interactResponse.json")
        }
        networkRule.enqueue(path("/idp/idx/introspect")) { response ->
            response.testBodyFromFile("client/challengePollRemediationResponse.json")
        }
        networkRule.enqueue(path("/idp/idx/challenge/poll")) { response ->
            response.testBodyFromFile("client/successWithInteractionCodeResponse.json")
        }
        networkRule.enqueue(path("/idp/idx/challenge/poll")) { response ->
            response.testBodyFromFile("client/challengePollRemediationResponseLong.json")
        }

        val clientResult = networkRule.createOidcClient().createIdxFlow() as OidcClientResult.Success<IdxFlow>
        val client = clientResult.result
        val resumeResult = client.resume() as OidcClientResult.Success<IdxResponse>
        val resumeResponse = resumeResult.result

        val capability = resumeResponse.remediations[0].capabilities.get<IdxPollRemediationCapability>()!!
        val delays = mutableListOf<Long>()
        capability.delayFunction = { delays += it }
        val pollResult = capability.poll(client) as OidcClientResult.Success<IdxResponse>

        assertThat(pollResult.result.isLoginSuccessful).isTrue()
        assertThat(delays).containsExactly(4000L, 8000L)
    }

    @Test fun testRemediationPollWithChange(): Unit = runBlocking {
        networkRule.enqueue(path("/oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("client/interactResponse.json")
        }
        networkRule.enqueue(path("/idp/idx/introspect")) { response ->
            response.testBodyFromFile("client/challengePollRemediationResponse.json")
        }
        networkRule.enqueue(path("/idp/idx/challenge/poll")) { response ->
            response.testBodyFromFile("client/selectAuthenticatorAuthenticateRemediationResponse.json")
        }

        val clientResult = networkRule.createOidcClient().createIdxFlow() as OidcClientResult.Success<IdxFlow>
        val client = clientResult.result
        val resumeResult = client.resume() as OidcClientResult.Success<IdxResponse>
        val resumeResponse = resumeResult.result

        val capability = resumeResponse.remediations[0].capabilities.get<IdxPollRemediationCapability>()!!
        val delays = mutableListOf<Long>()
        capability.delayFunction = { delays += it }
        val pollResult = capability.poll(client) as OidcClientResult.Success<IdxResponse>

        assertThat(pollResult.result.remediations.first().name).isEqualTo("select-authenticator-authenticate")
        assertThat(delays).containsExactly(4000L)
    }
}
