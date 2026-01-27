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
import com.okta.directauth.app.model.AuthMethod
import com.okta.directauth.app.model.AuthScreen
import com.okta.directauth.model.DirectAuthenticationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel that manages the UI navigation state for the authentication flow.
 *
 * This ViewModel is responsible for:
 * - Tracking the current screen being displayed (username, authenticator selection, password, etc.)
 * - Managing navigation between authentication screens
 * - Keeping track of the username and selected authentication method
 * - Handling back navigation through the authentication flow
 *
 * The ViewModel works in conjunction with MainViewModel:
 * - AuthenticationFlowViewModel: Manages UI navigation state (which screen to show)
 * - MainViewModel: Manages authentication logic (API calls, state from SDK)
 *
 * Screen Navigation Flow:
 * 1. UsernameInput -> SelectAuthenticator -> (Password/OktaVerify/Otp/Sms/Voice/Passkeys)
 * 2. If MFA required: MfaRequired -> (OktaVerify/Otp/Sms/Voice/Passkeys)
 */
class AuthenticationFlowViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<AuthScreen>(AuthScreen.UsernameInput(""))

    /**
     * The current authentication screen to display.
     * Observed by UI to determine which screen to render.
     */
    val uiState: StateFlow<AuthScreen> = _uiState.asStateFlow()

    private val _selectedAuthMethod = MutableStateFlow<AuthMethod?>(null)

    /**
     * The authentication method selected by the user.
     * Tracked to filter out the same method from MFA options if MFA is required.
     */
    val selectedAuthMethod: StateFlow<AuthMethod?> = _selectedAuthMethod.asStateFlow()

    private val _username = MutableStateFlow("")

    /**
     * The current username being authenticated.
     * Persisted across screen navigation within the auth flow.
     */
    val username: StateFlow<String> = _username.asStateFlow()

    /**
     * Sets the initial UI state (typically used when transitioning to MFA).
     *
     * Called when:
     * - App detects MfaRequired state after primary authentication
     * - User signs out (resets to UsernameInput)
     *
     * @param state The screen state to set as current
     */
    fun setInitialState(state: AuthScreen) {
        _uiState.value = state
    }

    /**
     * Advances to authenticator selection after username is entered.
     *
     * Called when:
     * - User submits a valid username on UsernameScreen
     *
     * Navigation: UsernameInput -> SelectAuthenticator
     *
     * @param username The username entered by the user
     */
    fun next(username: String) {
        _username.value = username
        _uiState.value = AuthScreen.SelectAuthenticator(username)
    }

    /**
     * Resets the authentication flow back to username entry.
     *
     * Called when:
     * - User clicks "Back to sign in" from any screen
     * - Authentication needs to restart
     *
     * Navigation: Any screen -> UsernameInput
     *
     * @param username The username to pre-populate (typically the current username)
     */
    fun reset(username: String) {
        _uiState.value = AuthScreen.UsernameInput(username)
    }

    /**
     * Returns to the authenticator selection screen.
     *
     * Called when:
     * - User clicks "Verify with something else" from an authenticator screen
     * - User wants to choose a different authentication method
     *
     * Behavior:
     * - If MFA is required: Shows MfaRequired screen with available MFA methods
     * - Otherwise: Shows SelectAuthenticator screen with all auth methods
     *
     * @param username The username being authenticated
     * @param mfaRequired Optional MFA state if MFA is required. If present, shows only MFA methods.
     */
    fun selectAuthenticator(
        username: String,
        mfaRequired: DirectAuthenticationState.MfaRequired? = null,
    ) {
        if (mfaRequired != null) {
            _selectedAuthMethod.value = null
            _uiState.value = AuthScreen.MfaRequired(username, mfaRequired)
        } else {
            _uiState.value = AuthScreen.SelectAuthenticator(username)
        }
    }

    /**
     * Navigates to the appropriate screen for the selected authentication method.
     *
     * Called when:
     * - User selects an authentication method from SelectAuthenticatorScreen
     * - MainViewModel DirectAuthenticationState is MfaRequired and user needs to select a different method
     *
     * Navigation mapping:
     * - Password -> PasswordAuthenticator screen
     * - OktaVerify -> OktaVerify screen (push notification)
     * - OTP -> Otp screen (code entry)
     * - SMS -> Sms screen (code entry)
     * - Voice -> Voice screen (code entry)
     * - Passkeys -> Passkeys screen (WebAuthn)
     *
     * The method also saves the selected auth method, which is used to:
     * - Filter it out from MFA options if MFA is later required
     * - Track user's authentication journey
     *
     * @param username The username being authenticated
     * @param authMethod The authentication method selected by the user
     * @param mfaRequired Optional MFA state if this is an MFA selection (null for primary auth)
     */
    fun onAuthenticatorSelected(
        username: String,
        authMethod: AuthMethod,
        mfaRequired: DirectAuthenticationState.MfaRequired?,
    ) {
        _selectedAuthMethod.value = authMethod
        when (authMethod) {
            AuthMethod.Mfa.OktaVerify -> _uiState.value = AuthScreen.OktaVerify(username, mfaRequired)
            AuthMethod.Mfa.Otp -> _uiState.value = AuthScreen.Otp(username, mfaRequired)
            AuthMethod.Mfa.Passkeys -> _uiState.value = AuthScreen.Passkeys(username, mfaRequired)
            AuthMethod.Mfa.Sms -> _uiState.value = AuthScreen.Sms(username, mfaRequired)
            AuthMethod.Mfa.Voice -> _uiState.value = AuthScreen.Voice(username, mfaRequired)
            AuthMethod.Password -> _uiState.value = AuthScreen.PasswordAuthenticator(username)
        }
    }

    /**
     * Updates the UI state to indicate that a one-time code has been sent.
     *
     * This function is called for authentication methods like SMS and Voice, where a code is sent
     * to the user's device. It updates the current screen's state to reflect that the code has
     * been sent, which can be used to show a confirmation message or change the UI accordingly.
     *
     * For example, it might change the text on a button from "Send Code" to "Resend Code" or
     * display a message like "A code has been sent to your phone."
     *
     * The function checks the current UI state and, if it's a screen that supports sending codes
     * (like `AuthScreen.Sms` or `AuthScreen.Voice`), it creates a new state with the `codeSent`
     * flag set to `true`.
     */
    fun codeSent() {
        when (val currentState = _uiState.value) {
            is AuthScreen.Sms -> {
                _uiState.value =
                    AuthScreen.Sms(
                        username = currentState.username,
                        mfaRequired = currentState.mfaRequired,
                        codeSent = true
                    )
            }

            is AuthScreen.Voice -> {
                _uiState.value =
                    AuthScreen.Voice(
                        username = currentState.username,
                        mfaRequired = currentState.mfaRequired,
                        codeSent = true
                    )
            }

            else -> {
                // Do nothing
            }
        }
    }

    /**
     * Handles back navigation through the authentication flow.
     *
     * Called when:
     * - User presses the system back button (intercepted by BackHandler in MainActivity)
     * - User navigates back to the previous screen
     *
     * Navigation rules:
     * - From SelectAuthenticator or MfaRequired -> UsernameInput
     * - From any authenticator screen (Password/OktaVerify/Otp/etc.) -> SelectAuthenticator
     * - From UsernameInput -> Does nothing (already at the start)
     *
     * Also clears the selected authentication method when navigating back.
     */
    fun goBack() {
        _selectedAuthMethod.value = null

        when (val currentState = _uiState.value) {
            is AuthScreen.SelectAuthenticator,
            is AuthScreen.MfaRequired,
            -> {
                _uiState.value = AuthScreen.UsernameInput(currentState.username)
            }

            is AuthScreen.PasswordAuthenticator,
            is AuthScreen.OktaVerify,
            is AuthScreen.Otp,
            is AuthScreen.Passkeys,
            is AuthScreen.Sms,
            is AuthScreen.Voice,
            -> {
                _uiState.value = AuthScreen.SelectAuthenticator(currentState.username)
            }

            is AuthScreen.UsernameInput -> {
                Unit
            } // Do nothing
        }
    }
}
