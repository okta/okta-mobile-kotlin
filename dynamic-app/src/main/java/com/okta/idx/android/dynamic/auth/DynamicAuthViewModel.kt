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
package com.okta.idx.android.dynamic.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.idx.kotlin.client.IdxClient
import com.okta.idx.kotlin.client.IdxClientResult
import com.okta.idx.kotlin.dto.IdxRemediation
import com.okta.idx.kotlin.dto.IdxResponse
import kotlinx.coroutines.launch

internal class DynamicAuthViewModel : ViewModel() {
    private val _state = MutableLiveData<DynamicAuthState>(DynamicAuthState.Loading)
    val state: LiveData<DynamicAuthState> = _state

    @Volatile private var client: IdxClient? = null

    init {
        createClient()
    }

    private fun createClient() {
        _state.value = DynamicAuthState.Loading
        viewModelScope.launch {
            when (val clientResult = IdxClient.start(IdxClientConfigurationProvider.get())) {
                is IdxClientResult.Error -> {
                    _state.value = DynamicAuthState.Error("Failed to create client")
                }
                is IdxClientResult.Response -> {
                    client = clientResult.response
                    when (val resumeResult = clientResult.response.resume()) {
                        is IdxClientResult.Error -> {
                            _state.value = DynamicAuthState.Error("Failed to call resume")
                        }
                        is IdxClientResult.Response -> {
                            _state.value = createForm(resumeResult.response)
                        }
                    }
                }
            }
        }
    }

    fun resume() {
        val localClient = client
        if (localClient != null) {
            _state.value = DynamicAuthState.Loading
            viewModelScope.launch {
                when (val resumeResult = localClient.resume()) {
                    is IdxClientResult.Error -> {
                        _state.value = DynamicAuthState.Error("Failed to call resume")
                    }
                    is IdxClientResult.Response -> {
                        _state.value = createForm(resumeResult.response)
                    }
                }
            }
        } else {
            createClient()
        }
    }

    private fun createForm(response: IdxResponse): DynamicAuthState.Form {
        val fields = mutableListOf<DynamicAuthField>()
        for (remediation in response.remediations) {
            for (visibleField in remediation.form.visibleFields) {
                fields += visibleField.asDynamicAuthFields()
            }
            fields += remediation.asDynamicAuthFieldActions()
        }
        val messages = mutableListOf<String>()
        for (message in response.messages) {
            messages += message.message
        }
        return DynamicAuthState.Form(response, fields, messages)
    }

    private fun IdxRemediation.Form.Field.asDynamicAuthFields(): List<DynamicAuthField> {
        return when (type) {
            "boolean" -> {
                listOf(DynamicAuthField.CheckBox(label ?: "") {
                    value = it
                })
            }
            "object" -> {
                val result = mutableListOf<DynamicAuthField>()
                form?.visibleFields?.forEach {
                    result += it.asDynamicAuthFields()
                }
                result
            }
            else -> {
                listOf(DynamicAuthField.Text(label ?: "", isRequired, isSecret) {
                    value = it
                })
            }
        }
    }

    private fun IdxRemediation.asDynamicAuthFieldActions(): List<DynamicAuthField> {
        val title = when (type) {
            IdxRemediation.Type.SKIP -> "Skip"
            IdxRemediation.Type.ENROLL_PROFILE, IdxRemediation.Type.SELECT_ENROLL_PROFILE -> "Sign Up"
            IdxRemediation.Type.SELECT_IDENTIFY, IdxRemediation.Type.IDENTIFY -> "Sign In"
            IdxRemediation.Type.REDIRECT_IDP -> "Social Login" // TODO:
            IdxRemediation.Type.SELECT_AUTHENTICATOR_AUTHENTICATE -> "Choose"
            IdxRemediation.Type.LAUNCH_AUTHENTICATOR -> "Launch Authenticator"
            IdxRemediation.Type.CANCEL -> "Restart"
            else -> "Continue"
        }
        return listOf(DynamicAuthField.Action(title) {
            val remediation = this
            viewModelScope.launch {
                when (val resumeResult = client?.proceed(remediation)) {
                    is IdxClientResult.Error -> {
                        _state.value = DynamicAuthState.Error("Failed to call proceed")
                    }
                    is IdxClientResult.Response -> {
                        _state.value = createForm(resumeResult.response)
                    }
                }
            }
        })
    }
}
