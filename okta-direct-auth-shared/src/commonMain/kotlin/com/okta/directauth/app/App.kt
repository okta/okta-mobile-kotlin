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
import com.okta.directauth.app.model.AppNavigationState
import com.okta.directauth.app.model.AuthMethod
import com.okta.directauth.app.model.AuthMethod.Mfa.OktaVerify
import com.okta.directauth.app.model.AuthMethod.Mfa.Otp
import com.okta.directauth.app.model.AuthMethod.Mfa.Passkeys
import com.okta.directauth.app.model.AuthMethod.Mfa.Sms
import com.okta.directauth.app.model.AuthMethod.Mfa.Voice
import com.okta.directauth.app.model.AuthMethod.Password
import com.okta.directauth.app.model.AuthScreen
import com.okta.directauth.app.model.OAuth2FlowState
import com.okta.directauth.app.platform.AppStorage
import com.okta.directauth.app.platform.PlatformBackHandler
import com.okta.directauth.app.platform.rememberWebAuthnCeremonyHandler
import com.okta.directauth.app.screen.AuthenticatedScreen
import com.okta.directauth.app.screen.AuthenticationFlow
import com.okta.directauth.app.screen.AuthenticatorNavContext
import com.okta.directauth.app.screen.CodeEntryScreen
import com.okta.directauth.app.screen.ErrorScreen
import com.okta.directauth.app.screen.HomeMenuScreen
import com.okta.directauth.app.screen.OAuth2AuthenticatedScreen
import com.okta.directauth.app.screen.OobPollingScreen
import com.okta.directauth.app.screen.PasskeysScreen
import com.okta.directauth.app.screen.PasswordChangeScreen
import com.okta.directauth.app.screen.PasswordChangeSuccessScreen
import com.okta.directauth.app.screen.asString
import com.okta.directauth.app.ui.theme.DirectAuthAppTheme
import com.okta.directauth.app.util.AppLogger
import com.okta.directauth.app.viewModel.AuthenticationFlowViewModel
import com.okta.directauth.app.viewModel.MainViewModel
import com.okta.directauth.app.viewModel.OAuth2FlowViewModel
import com.okta.directauth.model.DirectAuthContinuation
import com.okta.directauth.model.DirectAuthenticationError
import com.okta.directauth.model.DirectAuthenticationIntent
import com.okta.directauth.model.DirectAuthenticationState
import kotlinx.coroutines.launch

private const val TAG = "App"

@Composable
fun App(appStorage: AppStorage) {
    val oauth2ViewModel: OAuth2FlowViewModel = viewModel { OAuth2FlowViewModel() }
    var navigationState by remember { mutableStateOf<AppNavigationState>(AppNavigationState.HomeMenu) }

    val platformContext =
        com.okta.directauth.app.platform
            .rememberPlatformContext()

    DirectAuthAppTheme {
        Crossfade(targetState = navigationState) { navState ->
            when (navState) {
                AppNavigationState.HomeMenu -> {
                    HomeMenuScreen(
                        onDirectAuth = { navigationState = AppNavigationState.DirectAuth },
                        onResourceOwner = { navigationState = AppNavigationState.ResourceOwner },
                        onDeviceAuthorization = { navigationState = AppNavigationState.DeviceAuthorization },
                        onBrowserAuth = { navigationState = AppNavigationState.BrowserAuth },
                        onTokenExchange = { navigationState = AppNavigationState.TokenExchange },
                        onSessionToken = { navigationState = AppNavigationState.SessionToken }
                    )
                }

                AppNavigationState.DirectAuth -> {
                    DirectAuthFlow(
                        appStorage = appStorage,
                        onBackToHome = { navigationState = AppNavigationState.HomeMenu }
                    )
                }

                AppNavigationState.ResourceOwner -> {
                    OAuth2FlowScreen(
                        oauth2ViewModel = oauth2ViewModel,
                        onBackToHome = {
                            oauth2ViewModel.reset()
                            navigationState = AppNavigationState.HomeMenu
                        }
                    ) { onBack ->
                        com.okta.directauth.app.screen.ResourceOwnerScreen(
                            onSignIn = { username, password -> oauth2ViewModel.startResourceOwner(username, password) },
                            onBack = onBack
                        )
                    }
                }

                AppNavigationState.DeviceAuthorization -> {
                    OAuth2FlowScreen(
                        oauth2ViewModel = oauth2ViewModel,
                        onBackToHome = {
                            oauth2ViewModel.reset()
                            navigationState = AppNavigationState.HomeMenu
                        }
                    ) { onBack ->
                        com.okta.directauth.app.screen.DeviceAuthorizationScreen(
                            flowState = oauth2ViewModel.flowState.collectAsState().value,
                            onStart = { oauth2ViewModel.startDeviceAuthorization() },
                            onCancel = onBack
                        )
                    }
                }

                AppNavigationState.BrowserAuth -> {
                    OAuth2FlowScreen(
                        oauth2ViewModel = oauth2ViewModel,
                        onBackToHome = {
                            oauth2ViewModel.reset()
                            navigationState = AppNavigationState.HomeMenu
                        }
                    ) { onBack ->
                        com.okta.directauth.app.screen.BrowserAuthScreen(
                            onSignIn = { oauth2ViewModel.startBrowserAuth(platformContext) },
                            onBack = onBack
                        )
                    }
                }

                AppNavigationState.TokenExchange -> {
                    OAuth2FlowScreen(
                        oauth2ViewModel = oauth2ViewModel,
                        onBackToHome = {
                            oauth2ViewModel.reset()
                            navigationState = AppNavigationState.HomeMenu
                        }
                    ) { onBack ->
                        com.okta.directauth.app.screen.TokenExchangeScreen(
                            onStartExchange = { idToken, deviceSecret -> oauth2ViewModel.startTokenExchange(idToken, deviceSecret) },
                            onBack = onBack
                        )
                    }
                }

                AppNavigationState.SessionToken -> {
                    OAuth2FlowScreen(
                        oauth2ViewModel = oauth2ViewModel,
                        onBackToHome = {
                            oauth2ViewModel.reset()
                            navigationState = AppNavigationState.HomeMenu
                        }
                    ) { onBack ->
                        com.okta.directauth.app.screen.SessionTokenScreen(
                            onStartFlow = { sessionToken -> oauth2ViewModel.startSessionToken(sessionToken) },
                            onBack = onBack
                        )
                    }
                }
            }
        }
    }
}

