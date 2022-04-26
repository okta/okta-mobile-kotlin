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
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundationbootstrap.CredentialBootstrap
import com.okta.idx.android.dynamic.BuildConfig
import com.okta.idx.android.dynamic.SocialRedirectCoordinator
import com.okta.idx.kotlin.client.IdxFlow
import com.okta.idx.kotlin.client.IdxFlow.Companion.createIdxFlow
import com.okta.idx.kotlin.client.IdxRedirectResult
import com.okta.idx.kotlin.dto.IdxAuthenticator
import com.okta.idx.kotlin.dto.IdxAuthenticatorCollection
import com.okta.idx.kotlin.dto.IdxIdpCapability
import com.okta.idx.kotlin.dto.IdxNumberChallengeCapability
import com.okta.idx.kotlin.dto.IdxPollAuthenticatorCapability
import com.okta.idx.kotlin.dto.IdxPollRemediationCapability
import com.okta.idx.kotlin.dto.IdxRecoverCapability
import com.okta.idx.kotlin.dto.IdxRemediation
import com.okta.idx.kotlin.dto.IdxResendCapability
import com.okta.idx.kotlin.dto.IdxResponse
import com.okta.idx.kotlin.dto.IdxTotpCapability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class DynamicAuthViewModel(private val recoveryToken: String) : ViewModel() {
    private val _state = MutableLiveData<DynamicAuthState>(DynamicAuthState.Loading)
    val state: LiveData<DynamicAuthState> = _state

    @Volatile private var flow: IdxFlow? = null
    @Volatile private var pollingJob: Job? = null

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
            val extraRequestParameters = mutableMapOf<String, String>()
            if (recoveryToken.isNotEmpty()) {
                extraRequestParameters["recovery_token"] = recoveryToken
            }
            when (val clientResult = CredentialBootstrap.oidcClient.createIdxFlow(
                redirectUrl = BuildConfig.REDIRECT_URI,
                extraStartRequestParameters = extraRequestParameters,
            )) {
                is OidcClientResult.Error -> {
                    _state.value = DynamicAuthState.Error("Failed to create client")
                }
                is OidcClientResult.Success -> {
                    flow = clientResult.result
                    when (val resumeResult = clientResult.result.resume()) {
                        is OidcClientResult.Error -> {
                            _state.value = DynamicAuthState.Error("Failed to call resume")
                        }
                        is OidcClientResult.Success -> {
                            handleResponse(resumeResult.result)
                        }
                    }
                }
            }
        }
    }

    fun resume() {
        val localFlow = flow
        if (localFlow != null) {
            _state.value = DynamicAuthState.Loading
            viewModelScope.launch {
                when (val resumeResult = localFlow.resume()) {
                    is OidcClientResult.Error -> {
                        _state.value = DynamicAuthState.Error("Failed to call resume")
                    }
                    is OidcClientResult.Success -> {
                        handleResponse(resumeResult.result)
                    }
                }
            }
        } else {
            createClient()
        }
    }

    fun handleRedirect(uri: Uri) {
        viewModelScope.launch {
            when (val redirectResult = flow?.evaluateRedirectUri(uri)) {
                is IdxRedirectResult.Error -> {
                    Timber.e(redirectResult.exception, redirectResult.errorMessage)
                    _state.value = DynamicAuthState.Error(redirectResult.errorMessage)
                }
                is IdxRedirectResult.InteractionRequired -> {
                    handleResponse(redirectResult.response)
                }
                is IdxRedirectResult.Tokens -> {
                    CredentialBootstrap.defaultCredential().storeToken(redirectResult.response)
                    _state.value = DynamicAuthState.Tokens
                }
                null -> {
                    Timber.d("No client for handleRedirect.")
                }
            }
        }
    }

    private suspend fun handleResponse(response: IdxResponse) {
        if (response.isLoginSuccessful) {
            when (val result = flow?.exchangeInteractionCodeForTokens(response.remediations[IdxRemediation.Type.ISSUE]!!)) {
                is OidcClientResult.Error -> {
                    _state.value = DynamicAuthState.Error("Failed to call resume")
                }
                is OidcClientResult.Success -> {
                    CredentialBootstrap.defaultCredential().storeToken(result.result)
                    _state.value = DynamicAuthState.Tokens
                }
            }
            return
        }
        cancelPolling()
        var hasAddedTotpImageField = false
        val fields = mutableListOf<DynamicAuthField>()
        for (remediation in response.remediations) {
            fields += remediation.asTotpImageDynamicAuthField().also {
                if (it.isNotEmpty()) {
                    hasAddedTotpImageField = true
                }
            }
            fields += remediation.asNumberChallengeField()
            for (visibleField in remediation.form.visibleFields) {
                fields += visibleField.asDynamicAuthFields()
            }
            fields += remediation.asDynamicAuthFieldResendAction()
            fields += remediation.asDynamicAuthFieldActions()
            remediation.startPolling()
        }
        if (!hasAddedTotpImageField) {
            val field = response.authenticators.current?.asTotpImageDynamicAuthField()
            if (field != null) {
                fields.add(0, field)
            }
        }
        fields += response.recoverDynamicAuthFieldAction()
        fields += response.fatalErrorFieldAction()
        val messages = mutableListOf<String>()
        for (message in response.messages) {
            messages += message.message
        }
        _state.value = DynamicAuthState.Form(response, fields, messages)
    }

    private fun IdxRemediation.asNumberChallengeField(): List<DynamicAuthField> {
        val capability = authenticators.capability<IdxNumberChallengeCapability>() ?: return emptyList()
        return listOf(DynamicAuthField.Label("Please select ${capability.correctAnswer}"))
    }

    private suspend fun IdxRemediation.asTotpImageDynamicAuthField(): List<DynamicAuthField> {
        val authenticator = authenticators.firstOrNull { it.capabilities.get<IdxTotpCapability>() != null } ?: return emptyList()
        val field = authenticator.asTotpImageDynamicAuthField() ?: return emptyList()
        return listOf(field)
    }

    private suspend fun IdxAuthenticator.asTotpImageDynamicAuthField(): DynamicAuthField? {
        val capability = capabilities.get<IdxTotpCapability>() ?: return null
        val bitmap = withContext(Dispatchers.Default) {
            capability.asImage()
        } ?: return null
        val label = displayName ?: "Launch Google Authenticator, tap the \"+\" icon, then select \"Scan a QR code\"."
        return DynamicAuthField.Image(label, bitmap, capability.sharedSecret)
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
                options?.let { options ->
                    val transformed = options.map {
                        val fields =
                            it.form?.visibleFields?.flatMap { field -> field.asDynamicAuthFields() } ?: emptyList()
                        DynamicAuthField.Options.Option(it, it.label, fields)
                    }
                    val displayMessages = messages.joinToString(separator = "\n") { it.message }
                    listOf(DynamicAuthField.Options(label, transformed, isRequired, displayMessages) {
                        selectedOption = it
                    })
                } ?: emptyList()
            }
            type == "boolean" -> {
                listOf(DynamicAuthField.CheckBox(label ?: "") {
                    value = it
                })
            }
            type == "string" -> {
                val displayMessages = messages.joinToString(separator = "\n") { it.message }
                val field = DynamicAuthField.Text(label ?: "", isRequired, isSecret, displayMessages) {
                    value = it
                }
                (value as? String?)?.let {
                    field.value = it
                }
                listOf(field)
            }
            else -> {
                Timber.d("Unknown field type: %s", this)
                emptyList()
            }
        }
    }

    private fun IdxRemediation.asDynamicAuthFieldResendAction(): List<DynamicAuthField> {
        val capability = authenticators.capability<IdxResendCapability>() ?: return emptyList()
        if (form.visibleFields.find { it.type != "string" } == null) {
            return emptyList() // There is no way to type in the code yet.
        }
        return listOf(DynamicAuthField.Action("Resend Code") { context ->
            proceed(capability.remediation, context)
        })
    }

    private fun IdxRemediation.asDynamicAuthFieldActions(): List<DynamicAuthField> {
        // Don't show action for actions that are pollable without visible fields.
        if (form.visibleFields.count() == 0 && capabilities.get<IdxPollRemediationCapability>() != null) {
            return emptyList()
        }

        val title = when (type) {
            IdxRemediation.Type.SKIP -> "Skip"
            IdxRemediation.Type.ENROLL_PROFILE, IdxRemediation.Type.SELECT_ENROLL_PROFILE -> "Sign Up"
            IdxRemediation.Type.SELECT_IDENTIFY, IdxRemediation.Type.IDENTIFY -> "Sign In"
            IdxRemediation.Type.SELECT_AUTHENTICATOR_AUTHENTICATE, IdxRemediation.Type.SELECT_AUTHENTICATOR_ENROLL -> "Choose"
            IdxRemediation.Type.LAUNCH_AUTHENTICATOR -> "Launch Authenticator"
            IdxRemediation.Type.CANCEL -> "Restart"
            IdxRemediation.Type.UNLOCK_ACCOUNT -> "Unlock Account"
            IdxRemediation.Type.REDIRECT_IDP -> {
                capabilities.get<IdxIdpCapability>()?.let { capability ->
                    "Login with ${capability.name}"
                } ?: "Social Login"
            }
            else -> "Continue"
        }

        return listOf(DynamicAuthField.Action(title) { context ->
            proceed(this, context)
        })
    }

    private fun IdxResponse.recoverDynamicAuthFieldAction(): List<DynamicAuthField> {
        val capability = authenticators.current?.capabilities?.get<IdxRecoverCapability>() ?: return emptyList()
        return listOf(DynamicAuthField.Action("Recover") { context ->
            proceed(capability.remediation, context)
        })
    }

    private fun IdxResponse.fatalErrorFieldAction(): List<DynamicAuthField> {
        if (remediations.isNotEmpty()) {
            return emptyList()
        }
        return listOf(DynamicAuthField.Action("Go to login") {
            createClient()
        })
    }

    private fun IdxRemediation.startPolling() {
        val localFlow = flow ?: return

        val pollFunction: suspend (IdxFlow) -> OidcClientResult<IdxResponse>

        val remediationCapability = capabilities.get<IdxPollRemediationCapability>()
        val authenticatorCapability = authenticators.capability<IdxPollAuthenticatorCapability>()

        when {
            remediationCapability != null -> {
                pollFunction = remediationCapability::poll
            }
            authenticatorCapability != null -> {
                pollFunction = authenticatorCapability::poll
            }
            else -> {
                return
            }
        }

        pollingJob = viewModelScope.launch {
            when (val result = pollFunction(localFlow)) {
                is OidcClientResult.Error -> {
                    _state.value = DynamicAuthState.Error("Failed to poll")
                }
                is OidcClientResult.Success -> {
                    handleResponse(result.result)
                }
            }
        }
    }

    private fun cancelPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun proceed(remediation: IdxRemediation, context: Context) {
        val idpCapability = remediation.capabilities.get<IdxIdpCapability>()
        if (idpCapability != null) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(idpCapability.redirectUrl.toString()))
                context.startActivity(browserIntent)
            } catch (e: ActivityNotFoundException) {
                Timber.e(e, "Failed to load URL.")
            }
            return
        }
        proceed(remediation)
    }

    private fun proceed(remediation: IdxRemediation) {
        cancelPolling()
        viewModelScope.launch {
            _state.value = DynamicAuthState.Loading
            when (val resumeResult = flow?.proceed(remediation)) {
                is OidcClientResult.Error -> {
                    _state.value = DynamicAuthState.Error("Failed to call proceed")
                }
                is OidcClientResult.Success -> {
                    handleResponse(resumeResult.result)
                }
            }
        }
    }
}

private inline fun <reified Capability : IdxAuthenticator.Capability> IdxAuthenticatorCollection.capability(): Capability? {
    val authenticator = firstOrNull { it.capabilities.get<Capability>() != null } ?: return null
    return authenticator.capabilities.get()
}
