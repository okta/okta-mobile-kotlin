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
import com.okta.idx.android.directauth.TestingGlobals
import com.okta.idx.android.directauth.sdk.forms.LaunchForm
import com.okta.idx.android.directauth.sdk.forms.RegisterForm
import com.okta.idx.android.directauth.sdk.forms.RegisterPasswordForm
import com.okta.idx.android.directauth.sdk.forms.RegisterPhoneForm
import com.okta.idx.android.directauth.sdk.forms.SelectAuthenticatorForm
import com.okta.idx.android.directauth.sdk.forms.SelectFactorForm
import com.okta.idx.android.directauth.sdk.forms.UsernamePasswordForm
import com.okta.idx.android.directauth.sdk.forms.VerifyCodeForm
import com.okta.idx.sdk.api.client.Authenticator
import com.okta.idx.sdk.api.client.IDXAuthenticationWrapper
import com.okta.idx.sdk.api.client.ProceedContext
import com.okta.idx.sdk.api.exception.ProcessingException
import com.okta.idx.sdk.api.model.AuthenticationStatus
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
            val proceedContext: ProceedContext? = null,
        ) : State()

        object Loading : State()
        class Success(val tokenResponse: TokenResponse) : State()
        class FailedToLoad(val messages: List<String>) : State()
    }

    internal sealed class ProceedTransition {
        class TokenTransition(val tokenResponse: TokenResponse) : ProceedTransition()
        class FormTransition(val form: Form, val proceedContext: ProceedContext?) :
            ProceedTransition()

        class ErrorTransition(val errors: List<String>) : ProceedTransition()
        class TerminalTransition(val errors: List<String>) : ProceedTransition()

        object ClearErrorsTransition : ProceedTransition()
    }

    internal class ProceedData(
        val authenticationWrapper: IDXAuthenticationWrapper,
        val proceedContext: ProceedContext?,
        private val formAction: FormAction,
    ) {
        fun handleTerminalTransitions(response: AuthenticationResponse): ProceedTransition? {
            if (response.tokenResponse != null) {
                return ProceedTransition.TokenTransition(response.tokenResponse)
            }
            if (response.authenticationStatus == AuthenticationStatus.SKIP_COMPLETE) {
                return ProceedTransition.TerminalTransition(response.errors ?: emptyList())
            }
            if (response.errors.isNotEmpty()) {
                return ProceedTransition.ErrorTransition(response.errors)
            }
            return null
        }

        fun handleKnownTransitions(response: AuthenticationResponse): ProceedTransition {
            handleTerminalTransitions(response)?.let { return it }

            return when (response.authenticationStatus) {
                AuthenticationStatus.AWAITING_PASSWORD_RESET -> {
                    registerPasswordForm(response, "Reset my Password")
                }
                AuthenticationStatus.PASSWORD_EXPIRED -> {
                    registerPasswordForm(response, "Password Expired")
                }
                AuthenticationStatus.AWAITING_AUTHENTICATOR_SELECTION -> {
                    selectAuthenticatorForm(response, "Select Authenticator")
                }
                AuthenticationStatus.AWAITING_AUTHENTICATOR_VERIFICATION -> {
                    verifyForm(response)
                }
                AuthenticationStatus.AWAITING_AUTHENTICATOR_ENROLLMENT_SELECTION -> {
                    selectAuthenticatorForm(response, "Enroll Authenticator")
                }
                AuthenticationStatus.AWAITING_AUTHENTICATOR_VERIFICATION_DATA -> {
                    // This is possible when email is not required and you're authenticating without verifying email.
                    if (response.authenticators.size == 1) {
                        selectFactorForm(response, response.authenticators.first().factors, "Select Factor")
                    } else {
                        unsupportedPolicy()
                    }
                }
                else -> unsupportedPolicy()
            }
        }

        private fun selectAuthenticatorForm(
            previousResponse: AuthenticationResponse,
            title: String,
        ): ProceedTransition {
            val canSkip =
                authenticationWrapper.isSkipAuthenticatorPresent(previousResponse.proceedContext)

            return ProceedTransition.FormTransition(
                SelectAuthenticatorForm(
                    SelectAuthenticatorForm.ViewModel(
                        previousResponse.authenticators,
                        canSkip,
                        title,
                    ),
                    formAction
                ),
                previousResponse.proceedContext
            )
        }

        fun selectFactorForm(
            response: AuthenticationResponse,
            factors: List<Authenticator.Factor>,
            title: String,
        ): ProceedTransition {
            val canSkip = authenticationWrapper.isSkipAuthenticatorPresent(response.proceedContext)
            return ProceedTransition.FormTransition(
                SelectFactorForm(
                    viewModel = SelectFactorForm.ViewModel(
                        factors = factors,
                        canSkip = canSkip,
                        title = title,
                    ),
                    formAction = formAction,
                ),
                response.proceedContext
            )
        }

        fun registerVerifyForm(
            response: AuthenticationResponse,
            factor: Authenticator.Factor,
        ): ProceedTransition {
            return when (factor.method) {
                "email" -> {
                    verifyForm(response)
                }
                "password" -> {
                    registerPasswordForm(response, "Setup Password")
                }
                "sms" -> {
                    ProceedTransition.FormTransition(
                        RegisterPhoneForm(
                            RegisterPhoneForm.ViewModel(
                                factor = factor
                            ),
                            formAction
                        ),
                        response.proceedContext
                    )
                }
                "voice" -> {
                    ProceedTransition.FormTransition(
                        RegisterPhoneForm(
                            RegisterPhoneForm.ViewModel(
                                factor = factor
                            ),
                            formAction
                        ),
                        response.proceedContext
                    )
                }
                else -> unsupportedPolicy()
            }
        }

        private fun registerPasswordForm(
            response: AuthenticationResponse,
            title: String
        ): ProceedTransition {
            return ProceedTransition.FormTransition(
                RegisterPasswordForm(
                    RegisterPasswordForm.ViewModel(
                        title = title,
                    ),
                    formAction
                ),
                response.proceedContext
            )
        }

        fun verifyForm(response: AuthenticationResponse): ProceedTransition {
            return ProceedTransition.FormTransition(
                VerifyCodeForm(formAction = formAction),
                response.proceedContext
            )
        }

        private fun unsupportedPolicy(): ProceedTransition {
            return ProceedTransition.TerminalTransition(listOf("Unsupported policy"))
        }

        suspend fun invokeTransitionFactory(transitionFactory: suspend ProceedData.() -> ProceedTransition): ProceedTransition {
            return transitionFactory()
        }
    }

    internal fun proceed(transitionFactory: suspend ProceedData.() -> ProceedTransition) {
        val initialState = stateLiveData.value as? State.Data ?: return

        stateLiveData.value = State.Loading

        val proceedData = ProceedData(authenticationWrapper, initialState.proceedContext, this)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                proceedData
                    .invokeTransitionFactory(transitionFactory)
                    .handle(initialState)
            } catch (e: Exception) {
                Timber.e(e, "An error occurred.")
                stateLiveData.postValue(initialState.copy(messages = messagesFromException(e)))
            }
        }
    }

    private fun transitionToForm(form: Form) {
        stateLiveData.value = State.Data(form = form)
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
                TestingGlobals.CURRENT_PROCEED_CONTEXT.set(proceedContext)
                stateLiveData.postValue(
                    State.Data(form = form, proceedContext = proceedContext)
                )
            }
            is ProceedTransition.TerminalTransition -> {
                stateLiveData.postValue(State.Data(initialForm(), messages = errors))
            }
            is ProceedTransition.ClearErrorsTransition -> {
                stateLiveData.postValue(initialState.copy(messages = emptyList()))
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
        transitionToForm(form = initialForm())
    }

    fun skip() {
        proceed {
            val response = authenticationWrapper.skipAuthenticatorEnrollment(proceedContext)
            handleKnownTransitions(response)
        }
    }

    fun proceedToUsernameAndPassword() {
        proceed {
            val response = authenticationWrapper.begin()
            handleTerminalTransitions(response)?.let { return@proceed it }
            ProceedTransition.FormTransition(
                form = UsernamePasswordForm(
                    formAction = this@FormAction,
                    viewModel = UsernamePasswordForm.ViewModel(
                        socialIdps = response.idps ?: emptyList()
                    )
                ),
                proceedContext = response.proceedContext
            )
        }
    }

    fun proceedToRegister() {
        proceed {
            val beginResponse = authenticationWrapper.begin()
            handleTerminalTransitions(beginResponse)?.let { return@proceed it }
            val newUserRegistrationResponse = authenticationWrapper.fetchSignUpFormValues(
                beginResponse.proceedContext
            )
            val attributeList = newUserRegistrationResponse.formValues?.getOrNull(0)?.form?.value?.map {
                RegisterForm.Attribute(it.name, it.label)
            } ?: emptyList()
            handleTerminalTransitions(newUserRegistrationResponse)?.let { return@proceed it }
            ProceedTransition.FormTransition(
                RegisterForm(
                    formAction = this@FormAction,
                    viewModel = RegisterForm.ViewModel(attributeList),
                ),
                proceedContext = newUserRegistrationResponse.proceedContext
            )
        }
    }

    private fun initialForm(): Form {
        return LaunchForm(formAction = this)
    }
}
