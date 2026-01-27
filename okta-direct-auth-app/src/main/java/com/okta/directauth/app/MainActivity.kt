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
package com.okta.directauth.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.okta.directauth.app.model.AuthMethod
import com.okta.directauth.app.model.AuthMethod.Mfa.OktaVerify
import com.okta.directauth.app.model.AuthMethod.Mfa.Otp
import com.okta.directauth.app.model.AuthMethod.Mfa.Passkeys
import com.okta.directauth.app.model.AuthMethod.Mfa.Sms
import com.okta.directauth.app.model.AuthMethod.Mfa.Voice
import com.okta.directauth.app.model.AuthMethod.Password
import com.okta.directauth.app.model.AuthScreen
import com.okta.directauth.app.screen.AuthenticatedScreen
import com.okta.directauth.app.screen.AuthenticationFlow
import com.okta.directauth.app.screen.CodeEntryScreen
import com.okta.directauth.app.screen.ErrorScreen
import com.okta.directauth.app.screen.OobPollingScreen
import com.okta.directauth.app.screen.PasswordChangeScreen
import com.okta.directauth.app.screen.PasswordChangeSuccessScreen
import com.okta.directauth.app.screen.asString
import com.okta.directauth.app.ui.theme.DirectAuthAppTheme
import com.okta.directauth.app.viewModel.AuthenticationFlowViewModel
import com.okta.directauth.app.viewModel.MainViewModel
import com.okta.directauth.model.DirectAuthContinuation
import com.okta.directauth.model.DirectAuthenticationError
import com.okta.directauth.model.DirectAuthenticationIntent
import com.okta.directauth.model.DirectAuthenticationState

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val authenticationFlowViewModel: AuthenticationFlowViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DirectAuthAppTheme {
                val intent = viewModel.authIntent.collectAsState()
                val selectAuthMethod by authenticationFlowViewModel.selectedAuthMethod.collectAsState()
                val username by authenticationFlowViewModel.username.collectAsState()

                val authStateFlow =
                    if (intent.value == DirectAuthenticationIntent.RECOVERY) {
                        viewModel.directAuthSspr.authenticationState
                    } else {
                        viewModel.directAuth.authenticationState
                    }
                val signInState by authStateFlow.collectAsState()
                val passwordChangeResult by viewModel.passwordChangeResult.collectAsState()

                Log.d("MainActivity", "State: $signInState - Intent: $intent")
                var displayedState by remember { mutableStateOf(signInState) }

                BackHandler(enabled = true) { authenticationFlowViewModel.goBack() }

                LaunchedEffect(signInState) {
                    if (signInState is DirectAuthenticationState.MfaRequired) {
                        authenticationFlowViewModel.setInitialState(
                            AuthScreen.MfaRequired(authenticationFlowViewModel.username.value, signInState as DirectAuthenticationState.MfaRequired)
                        )
                    }

                    if (signInState is DirectAuthContinuation.OobPending) {
                        authenticationFlowViewModel.codeSent()
                    }

                    if (signInState !is DirectAuthenticationState.AuthorizationPending) {
                        displayedState = signInState
                    }
                }

                Box(modifier = Modifier.statusBarsPadding()) {
                    Crossfade(targetState = displayedState) { currentState ->
                        /*
                         * Main state machine for the authentication flow.
                         *
                         * This when-expression handles all possible authentication states and displays
                         * the appropriate UI screen for each state:
                         *
                         * - OobPending: User initiated OOB (SMS/Voice/Push) and we're polling for result
                         * - Prompt: Server requires additional input (e.g., OTP code) during authentication
                         * - Transfer: User is authenticating via device transfer (shows binding code)
                         * - WebAuthn: Passkey/WebAuthn authentication is required (not yet implemented)
                         * - DirectAuthenticationError: Authentication failed with an error
                         * - Authenticated: User successfully authenticated (shows token)
                         * - Canceled/Idle/MfaRequired: Initial authentication flow (username/select authenticator)
                         * - Other states: States that don't require UI updates (e.g., AuthorizationPending)
                         */
                        when (currentState) {
                            // Out-of-Band authentication polling (SMS, Voice, Push)
                            is DirectAuthContinuation.OobPending -> {
                                when (selectAuthMethod) {
                                    Sms, Voice -> {
                                        val supportedAuthenticators: List<AuthMethod> = listOf(Password, OktaVerify, Otp, Passkeys, Sms, Voice)
                                        AuthenticationFlow(viewModel, authenticationFlowViewModel, supportedAuthenticators)
                                    }

                                    else -> {
                                        OobPollingScreen(
                                            message = "Polling for OOB result...",
                                            onCancel = viewModel::reset,
                                            countdownSeconds = currentState.expirationInSeconds,
                                            pollAction = { viewModel.pendingOob(currentState) }
                                        )
                                    }
                                }
                            }

                            // Server requires additional input during authentication (e.g., OTP code)
                            is DirectAuthContinuation.Prompt -> {
                                CodeEntryScreen(
                                    username = username,
                                    backToSignIn = viewModel::reset,
                                    verifyWithSomethingElse = null
                                ) { code ->
                                    viewModel.prompt(currentState, code)
                                }
                            }

                            // Device transfer authentication (e.g., authenticate via Okta Verify)
                            is DirectAuthContinuation.Transfer -> {
                                OobPollingScreen(
                                    message = "Please verify the code on your other device",
                                    onCancel = viewModel::reset,
                                    bindingCode = currentState.bindingCode,
                                    countdownSeconds = currentState.expirationInSeconds,
                                    pollAction = { viewModel.transfer(currentState) }
                                )
                            }

                            // Passkey/WebAuthn authentication (not yet implemented)
                            is DirectAuthContinuation.WebAuthn -> {
                                TODO()
                            }

                            // Authentication error occurred - display error message
                            is DirectAuthenticationError -> {
                                ErrorScreen(
                                    error = currentState.asString(),
                                    onBack = {
                                        viewModel.reset()
                                        authenticationFlowViewModel.setInitialState(AuthScreen.UsernameInput(""))
                                    }
                                )
                            }

                            // Successfully authenticated - display the idToken and sign-out option
                            is DirectAuthenticationState.Authenticated -> {
                                when (intent.value) {
                                    DirectAuthenticationIntent.SIGN_IN -> {
                                        AuthenticatedScreen(
                                            idToken = currentState.token.idToken ?: "",
                                            onSignOut = {
                                                viewModel.reset()
                                                authenticationFlowViewModel.setInitialState(AuthScreen.UsernameInput(""))
                                            }
                                        )
                                    }

                                    DirectAuthenticationIntent.RECOVERY -> {
                                        when (passwordChangeResult) {
                                            is MainViewModel.PasswordChangeResult.Success -> {
                                                PasswordChangeSuccessScreen(
                                                    username = username,
                                                    onBackToSignIn = {
                                                        viewModel.reset()
                                                        authenticationFlowViewModel.setInitialState(AuthScreen.UsernameInput(""))
                                                    }
                                                )
                                            }

                                            else -> {
                                                PasswordChangeScreen(
                                                    username = username,
                                                    backToSignIn = {
                                                        viewModel.reset()
                                                        authenticationFlowViewModel.setInitialState(AuthScreen.UsernameInput(""))
                                                    },
                                                    passwordChangeResult = passwordChangeResult,
                                                    onDismissError = { viewModel.dismissPasswordChangeError() },
                                                    next = { newPassword ->
                                                        viewModel.changePassword(currentState.token.accessToken, newPassword)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Initial authentication flow states - username then select authenticator to use.
                            DirectAuthenticationState.Canceled,
                            DirectAuthenticationState.Idle,
                            is DirectAuthenticationState.MfaRequired,
                            -> {
                                val supportedAuthenticators: List<AuthMethod> = listOf(Password, OktaVerify, Otp, Passkeys, Sms, Voice)
                                AuthenticationFlow(
                                    viewModel,
                                    authenticationFlowViewModel,
                                    if (intent.value == DirectAuthenticationIntent.RECOVERY) supportedAuthenticators.filterNot { it == Password } else supportedAuthenticators
                                )
                            }

                            is DirectAuthenticationState.AuthorizationPending -> {
                                Unit
                            } // Do nothing. This example does not use this state to update UI
                        }
                    }
                }
            }
        }
    }
}
