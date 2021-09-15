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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.idx.android.dynamic.SocialRedirectCoordinator
import com.okta.idx.kotlin.client.IdxClient
import com.okta.idx.kotlin.client.IdxClientResult
import com.okta.idx.kotlin.dto.IdxIdpTrait
import com.okta.idx.kotlin.dto.IdxRecoverTrait
import com.okta.idx.kotlin.dto.IdxRedirectResult
import com.okta.idx.kotlin.dto.IdxRemediation
import com.okta.idx.kotlin.dto.IdxResponse
import kotlinx.coroutines.launch
import timber.log.Timber

internal class DynamicAuthViewModel : ViewModel() {
    private val _state = MutableLiveData<DynamicAuthState>(DynamicAuthState.Loading)
    val state: LiveData<DynamicAuthState> = _state

    @Volatile private var client: IdxClient? = null

    init {
        createClient()
        SocialRedirectCoordinator.listener = ::handleRedirect
    }

    override fun onCleared() {
        SocialRedirectCoordinator.listener = null
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
                            handleResponse(resumeResult.response)
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
                        handleResponse(resumeResult.response)
                    }
                }
            }
        } else {
            createClient()
        }
    }

    fun handleRedirect(uri: Uri) {
        viewModelScope.launch {
            when (val redirectResult = client?.redirectResult(uri)) {
                is IdxRedirectResult.Error -> {
                    Timber.e(redirectResult.exception, redirectResult.errorMessage)
                    _state.value = DynamicAuthState.Error(redirectResult.errorMessage)
                }
                is IdxRedirectResult.InteractionRequired -> {
                    handleResponse(redirectResult.response)
                }
                is IdxRedirectResult.Tokens -> {
                    _state.value = DynamicAuthState.Tokens(redirectResult.response)
                }
                null -> {
                    Timber.d("No client for handleRedirect.")
                }
            }
        }
    }

    private suspend fun handleResponse(response: IdxResponse) {
        if (response.isLoginSuccessful) {
            when (val exchangeCodesResult = client?.exchangeCodes(response.remediations[IdxRemediation.Type.ISSUE]!!)) {
                is IdxClientResult.Error -> {
                    _state.value = DynamicAuthState.Error("Failed to call resume")
                }
                is IdxClientResult.Response -> {
                    _state.value = DynamicAuthState.Tokens(exchangeCodesResult.response)
                }
            }
            return
        }
        val fields = mutableListOf<DynamicAuthField>()
        for (remediation in response.remediations) {
            for (visibleField in remediation.form.visibleFields) {
                fields += visibleField.asDynamicAuthFields()
            }
            fields += remediation.asDynamicAuthFieldActions()
        }
        fields += response.recoverDynamicAuthFieldAction()
        val messages = mutableListOf<String>()
        for (message in response.messages) {
            messages += message.message
        }
        _state.value = DynamicAuthState.Form(response, fields, messages)
    }

    private fun IdxRemediation.Form.Field.asDynamicAuthFields(): List<DynamicAuthField> {
        return when (true) {
            form?.visibleFields?.isNullOrEmpty() == false -> {
                val result = mutableListOf<DynamicAuthField>()
                form?.visibleFields?.forEach {
                    result += it.asDynamicAuthFields()
                }
                result
            }
            options?.isNullOrEmpty() == false -> {
                val result = mutableListOf<DynamicAuthField>()
                options?.let { options ->
                    result += DynamicAuthField.Options(options) {
                        selectedOption = it
                    }
                }

                result
            }
            type == "boolean" -> {
                listOf(DynamicAuthField.CheckBox(label ?: "") {
                    value = it
                })
            }
            type == "string" -> {
                listOf(DynamicAuthField.Text(label ?: "", isRequired, isSecret) {
                    value = it
                })
            }
            else -> {
                Timber.d("Unknown field type: %s", this)
                emptyList()
            }
        }
    }

    private fun IdxRemediation.asDynamicAuthFieldActions(): List<DynamicAuthField> {
        val title = when (type) {
            IdxRemediation.Type.SKIP -> "Skip"
            IdxRemediation.Type.ENROLL_PROFILE, IdxRemediation.Type.SELECT_ENROLL_PROFILE -> "Sign Up"
            IdxRemediation.Type.SELECT_IDENTIFY, IdxRemediation.Type.IDENTIFY -> "Sign In"
            IdxRemediation.Type.SELECT_AUTHENTICATOR_AUTHENTICATE -> "Choose"
            IdxRemediation.Type.LAUNCH_AUTHENTICATOR -> "Launch Authenticator"
            IdxRemediation.Type.CANCEL -> "Restart"
            IdxRemediation.Type.REDIRECT_IDP -> {
                traits.get<IdxIdpTrait>()?.let { trait ->
                    "Login with ${trait.name}"
                } ?: "Social Login"
            }
            else -> "Continue"
        }
        return listOf(DynamicAuthField.Action(title) { context ->
            proceed(this, context)
        })
    }

    private fun IdxResponse.recoverDynamicAuthFieldAction(): List<DynamicAuthField> {
        val recoverTrait = authenticators.current?.traits?.get<IdxRecoverTrait>() ?: return emptyList()
        return listOf(DynamicAuthField.Action("Recover") { context ->
            proceed(recoverTrait.remediation, context)
        })
    }

    private fun proceed(remediation: IdxRemediation, context: Context) {
        val idpTrait = remediation.traits.get<IdxIdpTrait>()
        if (idpTrait != null) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(idpTrait.redirectUrl.toString()))
                context.startActivity(browserIntent)
            } catch (e: ActivityNotFoundException) {
                Timber.e(e, "Failed to load URL.")
            }
            return
        }
        viewModelScope.launch {
            when (val resumeResult = client?.proceed(remediation)) {
                is IdxClientResult.Error -> {
                    _state.value = DynamicAuthState.Error("Failed to call proceed")
                }
                is IdxClientResult.Response -> {
                    handleResponse(resumeResult.response)
                }
            }
        }
    }
}
