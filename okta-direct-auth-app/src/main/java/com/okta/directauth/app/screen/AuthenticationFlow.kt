package com.okta.directauth.app.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.okta.directauth.app.model.AuthMethod
import com.okta.directauth.app.model.AuthMethod.Mfa
import com.okta.directauth.app.model.AuthScreen
import com.okta.directauth.app.storage.AppStorage
import com.okta.directauth.app.viewModel.AuthenticationFlowViewModel
import com.okta.directauth.app.viewModel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun AuthenticationFlow(
    mainView: MainViewModel,
    authenticationViewModel: AuthenticationFlowViewModel,
    supportedAuthenticators: List<AuthMethod>,
) {
    val state by authenticationViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val appStorage = remember { AppStorage(context) }
    val savedUsername by appStorage.get("username").collectAsState(initial = "")
    val scope = rememberCoroutineScope()
    when (val currentState = state) {
        is AuthScreen.UsernameInput -> UsernameScreen(savedUsername) { updatedUsername, rememberMe ->
            scope.launch {
                if (rememberMe) {
                    if (updatedUsername != savedUsername) appStorage.save("username", updatedUsername)
                } else {
                    appStorage.clear("username")
                }
            }
            authenticationViewModel.next(updatedUsername)
        }

        is AuthScreen.SelectAuthenticator -> SelectAuthenticatorScreen(
            username = savedUsername,
            supportedAuthenticators = supportedAuthenticators,
            backToSignIn = {
                mainView.reset()
                authenticationViewModel.reset(currentState.username)
            },
            onAuthenticatorSelected = { username, authMethod ->
                authenticationViewModel.onAuthenticatorSelected(username, authMethod, null)
            }
        )

        is AuthScreen.PasswordAuthenticator -> PasswordScreen(
            username = currentState.username,
            backToSignIn = {
                mainView.reset()
                authenticationViewModel.reset(currentState.username)
            },
            verifyWithSomethingElse = { authenticationViewModel.selectAuthenticator(currentState.username) },
            forgotPassword = {
                mainView.forgotPassword()
                authenticationViewModel.selectAuthenticator(currentState.username)
            },
        ) { password ->
            mainView.signIn(currentState.username, password, AuthMethod.Password)
        }

        is AuthScreen.OktaVerify -> ChallengeScreen(
            title = "Get a push notification",
            buttonText = "Send push",
            username = currentState.username,
            backToSignIn = {
                mainView.reset()
                authenticationViewModel.reset(currentState.username)
            },
            verifyWithSomethingElse = { authenticationViewModel.selectAuthenticator(currentState.username, currentState.mfaRequired) },
        ) {
            if (currentState.mfaRequired != null) {
                mainView.resume("", AuthMethod.Mfa.OktaVerify, currentState.mfaRequired)
            } else {
                mainView.signIn(currentState.username, "", AuthMethod.Mfa.OktaVerify)
            }
        }

        is AuthScreen.Otp -> CodeEntryScreenWrapper(
            username = currentState.username,
            mfaRequired = currentState.mfaRequired,
            authMethod = AuthMethod.Mfa.Otp,
            authenticationViewModel = authenticationViewModel,
            mainView = mainView
        )

        is AuthScreen.Passkeys -> PasskeysScreen(
            currentState.username,
            backToSignIn = {
                mainView.reset()
                authenticationViewModel.reset(currentState.username)
            },
            verifyWithSomethingElse = { authenticationViewModel.selectAuthenticator(currentState.username, currentState.mfaRequired) },
        )

        is AuthScreen.Sms ->
            if (currentState.codeSent) {
                CodeEntryScreen(
                    username = currentState.username,
                    backToSignIn = { authenticationViewModel.reset(currentState.username) },
                    verifyWithSomethingElse = {
                        authenticationViewModel.selectAuthenticator(
                            currentState.username,
                            currentState.mfaRequired
                        )
                    },
                ) { code ->
                    if (currentState.mfaRequired != null) {
                        mainView.resume(code, AuthMethod.Mfa.Sms, currentState.mfaRequired)
                    } else {
                        mainView.signIn(currentState.username, code, AuthMethod.Mfa.Sms)
                    }
                }
            } else {
                ChallengeScreen(
                    title = "Get a code via SMS",
                    buttonText = "Send code",
                    username = currentState.username,
                    backToSignIn = { authenticationViewModel.reset(currentState.username) },
                    verifyWithSomethingElse = {
                        authenticationViewModel.selectAuthenticator(
                            currentState.username,
                            currentState.mfaRequired
                        )
                    },
                ) {
                    if (currentState.mfaRequired != null) {
                        mainView.resume("", AuthMethod.Mfa.Sms, currentState.mfaRequired)
                    } else {
                        mainView.signIn(currentState.username, "", AuthMethod.Mfa.Sms)
                    }
                }
            }

        is AuthScreen.Voice ->
            if (currentState.codeSent) {
                CodeEntryScreen(
                    username = currentState.username,
                    backToSignIn = { authenticationViewModel.reset(currentState.username) },
                    verifyWithSomethingElse = {
                        authenticationViewModel.selectAuthenticator(
                            currentState.username,
                            currentState.mfaRequired
                        )
                    },
                ) { code ->
                    if (currentState.mfaRequired != null) {
                        mainView.resume(code, AuthMethod.Mfa.Voice, currentState.mfaRequired)
                    } else {
                        mainView.signIn(currentState.username, code, AuthMethod.Mfa.Voice)
                    }
                }
            } else {
                ChallengeScreen(
                    title = "Get a code via voice call",
                    buttonText = "Call me",
                    username = currentState.username,
                    backToSignIn = { authenticationViewModel.reset(currentState.username) },
                    verifyWithSomethingElse = {
                        authenticationViewModel.selectAuthenticator(
                            currentState.username,
                            currentState.mfaRequired
                        )
                    },
                ) {
                    if (currentState.mfaRequired != null) {
                        mainView.resume("", AuthMethod.Mfa.Voice, currentState.mfaRequired)
                    } else {
                        mainView.signIn(currentState.username, "", AuthMethod.Mfa.Voice)
                    }
                }
            }

        is AuthScreen.MfaRequired -> {
            val mfaMethods = listOf(Mfa.OktaVerify, Mfa.Otp, Mfa.Passkeys, Mfa.Sms, Mfa.Voice)
            // filter out the auth method used by initial authentication
            val authMethods = authenticationViewModel.selectedAuthMethod.value?.let { exclude ->
                val excludedLabels = when (exclude) {
                    is Mfa.Sms, is Mfa.Voice -> setOf(Mfa.Sms.label, Mfa.Voice.label)
                    else -> setOf(exclude.label)
                }
                mfaMethods.filter { it.label !in excludedLabels }
            } ?: mfaMethods

            SelectAuthenticatorScreen(
                username = currentState.username,
                supportedAuthenticators = authMethods,
                backToSignIn = {
                    mainView.reset()
                    authenticationViewModel.reset(currentState.username)
                },
            ) { username, mfaMethod ->
                authenticationViewModel.onAuthenticatorSelected(username, mfaMethod, currentState.mfaRequired)
            }
        }
    }
}

/**
 * Wrapper composable for CodeEntryScreen (OTP, SMS, Voice).
 */
@Composable
private fun CodeEntryScreenWrapper(
    username: String,
    mfaRequired: com.okta.directauth.model.DirectAuthenticationState.MfaRequired?,
    authMethod: AuthMethod.Mfa,
    authenticationViewModel: AuthenticationFlowViewModel,
    mainView: MainViewModel
) {
    CodeEntryScreen(
        username = username,
        backToSignIn = {
            mainView.reset()
            authenticationViewModel.reset(username)
        },
        verifyWithSomethingElse = { authenticationViewModel.selectAuthenticator(username, mfaRequired) },
    ) { code ->
        if (mfaRequired != null) {
            mainView.resume(code, authMethod, mfaRequired)
        } else {
            mainView.signIn(username, code, authMethod)
        }
    }
}
