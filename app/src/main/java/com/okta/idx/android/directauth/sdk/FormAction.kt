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
package com.okta.idx.android.directauth.sdk

import androidx.lifecycle.MutableLiveData
import com.okta.idx.android.directauth.sdk.forms.ForgotPasswordResetForm
import com.okta.idx.android.directauth.sdk.forms.ForgotPasswordSelectAuthenticatorForm
import com.okta.idx.android.directauth.sdk.forms.RegisterSelectAuthenticatorForm
import com.okta.idx.android.directauth.sdk.forms.UsernamePasswordForm
import com.okta.idx.android.directauth.sdk.models.AuthenticatorType
import com.okta.idx.sdk.api.client.IDXAuthenticationWrapper
import com.okta.idx.sdk.api.exception.ProcessingException
import com.okta.idx.sdk.api.model.AuthenticationStatus
import com.okta.idx.sdk.api.model.IDXClientContext
import com.okta.idx.sdk.api.response.AuthenticationResponse
import com.okta.idx.sdk.api.response.TokenResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Used when a Form UI Action occurs to initiate transition to another state.
 */
data class FormAction internal constructor(
    private val viewModelScope: CoroutineScope,
    private val stateLiveData: MutableLiveData<State>,
    private val authenticationWrapper: IDXAuthenticationWrapper,
) {
    sealed class State {
        data class Data(
            val form: Form,
            val messages: List<String> = emptyList(),
        ) : State()

        object Loading : State()
        class Success(val tokenResponse: TokenResponse) : State()
        class FailedToLoad(val messages: List<String>) : State()
    }

    internal sealed class ProceedTransition {
        class TokenTransition(val tokenResponse: TokenResponse) : ProceedTransition()
        class FormTransition(val form: Form) : ProceedTransition()
        class ErrorTransition(val errors: List<String>) : ProceedTransition()
    }

    internal class ProceedData(
        val authenticationWrapper: IDXAuthenticationWrapper
    ) {
        fun handleKnownTransitions(response: AuthenticationResponse): ProceedTransition? {
            if (response.tokenResponse != null) {
                return ProceedTransition.TokenTransition(response.tokenResponse)
            }
            if (response.authenticationStatus == null && response.errors.isNotEmpty()) {
                return ProceedTransition.ErrorTransition(response.errors)
            }
            return null
        }

        fun registerSelectAuthenticatorForm(
            idxClientContext: IDXClientContext,
            formAction: FormAction
        ): ProceedTransition {
            if (AuthenticationWrapper.isSkipAuthenticatorPresent(idxClient, idxClientContext)) {
                val response =
                    AuthenticationWrapper.skipAuthenticatorEnrollment(idxClient, idxClientContext)
                handleKnownTransitions(response)?.let { return@registerSelectAuthenticatorForm it }
            }

            val options =
                authenticationWrapper.populateAuthenticatorUIOptions(idxClientContext)
                    .map { uiOption ->
                        when (uiOption.type) {
                            AuthenticatorType.EMAIL.authenticatorTypeText -> {
                                AuthenticatorType.EMAIL
                            }
                            AuthenticatorType.PASSWORD.authenticatorTypeText -> {
                                AuthenticatorType.PASSWORD
                            }
                            AuthenticatorType.SMS.authenticatorTypeText -> {
                                AuthenticatorType.SMS
                            }
                            else -> throw IllegalArgumentException("Unsupported option: ${uiOption.type}")
                        }
                    }

            return ProceedTransition.FormTransition(
                RegisterSelectAuthenticatorForm(
                    RegisterSelectAuthenticatorForm.ViewModel(options, idxClientContext),
                    formAction
                )
            )
        }

        fun forgotPasswordSelectAuthenticatorForm(
            previousResponse: AuthenticationResponse,
            formAction: FormAction
        ): ProceedTransition {
            if (previousResponse.authenticationStatus == AuthenticationStatus.AWAITING_PASSWORD_RESET) {
                return ProceedTransition.FormTransition(
                    ForgotPasswordResetForm(viewModel = ForgotPasswordResetForm.ViewModel(
                        idxClientContext = previousResponse.idxClientContext),
                        formAction = formAction
                    )
                )
            }

            val options = authenticationWrapper.populateForgotPasswordAuthenticatorUIOptions(
                previousResponse.idxClientContext
            ).map { uiOption ->
                when (uiOption.type) {
                    AuthenticatorType.EMAIL.authenticatorTypeText -> {
                        AuthenticatorType.EMAIL
                    }
                    AuthenticatorType.SMS.authenticatorTypeText -> {
                        AuthenticatorType.SMS
                    }
                    else -> throw IllegalArgumentException("Unsupported option: ${uiOption.type}")
                }
            }

            return ProceedTransition.FormTransition(
                ForgotPasswordSelectAuthenticatorForm(
                    ForgotPasswordSelectAuthenticatorForm.ViewModel(options, previousResponse.idxClientContext),
                    formAction
                )
            )
        }

        internal suspend fun invokeTransitionFactory(transitionFactory: suspend ProceedData.() -> ProceedTransition): ProceedTransition {
            return transitionFactory()
        }
    }

    internal fun proceed(transitionFactory: suspend ProceedData.() -> ProceedTransition) {
        val initialState = stateLiveData.value as? State.Data ?: return

        stateLiveData.value = State.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                ProceedData(authenticationWrapper)
                    .invokeTransitionFactory(transitionFactory)
                    .handle(initialState)
            } catch (e: Exception) {
                Timber.e(e, "An error occurred.")
                stateLiveData.postValue(initialState.copy(messages = messagesFromException(e)))
            }
        }
    }

    internal fun transitionToForm(form: Form) {
        stateLiveData.value = State.Data(form)
    }

    private fun ProceedTransition.handle(initialState: State.Data) {
        when (this) {
            is ProceedTransition.TokenTransition -> {
                stateLiveData.postValue(State.Success(tokenResponse))
            }
            is ProceedTransition.ErrorTransition -> {
                stateLiveData.postValue(initialState.copy(messages = errors))
            }
            is ProceedTransition.FormTransition -> {
                stateLiveData.postValue(
                    State.Data(form)
                )
            }
        }
    }

    private fun messagesFromException(e: Exception): List<String> {
        return when (e) {
            is ProcessingException -> {
                val messages = e.errorResponse.messages
                messages?.value?.map {
                    it.message
                } ?: listOf("An error occurred.")
            }
            else -> {
                listOf("An error occurred.")
            }
        }
    }

    fun signOut() {
        transitionToForm(UsernamePasswordForm(formAction = this))
    }
}