/**
 * Helper composable that renders an OAuth2 flow screen with shared authenticated/error handling.
 *
 * When the flow state is [OAuth2FlowState.Authenticated], shows [OAuth2AuthenticatedScreen].
 * When the flow state is [OAuth2FlowState.Error], shows [ErrorScreen].
 * Otherwise, delegates to [content] for flow-specific UI.
 */
@Composable
private fun OAuth2FlowScreen(
    oauth2ViewModel: OAuth2FlowViewModel,
    onBackToHome: () -> Unit,
    content: @Composable (onBack: () -> Unit) -> Unit,
) {
    val flowState by oauth2ViewModel.flowState.collectAsState()

    when (val state = flowState) {
        is OAuth2FlowState.Authenticated -> {
            OAuth2AuthenticatedScreen(
                tokenInfo = state.tokenInfo,
                onBackToHome = onBackToHome
            )
        }

        is OAuth2FlowState.Error -> {
            ErrorScreen(
                error = state.message,
                onBack = onBackToHome
            )
        }

        else -> {
            content(onBackToHome)
        }
    }
}

/**
 * The existing Direct Authentication flow, extracted from the original App composable.
 */
@Composable
private fun DirectAuthFlow(
    appStorage: AppStorage,
    onBackToHome: () -> Unit,
) {
    val mainViewModel: MainViewModel = viewModel { MainViewModel() }
    val authenticationFlowViewModel: AuthenticationFlowViewModel = viewModel { AuthenticationFlowViewModel() }

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

    PlatformBackHandler(enabled = true) {
        if (authFlowState is AuthScreen.UsernameInput) {
            onBackToHome()
        } else {
            authenticationFlowViewModel.goBack()
        }
    }

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
                context(AuthenticatorNavContext(backToSignIn = mainViewModel::reset)) {
                    CodeEntryScreen(username = username) { code ->
                        mainViewModel.prompt(currentState, code)
                    }
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
                val handler = rememberWebAuthnCeremonyHandler()
                if (handler != null) {
                    context(
                        AuthenticatorNavContext(
                            backToSignIn = {
                                mainViewModel.reset()
                                authenticationFlowViewModel.setInitialState(AuthScreen.UsernameInput(""))
                            },
                            verifyWithSomethingElse = { authenticationFlowViewModel.selectAuthenticator(username) }
                        )
                    ) {
                        PasskeysScreen(
                            username = username,
                            onStartCeremony = { mainViewModel.webAuthn(currentState, handler) }
                        )
                    }
                } else {
                    ErrorScreen(
                        error = "WebAuthn is not supported on this platform.",
                        onBack = {
                            mainViewModel.reset()
                            authenticationFlowViewModel.setInitialState(AuthScreen.UsernameInput(""))
                        }
                    )
                }
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
                                context(
                                    AuthenticatorNavContext(
                                        backToSignIn = {
                                            mainViewModel.reset()
                                            authenticationFlowViewModel.setInitialState(AuthScreen.UsernameInput(""))
                                        }
                                    )
                                ) {
                                    PasswordChangeScreen(
                                        username = username,
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
