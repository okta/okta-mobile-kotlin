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
import com.okta.idx.kotlin.client.IdxClient
import com.okta.idx.kotlin.client.IdxClientConfiguration
import com.okta.idx.kotlin.client.IdxClientResult
import com.okta.idx.kotlin.infrastructure.network.NetworkRule
import com.okta.idx.kotlin.infrastructure.network.RequestMatchers.path
import com.okta.idx.kotlin.infrastructure.testBodyFromFile
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Rule
import org.junit.Test

class IdxPollTraitTest {
    @get:Rule val networkRule = NetworkRule()

    private fun getConfiguration() = IdxClientConfiguration(
        issuer = "https://test.okta.com/oauth2/default".toHttpUrl(),
        clientId = "test",
        scopes = setOf("openid", "email", "profile", "offline_access"),
        redirectUri = "test.okta.com/login",
        okHttpCallFactory = networkRule.okHttpClient(),
    )

    @Test fun testPoll(): Unit = runBlocking {
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
            response.testBodyFromFile("client/challengeAuthenticatorRemediationResponse.json")
        }

        val clientResult = IdxClient.start(getConfiguration()) as IdxClientResult.Success<IdxClient>
        val client = clientResult.result
        val resumeResult = client.resume() as IdxClientResult.Success<IdxResponse>
        val resumeResponse = resumeResult.result

        val pollTrait = resumeResponse.remediations[0].authenticators[0].traits.get<IdxPollTrait>()!!
        val pollResult = pollTrait.poll(client) as IdxClientResult.Success<IdxResponse>

        assertThat(pollResult.result.isLoginSuccessful).isTrue()
    }

    @Test fun testPollWithChange(): Unit = runBlocking {
        networkRule.enqueue(path("/oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("client/interactResponse.json")
        }
        networkRule.enqueue(path("/idp/idx/introspect")) { response ->
            response.testBodyFromFile("client/challengeAuthenticatorRemediationResponse.json")
        }
        networkRule.enqueue(path("/idp/idx/challenge/poll")) { response ->
            response.testBodyFromFile("client/selectAuthenticatorAuthenticateRemediationResponse.json")
        }

        val clientResult = IdxClient.start(getConfiguration()) as IdxClientResult.Success<IdxClient>
        val client = clientResult.result
        val resumeResult = client.resume() as IdxClientResult.Success<IdxResponse>
        val resumeResponse = resumeResult.result

        val pollTrait = resumeResponse.remediations[0].authenticators[0].traits.get<IdxPollTrait>()!!
        val pollResult = pollTrait.poll(client) as IdxClientResult.Success<IdxResponse>

        assertThat(pollResult.result.remediations.first().name).isEqualTo("select-authenticator-authenticate")
    }
}
