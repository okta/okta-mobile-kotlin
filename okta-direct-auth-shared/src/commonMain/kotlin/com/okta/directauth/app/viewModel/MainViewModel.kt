/*
 * Copyright 2022-Present Okta, Inc.
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
package com.okta.directauth.app.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.authfoundation.ChallengeGrantType
import com.okta.authfoundation.api.http.KtorHttpExecutor
import com.okta.directauth.DirectAuthenticationFlowBuilder
import com.okta.directauth.api.DirectAuthenticationFlow
import com.okta.directauth.app.AppConfig
import com.okta.directauth.app.http.MyAccountReplacePasswordRequest
import com.okta.directauth.app.model.AuthMethod
import com.okta.directauth.app.model.OktaErrorResponse
import com.okta.directauth.app.util.AppLogger
import com.okta.directauth.model.DirectAuthContinuation
import com.okta.directauth.model.DirectAuthenticationIntent
import com.okta.directauth.model.DirectAuthenticationState
import com.okta.directauth.model.PrimaryFactor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Main ViewModel that manages the Direct Authentication flow.
 *
 * This ViewModel handles all authentication-related operations including:
 * - Starting authentication with username and primary factor (password, OTP, etc.)
 * - Resuming authentication with MFA when required
 * - Handling continuation states (OOB polling, prompts, transfers)
 * - Resetting the authentication flow
 *
 * The ViewModel maintains a reference to the DirectAuthenticationFlow SDK instance
 * and exposes methods that UI screens can call to progress through the auth flow.
 */
class MainViewModel : ViewModel() {
    companion object {
        private const val TAG = "MainViewModel"
    }

    private val scopes = listOf("openid", "profile", "email")

    private val ssprScope = listOf("okta.myAccount.password.manage")

    private val _authIntent = MutableStateFlow(DirectAuthenticationIntent.SIGN_IN)

