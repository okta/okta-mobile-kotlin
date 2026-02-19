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

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.okta.directauth.app.model.AuthMethod
import com.okta.directauth.app.model.AuthMethod.Mfa.OktaVerify
import com.okta.directauth.app.model.AuthMethod.Mfa.Otp
import com.okta.directauth.app.model.AuthMethod.Mfa.Passkeys
import com.okta.directauth.app.model.AuthMethod.Mfa.Sms
import com.okta.directauth.app.model.AuthMethod.Mfa.Voice
import com.okta.directauth.app.model.AuthMethod.Password
import com.okta.directauth.app.model.AuthScreen
import com.okta.directauth.app.platform.AppStorage
import com.okta.directauth.app.platform.PlatformBackHandler
import com.okta.directauth.app.screen.AuthenticatedScreen
import com.okta.directauth.app.screen.AuthenticationFlow
import com.okta.directauth.app.screen.CodeEntryScreen
import com.okta.directauth.app.screen.ErrorScreen
import com.okta.directauth.app.screen.OobPollingScreen
import com.okta.directauth.app.screen.PasswordChangeScreen
import com.okta.directauth.app.screen.PasswordChangeSuccessScreen
import com.okta.directauth.app.screen.asString
import com.okta.directauth.app.ui.theme.DirectAuthAppTheme
import com.okta.directauth.app.util.AppLogger
import com.okta.directauth.app.viewModel.AuthenticationFlowViewModel
import com.okta.directauth.app.viewModel.MainViewModel
import com.okta.directauth.model.DirectAuthContinuation
import com.okta.directauth.model.DirectAuthenticationError
import com.okta.directauth.model.DirectAuthenticationIntent
import com.okta.directauth.model.DirectAuthenticationState
import kotlinx.coroutines.launch

private const val TAG = "App"

