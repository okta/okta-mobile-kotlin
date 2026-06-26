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
            context(
                AuthenticatorNavContext(backToSignIn = {
                    onReset()
                    onResetNav(state.username)
                })
            ) {
                SelectAuthenticatorScreen(
                    username = savedUsername,
                    supportedAuthenticators = supportedAuthenticators,
                    onSelectAuthenticator = { username, authMethod ->
                        onAuthenticatorSelect(username, authMethod, null)
                    }
                )
            }
        }

        is AuthScreen.PasswordAuthenticator -> {
            context(
                AuthenticatorNavContext(
                    backToSignIn = {
                        onReset()
                        onResetNav(state.username)
                    },
                    verifyWithSomethingElse = { onSelectAuthenticator(state.username, null) }
                )
            ) {
                PasswordScreen(
                    username = state.username,
                    forgotPassword = {
                        onForgotPassword()
                        onSelectAuthenticator(state.username, null)
                    }
                ) { password ->
                    onSignIn(state.username, password, AuthMethod.Password)
                }
            }
        }

        is AuthScreen.OktaVerify -> {
            context(
                AuthenticatorNavContext(
                    backToSignIn = {
                        onReset()
                        onResetNav(state.username)
                    },
                    verifyWithSomethingElse = { onSelectAuthenticator(state.username, state.mfaRequired) }
                )
            ) {
                ChallengeScreen(
                    title = "Get a push notification",
                    buttonText = "Send push",
                    username = state.username,
                    onChallenge = {
                        if (state.mfaRequired != null) {
                            onResume("", AuthMethod.Mfa.OktaVerify, state.mfaRequired)
                        } else {
                            onSignIn(state.username, "", AuthMethod.Mfa.OktaVerify)
                        }
                    }
                )
            }
        }

        is AuthScreen.Otp -> {
            context(
                AuthenticatorNavContext(
                    backToSignIn = {
                        onReset()
                        onResetNav(state.username)
                    },
                    verifyWithSomethingElse = { onSelectAuthenticator(state.username, state.mfaRequired) }
                )
            ) {
                CodeEntryScreen(username = state.username) { code ->
                    if (state.mfaRequired != null) {
                        onResume(code, AuthMethod.Mfa.Otp, state.mfaRequired)
                    } else {
                        onSignIn(state.username, code, AuthMethod.Mfa.Otp)
                    }
                }
            }
        }

        is AuthScreen.Passkeys -> {
            context(
                AuthenticatorNavContext(
                    backToSignIn = {
                        onReset()
                        onResetNav(state.username)
                    },
                    verifyWithSomethingElse = { onSelectAuthenticator(state.username, state.mfaRequired) }
                )
            ) {
                PasskeysScreen(
                    username = state.username,
                    onStartCeremony = {
                        if (state.mfaRequired != null) {
                            onResume("", Mfa.Passkeys, state.mfaRequired)
                        } else {
                            onSignIn(state.username, "", Mfa.Passkeys)
                        }
                    }
                )
            }
        }

        is AuthScreen.Sms -> {
            context(
                AuthenticatorNavContext(
                    backToSignIn = { onResetNav(state.username) },
                    verifyWithSomethingElse = { onSelectAuthenticator(state.username, state.mfaRequired) }
                )
            ) {
                if (state.codeSent) {
                    CodeEntryScreen(username = state.username) { code ->
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
        }

        is AuthScreen.Voice -> {
            context(
                AuthenticatorNavContext(
                    backToSignIn = { onResetNav(state.username) },
                    verifyWithSomethingElse = { onSelectAuthenticator(state.username, state.mfaRequired) }
                )
            ) {
                if (state.codeSent) {
                    CodeEntryScreen(username = state.username) { code ->
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

            context(
                AuthenticatorNavContext(backToSignIn = {
                    onReset()
                    onResetNav(state.username)
                })
            ) {
                SelectAuthenticatorScreen(
                    username = state.username,
                    supportedAuthenticators = authMethods,
                    onSelectAuthenticator = { username, mfaMethod ->
                        onAuthenticatorSelect(username, mfaMethod, state.mfaRequired)
                    }
                )
            }
        }
    }
}
