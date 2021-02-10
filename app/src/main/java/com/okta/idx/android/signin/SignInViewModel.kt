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
package com.okta.idx.android.signin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.idx.android.network.Network
import com.okta.idx.sdk.api.client.IDXClient
import com.okta.idx.sdk.api.model.Credentials
import com.okta.idx.sdk.api.model.RemediationOption
import com.okta.idx.sdk.api.request.AnswerChallengeRequestBuilder
import com.okta.idx.sdk.api.request.IdentifyRequestBuilder
import com.okta.idx.sdk.api.response.IDXResponse
import com.okta.idx.sdk.api.response.TokenResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Optional

internal class SignInViewModel : ViewModel() {
    private val _stateLiveData = MutableLiveData<SignInState>(SignInState.Idle)
    val stateLiveData: LiveData<SignInState> = _stateLiveData

    fun signIn(username: String, password: String) {
        _stateLiveData.value = SignInState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tokenResponse = Idx(username, password).signIn()
                _stateLiveData.postValue(SignInState.Success(tokenResponse))
            } catch (e: Exception) {
                Timber.e(e, "An error occurred.")
                _stateLiveData.postValue(SignInState.Failure("An error occurred."))
            }
        }
    }

    fun acknowledgeFailure() {
        _stateLiveData.value = SignInState.Idle
    }

    private inner class Idx(private val username: String, private val password: String) {
        private val idxClient: IDXClient = Network.idxClient()
        private lateinit var stateHandle: String

        fun steps(): List<(previousResponse: IDXResponse) -> IDXResponse> {
            val steps = mutableListOf<(previousResponse: IDXResponse) -> IDXResponse>()

            steps += { previousResponse ->
                val remediationOption = previousResponse.remediation().remediationOptions()[0]
                val formValues = remediationOption.form()
                val requiresPassword = formValues.any { it.name == "credentials" }
                identify(remediationOption, requiresPassword)
            }

            steps += { previousResponse ->
                val remediationOption = previousResponse.remediation().remediationOptions()[0]
                answer(remediationOption, Credentials().apply { passcode = password.toCharArray() })
            }

            return steps
        }

        fun signIn(): TokenResponse {
            val interactHandle = idxClient.interact().interactionHandle
            var response: IDXResponse = idxClient.introspect(Optional.of(interactHandle))
            stateHandle = response.stateHandle

            steps().forEach { step ->
                if (response.isLoginSuccessful) {
                    return@forEach
                }

                response = step(response)
            }

            if (response.isLoginSuccessful) {
                return response.successWithInteractionCode.exchangeCode(idxClient)
            } else {
                throw IllegalStateException("Login failed.")
            }
        }

        private fun identify(
            remediationOption: RemediationOption,
            requiresPassword: Boolean
        ): IDXResponse {
            val identifyRequest = IdentifyRequestBuilder.builder()
                .withIdentifier(username)
                .apply {
                    if (requiresPassword) {
                        withCredentials(Credentials().apply { passcode = password.toCharArray() })
                    }
                }
                .withRememberMe(false)
                .withStateHandle(stateHandle)
                .build()
            return remediationOption.proceed(idxClient, identifyRequest)
        }

        private fun answer(
            remediationOption: RemediationOption,
            credentials: Credentials
        ): IDXResponse {
            val answerChallengeRequest = AnswerChallengeRequestBuilder.builder()
                .withStateHandle(stateHandle)
                .withCredentials(credentials)
                .build()
            return remediationOption.proceed(idxClient, answerChallengeRequest)
        }
    }
}

internal sealed class SignInState {
    object Idle : SignInState()
    object Loading : SignInState()
    class Success(val tokenResponse: TokenResponse) : SignInState()
    data class Failure(val message: String) : SignInState()
}