@Composable
fun App(appStorage: AppStorage) {
    val mainViewModel: MainViewModel = viewModel { MainViewModel() }
    val authenticationFlowViewModel: AuthenticationFlowViewModel = viewModel { AuthenticationFlowViewModel() }

    DirectAuthAppTheme {
        val intent = mainViewModel.authIntent.collectAsState()
        val selectAuthMethod by authenticationFlowViewModel.selectedAuthMethod.collectAsState()
        val username by authenticationFlowViewModel.username.collectAsState()
        val authFlowState by authenticationFlowViewModel.uiState.collectAsState()
        val savedUsername by appStorage.get("username").collectAsState(initial = "")
        val scope = rememberCoroutineScope()

        val authStateFlow =
            if (intent.value == DirectAuthenticationIntent.RECOVERY) {
                mainViewModel.directAuthSspr.authenticationState
            } else {
                mainViewModel.directAuth.authenticationState
            }
        val signInState by authStateFlow.collectAsState()
        val passwordChangeResult by mainViewModel.passwordChangeResult.collectAsState()

        AppLogger.write(TAG, "State: $signInState - Intent: $intent")
        var displayedState by remember { mutableStateOf(signInState) }

        PlatformBackHandler(enabled = true) { authenticationFlowViewModel.goBack() }

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

        Crossfade(targetState = displayedState) { currentState ->
            when (currentState) {
                is DirectAuthContinuation.OobPending -> {
                    when (selectAuthMethod) {
                        Sms, Voice -> {
                            val supportedAuthenticators: List<AuthMethod> = listOf(Password, OktaVerify, Otp, Passkeys, Sms, Voice)
                            AuthenticationFlow(
                                state = authFlowState,
                                selectedAuthMethod = selectAuthMethod,
                                savedUsername = savedUsername,
                                supportedAuthenticators = supportedAuthenticators,
                                onSignIn = mainViewModel::signIn,
                                onResume = mainViewModel::resume,
                                onReset = mainViewModel::reset,
                                onForgotPassword = mainViewModel::forgotPassword,
                                onNext = authenticationFlowViewModel::next,
                                onResetNav = authenticationFlowViewModel::reset,
                                onSelectAuthenticator = authenticationFlowViewModel::selectAuthenticator,
                                onAuthenticatorSelect = authenticationFlowViewModel::onAuthenticatorSelected,
                                onSaveUsername = { name, rememberMe ->
                                    scope.launch {
                                        if (rememberMe) {
                                            if (name != savedUsername) appStorage.save("username", name)
                                        } else {
                                            appStorage.clear("username")
                                        }
                                    }
                                }
                            )
                        }

                        else -> {
                            OobPollingScreen(
                                message = "Polling for OOB result...",
                                onCancel = mainViewModel::reset,
                                countdownSeconds = currentState.expirationInSeconds,
                                pollAction = { mainViewModel.pendingOob(currentState) }
                            )
                        }
                    }
                }

                is DirectAuthContinuation.Prompt -> {
                    CodeEntryScreen(
                        username = username,
                        backToSignIn = mainViewModel::reset,
                        verifyWithSomethingElse = null
                    ) { code ->
                        mainViewModel.prompt(currentState, code)
                    }
                }

                is DirectAuthContinuation.Transfer -> {
                    OobPollingScreen(
                        message = "Please verify the code on your other device",
                        onCancel = mainViewModel::reset,
                        bindingCode = currentState.bindingCode,
                        countdownSeconds = currentState.expirationInSeconds,
                        pollAction = { mainViewModel.transfer(currentState) }
                    )
                }

                is DirectAuthContinuation.WebAuthn -> {
                    TODO()
                }

                is DirectAuthenticationError -> {
                    ErrorScreen(
                        error = currentState.asString(),
                        onBack = {
                            mainViewModel.reset()
                            authenticationFlowViewModel.setInitialState(AuthScreen.UsernameInput(""))
                        }
                    )
                }

                is DirectAuthenticationState.Authenticated -> {
                    when (intent.value) {
                        DirectAuthenticationIntent.SIGN_IN -> {
                            AuthenticatedScreen(
                                idToken = currentState.token.idToken ?: "",
                                onSignOut = {
                                    mainViewModel.reset()
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
                                            mainViewModel.reset()
                                            authenticationFlowViewModel.setInitialState(AuthScreen.UsernameInput(""))
                                        }
                                    )
                                }

                                else -> {
                                    PasswordChangeScreen(
                                        username = username,
                                        backToSignIn = {
                                            mainViewModel.reset()
                                            authenticationFlowViewModel.setInitialState(AuthScreen.UsernameInput(""))
                                        },
                                        passwordChangeResult = passwordChangeResult,
                                        onDismissError = { mainViewModel.dismissPasswordChangeError() },
                                        next = { newPassword ->
                                            mainViewModel.changePassword(currentState.token.accessToken, newPassword)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                DirectAuthenticationState.Canceled,
                DirectAuthenticationState.Idle,
                is DirectAuthenticationState.MfaRequired,
                -> {
                    val supportedAuthenticators: List<AuthMethod> = listOf(Password, OktaVerify, Otp, Passkeys, Sms, Voice)
                    AuthenticationFlow(
                        state = authFlowState,
                        selectedAuthMethod = selectAuthMethod,
                        savedUsername = savedUsername,
                        supportedAuthenticators = if (intent.value == DirectAuthenticationIntent.RECOVERY) supportedAuthenticators.filterNot { it == Password } else supportedAuthenticators,
                        onSignIn = mainViewModel::signIn,
                        onResume = mainViewModel::resume,
                        onReset = mainViewModel::reset,
                        onForgotPassword = mainViewModel::forgotPassword,
                        onNext = authenticationFlowViewModel::next,
                        onResetNav = authenticationFlowViewModel::reset,
                        onSelectAuthenticator = authenticationFlowViewModel::selectAuthenticator,
                        onAuthenticatorSelect = authenticationFlowViewModel::onAuthenticatorSelected,
                        onSaveUsername = { name, rememberMe ->
                            scope.launch {
                                if (rememberMe) {
                                    if (name != savedUsername) appStorage.save("username", name)
                                } else {
                                    appStorage.clear("username")
                                }
                            }
                        }
                    )
                }

                is DirectAuthenticationState.AuthorizationPending -> {
                    Unit
                }
            }
        }
    }
}
