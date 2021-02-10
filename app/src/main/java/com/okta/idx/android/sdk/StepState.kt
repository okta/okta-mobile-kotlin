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
import com.okta.idx.sdk.api.model.RemediationOption
import com.okta.idx.sdk.api.model.UserProfile
import com.okta.idx.sdk.api.request.AnswerChallengeRequestBuilder
import com.okta.idx.sdk.api.request.ChallengeRequestBuilder
import com.okta.idx.sdk.api.request.EnrollRequestBuilder
import com.okta.idx.sdk.api.request.EnrollUserProfileUpdateRequestBuilder
import com.okta.idx.sdk.api.request.IdentifyRequestBuilder
import com.okta.idx.sdk.api.request.SkipAuthenticatorEnrollmentRequestBuilder
import com.okta.idx.sdk.api.response.IDXResponse
import com.okta.idx.sdk.api.response.TokenResponse

data class StepState(
    private val idxClient: IDXClient,
    private val stateHandle: String,
) {
    fun identify(
        remediationOption: RemediationOption,
        requestMutator: IdentifyRequestBuilder.() -> Unit = {}
    ): IDXResponse {
        val identifyRequest = IdentifyRequestBuilder.builder()
            .withStateHandle(stateHandle)
            .apply(requestMutator)
            .build()
        return remediationOption.proceed(idxClient, identifyRequest)
    }

    fun challenge(
        remediationOption: RemediationOption,
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
        return remediationOption.proceed(idxClient, challengeRequest)
    }

    fun answer(remediationOption: RemediationOption, credentials: Credentials): IDXResponse {
        val answerChallengeRequest = AnswerChallengeRequestBuilder.builder()
            .withStateHandle(stateHandle)
            .withCredentials(credentials)
            .build()
        return remediationOption.proceed(idxClient, answerChallengeRequest)
    }

    fun enroll(
        remediationOption: RemediationOption,
        authenticator: Authenticator? = null
    ): IDXResponse {
        return remediationOption.proceed(
            idxClient,
            EnrollRequestBuilder.builder()
                .apply {
                    if (authenticator != null) {
                        withAuthenticator(authenticator)
                    }
                }
                .withStateHandle(stateHandle)
                .build()
        )
    }

    fun enrollUserProfile(
        remediationOption: RemediationOption,
        attributes: Map<String, Any>,
    ): IDXResponse {
        val userProfile = UserProfile()
        for (attribute in attributes) {
            userProfile.addAttribute(attribute.key, attribute.value)
        }
        val request = EnrollUserProfileUpdateRequestBuilder.builder()
            .withUserProfile(userProfile)
            .withStateHandle(stateHandle)
            .build()
        return remediationOption.proceed(idxClient, request)
    }

    fun token(response: IDXResponse): TokenResponse {
        return response.successWithInteractionCode.exchangeCode(idxClient)
    }

    fun cancel(): IDXResponse {
        return idxClient.cancel(stateHandle)
    }

    fun skip(remediationOption: RemediationOption, ): IDXResponse {
        return remediationOption.proceed(
            idxClient,
            SkipAuthenticatorEnrollmentRequestBuilder.builder()
                .withStateHandle(stateHandle)
                .build()
        )
    }
}
