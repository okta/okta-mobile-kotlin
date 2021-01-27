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
package com.okta.idx.android.sdk

import com.okta.idx.sdk.api.client.IDXClient
import com.okta.idx.sdk.api.model.Authenticator
import com.okta.idx.sdk.api.model.Credentials
import com.okta.idx.sdk.api.request.AnswerChallengeRequestBuilder
import com.okta.idx.sdk.api.request.ChallengeRequestBuilder
import com.okta.idx.sdk.api.request.EnrollRequestBuilder
import com.okta.idx.sdk.api.request.IdentifyRequestBuilder
import com.okta.idx.sdk.api.response.IDXResponse
import com.okta.idx.sdk.api.response.TokenResponse

data class StepState(
    private val idxClient: IDXClient,
    private val stateHandle: String,
) {
    fun identify(
        username: String,
        rememberMe: Boolean,
        requestMutator: IdentifyRequestBuilder.() -> Unit = {}
    ): IDXResponse {
        val identifyRequest = IdentifyRequestBuilder.builder()
            .withIdentifier(username)
            .withRememberMe(rememberMe)
            .withStateHandle(stateHandle)
            .apply(requestMutator)
            .build()
        return idxClient.identify(identifyRequest)
    }

    fun challenge(
        id: String,
        methodType: String
    ): IDXResponse {
        val authenticator = Authenticator()
        authenticator.id = id
        authenticator.methodType = methodType
        val challengeRequest = ChallengeRequestBuilder.builder()
            .withStateHandle(stateHandle)
            .withAuthenticator(authenticator)
            .build()
        return idxClient.challenge(challengeRequest)
    }

    fun answer(credentials: Credentials): IDXResponse {
        val answerChallengeRequest = AnswerChallengeRequestBuilder.builder()
            .withStateHandle(stateHandle)
            .withCredentials(credentials)
            .build()
        return idxClient.answerChallenge(answerChallengeRequest)
    }

    fun enroll(authenticator: Authenticator): IDXResponse {
        return idxClient.enroll(
            EnrollRequestBuilder.builder()
                .withAuthenticator(authenticator)
                .withStateHandle(stateHandle)
                .build()
        )
    }

    fun token(response: IDXResponse): TokenResponse {
        return response.successWithInteractionCode.exchangeCode(idxClient)
    }

    fun cancel(): IDXResponse {
        return idxClient.cancel(stateHandle)
    }
}
