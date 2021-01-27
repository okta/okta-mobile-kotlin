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
package com.okta.idx.android.dynamic

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.idx.android.network.Network
import com.okta.idx.android.sdk.DisplayableStep
import com.okta.idx.android.sdk.IdxViewRegistry
import com.okta.idx.android.sdk.StepState
import com.okta.idx.sdk.api.exception.ProcessingException
import com.okta.idx.sdk.api.model.MessageValue
import com.okta.idx.sdk.api.response.IDXResponse
import com.okta.idx.sdk.api.response.TokenResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Optional

internal class DynamicViewModel : ViewModel() {
    private val _stateLiveData = MutableLiveData<State>(State.Loading)
    val stateLiveData: LiveData<State> = _stateLiveData

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val idxClient = Network.idxClient()
                val interactHandle = idxClient.interact().interactionHandle
                val response: IDXResponse = idxClient.introspect(Optional.of(interactHandle))
                val stateHandle = response.stateHandle
                // TODO: Handle multiple steps.
                _stateLiveData.postValue(
                    State.Form(
                        IdxViewRegistry.asDisplaySteps(response)[0],
                        StepState(idxClient, stateHandle)
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "An error occurred.")
                TODO()
            }
        }
    }

    fun signIn(form: State.Form) {
        callIdxClient(form) {
            form.displayableStep.proceed(form.stepState)
        }
    }

    fun cancel(form: State.Form) {
        callIdxClient(form) {
            form.stepState.cancel()
        }
    }

    private fun callIdxClient(form: State.Form, responseFactory: () -> IDXResponse) {
        _stateLiveData.value = State.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = responseFactory()

                if (response.isLoginSuccessful) {
                    _stateLiveData.postValue(State.Success(form.stepState.token(response)))
                } else {
                    // TODO: Handle multiple steps.
                    _stateLiveData.postValue(
                        State.Form(
                            IdxViewRegistry.asDisplaySteps(response)[0],
                            form.stepState
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "An error occurred.")

                when (e) {
                    is ProcessingException -> {
                        val messages = e.errorResponse.messages
                        _stateLiveData.postValue(
                            form.copy(
                                messages = messages?.value?.map {
                                    it.asFormMessage()
                                } ?: listOf(State.Form.Message.Error("An error occurred."))
                            )
                        )
                    }
                    else -> {
                        _stateLiveData.postValue(
                            form.copy(
                                messages = listOf(State.Form.Message.Error("An error occurred."))
                            )
                        )
                    }
                }
            }
        }
    }

    private fun MessageValue.asFormMessage(): State.Form.Message {
        return when (value) {
            "ERROR" -> {
                State.Form.Message.Error(message)
            }
            "INFO" -> {
                State.Form.Message.Info(message)
            }
            else -> {
                throw IllegalStateException("Unknown MessageValue value: $value")
            }
        }
    }

    internal sealed class State {
        data class Form(
            val displayableStep: DisplayableStep<*>,
            val stepState: StepState,
            val messages: List<Message> = emptyList(),
        ) : State() {
            sealed class Message {
                abstract val message: String
                abstract val textColor: Int

                data class Error(override val message: String) : Message() {
                    override val textColor: Int = android.graphics.Color.RED
                }

                data class Info(override val message: String) : Message() {
                    override val textColor: Int = android.graphics.Color.BLACK
                }
            }
        }

        object Loading : State()
        class Success(val tokenResponse: TokenResponse) : State()
    }
}