    private val apiExecutor = KtorHttpExecutor()

    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }

    /**
     * The intent of the authentication.
     *
     * This is used to differentiate which token is returned based on the intent of the user action. If it is for password recovery
     * then a token with the password.manage scope is returned for the application to use for password recovery.
     */
    val authIntent = _authIntent.asStateFlow()

    private val _passwordChangeResult = MutableStateFlow<PasswordChangeResult>(PasswordChangeResult.Idle)

    /**
     * The result of the password change operation.
     *
     * This StateFlow emits the result of password change attempts:
     * - Idle: No password change operation in progress
     * - Success: Password was changed successfully
     * - Error: Password change failed with an error message
     */
    val passwordChangeResult = _passwordChangeResult.asStateFlow()

    /**
     * Sealed class representing the result of a password change operation.
     */
    sealed class PasswordChangeResult {
        object Idle : PasswordChangeResult()

        object Success : PasswordChangeResult()

        sealed class Error : PasswordChangeResult() {
            data class OktaApiError(
                val oktaErrorResponse: OktaErrorResponse,
                val statusCode: Int,
            ) : Error()

            data class NetworkError(
                val message: String,
            ) : Error()
        }
    }

    /**
     * The Direct Authentication SDK flow instance to sign in.
     *
     * This provides access to:
     * - authenticationState: StateFlow of the current authentication state
     * - start(): Initiates authentication with a username and factor
     * - reset(): Resets the authentication flow to idle state
     */
    val directAuth: DirectAuthenticationFlow =
        DirectAuthenticationFlowBuilder
            .create(AppConfig.ISSUER, AppConfig.CLIENT_ID, scopes) {
                authorizationServerId = AppConfig.AUTHORIZATION_SERVER_ID
            }.getOrThrow()

    /**
     * The Direct Authentication SDK flow instance to use with myAccount API for password recovery.
     *
     * This provides access to:
     * - authenticationState: StateFlow of the current authentication state
     * - start(): Initiates authentication with a username and factor
     * - reset(): Resets the authentication flow to idle state
     */
    val directAuthSspr: DirectAuthenticationFlow =
        DirectAuthenticationFlowBuilder
            .create(AppConfig.ISSUER, AppConfig.CLIENT_ID, ssprScope) {
                directAuthenticationIntent = DirectAuthenticationIntent.RECOVERY
            }.getOrThrow()

    /**
     * Initiates authentication with a username and authentication method.
     *
     * This is the primary entry point for authentication. Called when:
     * - User enters password on PasswordScreen
     * - User submits OTP code on CodeEntryScreen
     * - User initiates Okta Verify push on OktaVerifyScreen
     * - User initiates SMS/Voice authentication
     *
     * The method converts the AuthMethod to a PrimaryFactor and starts the authentication flow.
     * The flow will emit state updates through directAuth.authenticationState.
     *
     * @param username The username to authenticate
     * @param password The password or code (empty string for push-based auth)
     * @param authMethod The authentication method (Password, OTP, SMS, Voice, OktaVerify, Passkeys)
     * @return Job that can be cancelled if the user navigates away
     */
    fun signIn(
        username: String,
        password: String,
        authMethod: AuthMethod,
    ): Job {
        AppLogger.write(TAG, "signIn() called - username: $username, authMethod: ${authMethod.label}")
        return viewModelScope.launch {
            AppLogger.write(TAG, "Starting authentication with ${authMethod.label} and intent: ${authIntent.value}")
            when (authIntent.value) {
                DirectAuthenticationIntent.SIGN_IN -> directAuth.start(username, authMethod.asFactor(password))
                DirectAuthenticationIntent.RECOVERY -> directAuthSspr.start(username, authMethod.asFactor(password))
            }
        }
    }

    /**
     * Resumes authentication with an MFA (Multi-Factor Authentication) method.
     *
     * Called when the user needs to provide a second factor after successful primary authentication.
     * This happens when:
     * - Password authentication succeeds but MFA is required by policy
     * - User previously selected an initial auth method and now needs to verify with MFA
     *
     * The method:
     * 1. Converts the MFA method to a SecondaryFactor
     * 2. Determines the appropriate challenge grant type (OobMfa, OtpMfa, WebAuthnMfa)
     * 3. Resumes the MfaRequired state with the second factor
     *
     * @param otp The verification code (for OTP/SMS/Voice) or empty string (for push)
     * @param mfaMethod The MFA method to use (Otp, SMS, Voice, OktaVerify, Passkeys)
     * @param mfaRequired The MfaRequired state that needs to be resumed
     * @return Job that can be cancelled if the user navigates away
     */
    fun resume(
        otp: String,
        mfaMethod: AuthMethod.Mfa,
        mfaRequired: DirectAuthenticationState.MfaRequired,
    ): Job {
        AppLogger.write(TAG, "resume() called - mfaMethod: ${mfaMethod.label}")
        val mfaFactor = mfaMethod.asFactor(otp)
        val challengeGrantType =
            when (mfaFactor) {
                is PrimaryFactor.Oob -> ChallengeGrantType.OobMfa
                is PrimaryFactor.Otp -> ChallengeGrantType.OtpMfa
                PrimaryFactor.WebAuthn -> ChallengeGrantType.WebAuthnMfa
            }
        AppLogger.write(TAG, "Resuming MFA with ${mfaMethod.label}, challengeGrantType: $challengeGrantType")
        return viewModelScope.launch { mfaRequired.resume(mfaFactor, listOf(challengeGrantType)) }
    }

    /**
     * Polls for Out-of-Band (OOB) authentication completion.
     *
     * Called periodically when waiting for the user to:
     * - Approve a push notification in Okta Verify app
     * - Click a link sent via SMS
     * - Answer a voice call
     *
     * The method calls proceed() on the OobPending continuation, which polls the server
     * to check if the user has completed the OOB challenge on their device.
     *
     * @param oobPending The OobPending continuation state to poll
     * @return Job representing the polling operation
     */
    fun pendingOob(oobPending: DirectAuthContinuation.OobPending): Job {
        AppLogger.write(TAG, "pendingOob() called - polling for OOB completion (expires in ${oobPending.expirationInSeconds}s)")
        return viewModelScope.launch {
            AppLogger.write(TAG, "Proceeding with OOB pending state")
            oobPending.proceed()
        }
    }

    /**
     * Submits a code in response to a server prompt during authentication.
     *
     * Called when the server requires additional input during the authentication flow.
     * This is a Continuation.Prompt state that occurs when the server needs more
     * information from the user (e.g., additional OTP code, recovery code).
     *
     * @param prompt The Prompt continuation state requiring input
     * @param code The code entered by the user
     * @return Job representing the submission operation
     */
    fun prompt(
        prompt: DirectAuthContinuation.Prompt,
        code: String,
    ): Job {
        AppLogger.write(TAG, "prompt() called - submitting code in response to server prompt")
        return viewModelScope.launch {
            AppLogger.write(TAG, "Proceeding with prompt continuation")
            prompt.proceed(code)
        }
    }

    /**
     * Proceeds with device transfer authentication.
     *
     * Called when authenticating via device transfer (e.g., Okta Verify with binding code).
     * The user verifies the binding code shown on this device with the code on their
     * authenticating device, then approves the request.
     *
     * This method initiates polling to wait for the user to complete the verification
     * on their other device.
     *
     * @param transfer The Transfer continuation state
     * @return Job representing the transfer polling operation
     */
    fun transfer(transfer: DirectAuthContinuation.Transfer): Job {
        AppLogger.write(TAG, "transfer() called - initiating device transfer with binding code: ${transfer.bindingCode}")
        return viewModelScope.launch {
            AppLogger.write(TAG, "Proceeding with transfer continuation (expires in ${transfer.expirationInSeconds}s)")
            transfer.proceed()
        }
    }

    /**
     * Resets the authentication flow to its initial idle state.
     *
     * Called when:
     * - User clicks "Back to sign in" to start over
     * - User signs out after successful authentication
     * - Authentication encounters an unrecoverable error
     * - User cancels an ongoing authentication operation
     *
     * After calling reset(), the app should navigate back to the username entry screen.
     */
    fun reset() {
        AppLogger.write(TAG, "reset() called - resetting authentication flow to idle state")
        _authIntent.value = DirectAuthenticationIntent.SIGN_IN
        _passwordChangeResult.value = PasswordChangeResult.Idle
        directAuth.reset()
        directAuthSspr.reset()
    }

    fun forgotPassword() {
        AppLogger.write(TAG, "forgotPassword() called")
        _authIntent.value = DirectAuthenticationIntent.RECOVERY
    }

    /**
     * Changes the user's password using the Okta myAccount API.
     *
     * This is used in the self-service password recovery (SSPR) flow after the user has
     * authenticated with the okta.myAccount.password.manage scope.
     *
     * @param accessToken The access token with okta.myAccount.password.manage scope
     * @param newPassword The new password to set for the user
     * @return Job representing the password change operation
     */
    fun changePassword(
        accessToken: String,
        newPassword: String,
    ): Job {
        AppLogger.write(TAG, "changePassword() called")
        _passwordChangeResult.value = PasswordChangeResult.Idle
        return viewModelScope.launch {
            AppLogger.write(TAG, "Executing password change request")
            try {
                val request =
                    MyAccountReplacePasswordRequest(
                        issuerUrl = AppConfig.ISSUER,
                        accessToken = accessToken,
                        newPassword = newPassword
                    )

                apiExecutor.execute(request).fold(
                    onSuccess = { response ->
                        if (response.statusCode == 200) {
                            AppLogger.write(TAG, "Password changed successfully")
                            _passwordChangeResult.value = PasswordChangeResult.Success
                        } else {
                            val oktaError =
                                runCatching {
                                    response.body?.takeIf { it.isNotEmpty() }?.let { byteArray ->
                                        val errorJsonString = byteArray.toString(Charsets.UTF_8)
                                        json.decodeFromString<OktaErrorResponse>(errorJsonString)
                                    }
                                }.getOrElse {
                                    AppLogger.write(TAG, "Failed to parse error response")
                                    it.printStackTrace()
                                    return@fold
                                }

                            if (oktaError != null) {
                                AppLogger.write(TAG, "Password change failed (status: ${response.statusCode}): ${oktaError.errorSummary}")
                                _passwordChangeResult.value = PasswordChangeResult.Error.OktaApiError(oktaError, response.statusCode)
                            } else {
                                val errorMessage = "Password change failed (status: ${response.statusCode})"
                                AppLogger.write(TAG, errorMessage)
                                _passwordChangeResult.value = PasswordChangeResult.Error.NetworkError(errorMessage)
                            }
                        }
                    },
                    onFailure = { error ->
                        val errorMessage = error.message ?: "Unknown error occurred"
                        AppLogger.write(TAG, "Password change failed with error: $errorMessage")
                        error.printStackTrace()
                        _passwordChangeResult.value = PasswordChangeResult.Error.NetworkError(errorMessage)
                    }
                )
            } catch (e: Exception) {
                val errorMessage = e.message ?: "An unexpected error occurred"
                AppLogger.write(TAG, "Password change encountered exception: $errorMessage")
                e.printStackTrace()
                _passwordChangeResult.value = PasswordChangeResult.Error.NetworkError(errorMessage)
            }
        }
    }

    /**
     * Dismisses the current password change error by resetting the result state to Idle.
     * This allows the user to retry the password change after an error.
     */
    fun dismissPasswordChangeError() {
        _passwordChangeResult.value = PasswordChangeResult.Idle
    }
}
