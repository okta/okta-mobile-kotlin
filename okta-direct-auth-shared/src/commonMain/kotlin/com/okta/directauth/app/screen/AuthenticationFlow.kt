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
package com.okta.directauth.app.screen

import androidx.compose.runtime.Composable
import com.okta.directauth.app.model.AuthMethod
import com.okta.directauth.app.model.AuthMethod.Mfa
import com.okta.directauth.app.model.AuthScreen
import com.okta.directauth.model.DirectAuthenticationState
import kotlinx.coroutines.Job

@Composable
fun AuthenticationFlow(
    state: AuthScreen,
    selectedAuthMethod: AuthMethod?,
    savedUsername: String,
    supportedAuthenticators: List<AuthMethod>,
    onSignIn: (username: String, password: String, authMethod: AuthMethod) -> Job,
    onResume: (otp: String, mfaMethod: Mfa, mfaRequired: DirectAuthenticationState.MfaRequired) -> Job,
    onReset: () -> Unit,
    onForgotPassword: () -> Unit,
    onNext: (username: String) -> Unit,
    onResetNav: (username: String) -> Unit,
    onSelectAuthenticator: (username: String, mfaRequired: DirectAuthenticationState.MfaRequired?) -> Unit,
    onAuthenticatorSelect: (username: String, authMethod: AuthMethod, mfaRequired: DirectAuthenticationState.MfaRequired?) -> Unit,
    onSaveUsername: (username: String, rememberMe: Boolean) -> Unit,
) {
    when (state) {
        is AuthScreen.UsernameInput -> {
            UsernameScreen(
                savedUsername = savedUsername,
                onNext = { updatedUsername, rememberMe ->
                    onSaveUsername(updatedUsername, rememberMe)
                    onNext(updatedUsername)
                }
            )
        }

        is AuthScreen.SelectAuthenticator -> {
            SelectAuthenticatorScreen(
                username = savedUsername,
                supportedAuthenticators = supportedAuthenticators,
                backToSignIn = {
                    onReset()
                    onResetNav(state.username)
                },
                onSelectAuthenticator = { username, authMethod ->
                    onAuthenticatorSelect(username, authMethod, null)
                }
            )
        }

        is AuthScreen.PasswordAuthenticator -> {
            PasswordScreen(
                username = state.username,
                backToSignIn = {
                    onReset()
                    onResetNav(state.username)
                },
                verifyWithSomethingElse = { onSelectAuthenticator(state.username, null) },
                forgotPassword = {
                    onForgotPassword()
                    onSelectAuthenticator(state.username, null)
                }
            ) { password ->
                onSignIn(state.username, password, AuthMethod.Password)
            }
        }

        is AuthScreen.OktaVerify -> {
            ChallengeScreen(
                title = "Get a push notification",
                buttonText = "Send push",
                username = state.username,
                backToSignIn = {
                    onReset()
                    onResetNav(state.username)
                },
                verifyWithSomethingElse = { onSelectAuthenticator(state.username, state.mfaRequired) },
                onChallenge = {
                    if (state.mfaRequired != null) {
                        onResume("", AuthMethod.Mfa.OktaVerify, state.mfaRequired)
                    } else {
                        onSignIn(state.username, "", AuthMethod.Mfa.OktaVerify)
                    }
                }
            )
        }

        is AuthScreen.Otp -> {
            CodeEntryScreen(
                username = state.username,
                backToSignIn = {
                    onReset()
                    onResetNav(state.username)
                },
                verifyWithSomethingElse = { onSelectAuthenticator(state.username, state.mfaRequired) }
            ) { code ->
                if (state.mfaRequired != null) {
                    onResume(code, AuthMethod.Mfa.Otp, state.mfaRequired)
                } else {
                    onSignIn(state.username, code, AuthMethod.Mfa.Otp)
                }
            }
        }

        is AuthScreen.Passkeys -> {
            PasskeysScreen(
                state.username,
                backToSignIn = {
                    onReset()
                    onResetNav(state.username)
                },
                verifyWithSomethingElse = { onSelectAuthenticator(state.username, state.mfaRequired) }
            )
        }

        is AuthScreen.Sms -> {
            if (state.codeSent) {
                CodeEntryScreen(
                    username = state.username,
                    backToSignIn = { onResetNav(state.username) },
                    verifyWithSomethingElse = {
                        onSelectAuthenticator(
                            state.username,
                            state.mfaRequired
                        )
                    }
                ) { code ->
                    if (state.mfaRequired != null) {
                        onResume(code, AuthMethod.Mfa.Sms, state.mfaRequired)
                    } else {
                        onSignIn(state.username, code, AuthMethod.Mfa.Sms)
                    }
                }
            } else {
                ChallengeScreen(
                    title = "Get a code via SMS",
                    buttonText = "Send code",
                    username = state.username,
                    backToSignIn = { onResetNav(state.username) },
                    verifyWithSomethingElse = {
                        onSelectAuthenticator(
                            state.username,
                            state.mfaRequired
                        )
                    },
                    onChallenge = {
                        if (state.mfaRequired != null) {
                            onResume("", AuthMethod.Mfa.Sms, state.mfaRequired)
                        } else {
                            onSignIn(state.username, "", AuthMethod.Mfa.Sms)
                        }
                    }
                )
            }
        }

        is AuthScreen.Voice -> {
            if (state.codeSent) {
                CodeEntryScreen(
                    username = state.username,
                    backToSignIn = { onResetNav(state.username) },
                    verifyWithSomethingElse = {
                        onSelectAuthenticator(
                            state.username,
                            state.mfaRequired
                        )
                    }
                ) { code ->
                    if (state.mfaRequired != null) {
                        onResume(code, AuthMethod.Mfa.Voice, state.mfaRequired)
                    } else {
                        onSignIn(state.username, code, AuthMethod.Mfa.Voice)
                    }
                }
            } else {
                ChallengeScreen(
                    title = "Get a code via voice call",
                    buttonText = "Call me",
                    username = state.username,
                    backToSignIn = { onResetNav(state.username) },
                    verifyWithSomethingElse = {
                        onSelectAuthenticator(
                            state.username,
                            state.mfaRequired
                        )
                    },
                    onChallenge = {
                        if (state.mfaRequired != null) {
                            onResume("", AuthMethod.Mfa.Voice, state.mfaRequired)
                        } else {
                            onSignIn(state.username, "", AuthMethod.Mfa.Voice)
                        }
                    }
                )
            }
        }

        is AuthScreen.MfaRequired -> {
            val mfaMethods = listOf(Mfa.OktaVerify, Mfa.Otp, Mfa.Passkeys, Mfa.Sms, Mfa.Voice)
            // filter out the auth method used by initial authentication
            val authMethods =
                selectedAuthMethod?.let { exclude ->
                    val excludedLabels =
                        when (exclude) {
                            is Mfa.Sms, is Mfa.Voice -> setOf(Mfa.Sms.label, Mfa.Voice.label)
                            else -> setOf(exclude.label)
                        }
                    mfaMethods.filter { it.label !in excludedLabels }
                } ?: mfaMethods

            SelectAuthenticatorScreen(
                username = state.username,
                supportedAuthenticators = authMethods,
                backToSignIn = {
                    onReset()
                    onResetNav(state.username)
                },
                onSelectAuthenticator = { username, mfaMethod ->
                    onAuthenticatorSelect(username, mfaMethod, state.mfaRequired)
                }
            )
        }
    }
}
