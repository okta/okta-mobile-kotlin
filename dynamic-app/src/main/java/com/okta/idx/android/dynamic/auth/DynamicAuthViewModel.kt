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
import com.okta.idx.android.dynamic.EmailRedirectCoordinator
import com.okta.idx.android.dynamic.SocialRedirectCoordinator
import com.okta.idx.kotlin.client.IdxRedirectResult
import com.okta.idx.kotlin.client.InteractionCodeFlow
import com.okta.idx.kotlin.client.InteractionCodeFlow.Companion.createInteractionCodeFlow
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

    @Volatile private var flow: InteractionCodeFlow? = null
    @Volatile private var pollingJob: Job? = null

    init {
        createClient()
        SocialRedirectCoordinator.listener = ::handleSocialRedirect
        EmailRedirectCoordinator.listener = ::handleEmailRedirect
    }

    override fun onCleared() {
        SocialRedirectCoordinator.listener = null
        EmailRedirectCoordinator.listener = null
    }

    private fun createClient() {
        _state.value = DynamicAuthState.Loading
        viewModelScope.launch {
            val extraRequestParameters = mutableMapOf<String, String>()
            if (recoveryToken.isNotEmpty()) {
                extraRequestParameters["recovery_token"] = recoveryToken
            }
            // Initiate the IDX client and start IDX flow.
            when (
                val clientResult = CredentialBootstrap.oidcClient.createInteractionCodeFlow(
                    redirectUrl = BuildConfig.REDIRECT_URI,
                    extraStartRequestParameters = extraRequestParameters,
                )
            ) {
                is OidcClientResult.Error -> {
                    Timber.e(clientResult.exception, "Failed to create client")
                    _state.value = DynamicAuthState.Error("Failed to create client")
                }
                is OidcClientResult.Success -> {
                    flow = clientResult.result
                    // Call the IDX API's resume method to receive the first IDX response.
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

    // Resume in case of an error.
    fun resume() {
        val localFlow = flow
        if (localFlow != null) {
            _state.value = DynamicAuthState.Loading
            viewModelScope.launch {
                when (val resumeResult = localFlow.resume()) {
                    is OidcClientResult.Error -> {
                        Timber.e(resumeResult.exception, "Failed to call resume")
                        _state.value = DynamicAuthState.Error("Failed to call resume with exception: ${resumeResult.exception}")
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

    private fun handleSocialRedirect(uri: Uri) {
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

    private fun handleEmailRedirect(uri: Uri, context: Context) {
        viewModelScope.launch {
            val idxForm = _state.value as DynamicAuthState.Form
            idxForm.idxResponse.remediations.firstOrNull {
                it.type == IdxRemediation.Type.CHALLENGE_AUTHENTICATOR
            }?.let {
                val otpCode = uri.getQueryParameter("otp")
                if (otpCode != null) {
                    it.form["credentials.passcode"]?.apply {
                        value = otpCode
                        proceed(it, context)
                    }
                }
            }
        }
    }

    private suspend fun handleResponse(response: IdxResponse) {
        // If a response is successful, immediately exchange it for a token and exit.
        if (response.isLoginSuccessful) {
            when (val result = flow?.exchangeInteractionCodeForTokens(response.remediations[IdxRemediation.Type.ISSUE]!!)) {
                is OidcClientResult.Success -> {
                    CredentialBootstrap.defaultCredential().storeToken(result.result)
                    cancelPolling()
                    _state.value = DynamicAuthState.Tokens
                }
                else -> {
                    _state.value = DynamicAuthState.Error("Failed to call resume")
                }
            }
            return
        }
        // Cancel current polling jobs so we can start new ones from current remediation.
        cancelPolling()
        // Obtain fields, actions and images from remediation and collect as DynamicAuthFields.
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
            // Start polling current remediation (if applicable). Required for asynchronous actions like using an email magic link to sign in.
            remediation.startPolling()
        }
        // If remediation didn't have a TOTP image check the authenticators for one.
        if (!hasAddedTotpImageField) {
            val field = response.authenticators.current?.asTotpImageDynamicAuthField()
            if (field != null) {
                fields.add(0, field)
            }
        }
        fields += response.recoverDynamicAuthFieldAction()
        fields += response.fatalErrorFieldAction()
        // Check for messages, such as entering an incorrect code or auth error.
        val messages = mutableListOf<String>()
        for (message in response.messages) {
            messages += message.message
        }
        _state.value = DynamicAuthState.Form(response, fields, messages)
    }

    /**
     * Get a label from `IdxRemediation.authenticators` when `IdxNumberChallengeCapability` is present
     */
    private fun IdxRemediation.asNumberChallengeField(): List<DynamicAuthField> {
        val capability = authenticators.capability<IdxNumberChallengeCapability>() ?: return emptyList()
        return listOf(DynamicAuthField.Label("Please select ${capability.correctAnswer}"))
    }

    /**
     * Get a bitmap image, like a QR Code, from `IdxRemediation.authenticators` when `IdxTotpCapability` is present.
     */
    private suspend fun IdxRemediation.asTotpImageDynamicAuthField(): List<DynamicAuthField> {
        val authenticator = authenticators.firstOrNull { it.capabilities.get<IdxTotpCapability>() != null } ?: return emptyList()
        val field = authenticator.asTotpImageDynamicAuthField() ?: return emptyList()
        return listOf(field)
    }

    /**
     * Get a bitmap image, like a QR Code,  from `IdxAuthenticator` when `IdxTotpCapability` is present.
     */
    private suspend fun IdxAuthenticator.asTotpImageDynamicAuthField(): DynamicAuthField? {
        val capability = capabilities.get<IdxTotpCapability>() ?: return null
        val bitmap = withContext(Dispatchers.Default) {
            capability.asImage()
        } ?: return null
        val label = displayName ?: "Launch Google Authenticator, tap the \"+\" icon, then select \"Scan a QR code\"."
        return DynamicAuthField.Image(label, bitmap, capability.sharedSecret)
    }

    /**
     * Get text fields, checkboxes, radio buttons and radio button groups from `IdxRemediation.form.visibleFields`.
     */
    private fun IdxRemediation.Form.Field.asDynamicAuthFields(): List<DynamicAuthField> {
        return when {
            // Nested form inside a field.
            !form?.visibleFields.isNullOrEmpty() -> {
                val result = mutableListOf<DynamicAuthField>()
                form?.visibleFields?.forEach {
                    result += it.asDynamicAuthFields()
                }
                result
            }
            // Options represent multiple choice items like authenticators and can be nested.
            !options.isNullOrEmpty() -> {
                options?.let { options ->
                    val transformed = options.map {
                        val fields =
                            it.form?.visibleFields?.flatMap { field -> field.asDynamicAuthFields() } ?: emptyList()
                        DynamicAuthField.Options.Option(it, it.label, fields)
                    }
                    val displayMessages = messages.joinToString(separator = "\n") { it.message }
                    listOf(
                        DynamicAuthField.Options(label, transformed, isRequired, displayMessages) {
                            selectedOption = it
                        }
                    )
                } ?: emptyList()
            }
            // Simple boolean field for checkbox.
            type == "boolean" -> {
                listOf(
                    DynamicAuthField.CheckBox(label ?: "") {
                        value = it
                    }
                )
            }
            // Simple text field.
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

    /**
     * Get a resend action from `IdxRemediation.authenticators` when `IdxResendCapability` is present.
     */
    private fun IdxRemediation.asDynamicAuthFieldResendAction(): List<DynamicAuthField> {
        val capability = authenticators.capability<IdxResendCapability>() ?: return emptyList()
        if (form.visibleFields.find { it.type != "string" } == null) {
            return emptyList() // There is no way to type in the code yet.
        }
        return listOf(
            DynamicAuthField.Action("Resend Code") { context ->
                proceed(capability.remediation, context)
            }
        )
    }

    /**
     * Get actions for `IdxRemediations` with visibleFields.
     */
    private fun IdxRemediation.asDynamicAuthFieldActions(): List<DynamicAuthField> {
        // Don't show action for actions that are pollable without visible fields.
        if (form.visibleFields.isEmpty() && capabilities.get<IdxPollRemediationCapability>() != null) {
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

        return listOf(
            DynamicAuthField.Action(title) { context ->
                proceed(this, context)
            }
        )
    }

    /**
     * Get a recover action from `IdxResponse.authenticators` when `IdxRecoverCapability` is present.
     */
    private fun IdxResponse.recoverDynamicAuthFieldAction(): List<DynamicAuthField> {
        val capability = authenticators.current?.capabilities?.get<IdxRecoverCapability>() ?: return emptyList()
        return listOf(
            DynamicAuthField.Action("Recover") { context ->
                proceed(capability.remediation, context)
            }
        )
    }

    /**
     * Create a 'Go to login' action for `IdxResponse` when remediations are empty.
     */
    private fun IdxResponse.fatalErrorFieldAction(): List<DynamicAuthField> {
        if (remediations.isNotEmpty()) {
            return emptyList()
        }
        return listOf(
            DynamicAuthField.Action("Go to login") {
                createClient()
            }
        )
    }

    /**
     * Start polling on a remediation (if applicable) for asynchronous actions like clicking on an email magic link or okta verify.
     */
    private fun IdxRemediation.startPolling() {
        val localFlow = flow ?: return

        val remediationCapability = capabilities.get<IdxPollRemediationCapability>()
        val authenticatorCapability = authenticators.capability<IdxPollAuthenticatorCapability>()

        // Create a poll function for the available capability.
        val pollFunction = when {
            remediationCapability != null -> remediationCapability::poll
            authenticatorCapability != null -> authenticatorCapability::poll
            else -> return
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

    /**
     * Proceed to the next phase of the remediation. If the remediation has an `IdxIdpCapability`,
     * it'll redirect to a browser showing the identity provider, otherwise it calls the Authorization Server with the given `remediation`.
     */
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

    /**
     * Proceed to the next step in the IDX flow using the specified remediation.
     */
    private fun proceed(remediation: IdxRemediation) {
        cancelPolling()
        viewModelScope.launch {
            _state.value = DynamicAuthState.Loading
            when (val resumeResult = flow?.proceed(remediation)) {
                is OidcClientResult.Success -> {
                    handleResponse(resumeResult.result)
                }
                else -> {
                    _state.value = DynamicAuthState.Error("Failed to call proceed")
                }
            }
        }
    }
}

private inline fun <reified Capability : IdxAuthenticator.Capability> IdxAuthenticatorCollection.capability(): Capability? {
    val authenticator = firstOrNull { it.capabilities.get<Capability>() != null } ?: return null
    return authenticator.capabilities.get()
}
