package com.okta.directauth.app

import android.os.Bundle
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
import com.okta.directauth.app.model.AuthScreen
import com.okta.directauth.app.screen.AuthenticatedScreen
import com.okta.directauth.app.screen.AuthenticationFlow
import com.okta.directauth.app.screen.CodeEntryScreen
import com.okta.directauth.app.screen.ErrorScreen
import com.okta.directauth.app.screen.OobPollingScreen
import com.okta.directauth.app.screen.asString
import com.okta.directauth.app.ui.theme.DirectAuthAppTheme
import com.okta.directauth.app.viewModel.AuthenticationFlowViewModel
import com.okta.directauth.app.viewModel.MainViewModel
import com.okta.directauth.model.Continuation
import com.okta.directauth.model.DirectAuthenticationError
import com.okta.directauth.model.DirectAuthenticationState

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val authenticationFlowViewModel: AuthenticationFlowViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DirectAuthAppTheme {
                val state by viewModel.directAuth.authenticationState.collectAsState()
                var displayedState by remember { mutableStateOf(state) }
                val authState by authenticationFlowViewModel.uiState.collectAsState()
                val selectAuthMethod by authenticationFlowViewModel.selectedAuthMethod.collectAsState()
                val username by authenticationFlowViewModel.username.collectAsState()

                BackHandler(enabled = authState !is AuthScreen.UsernameInput) {
                    authenticationFlowViewModel.goBack()
                }

                LaunchedEffect(state) {
                    if (state is DirectAuthenticationState.MfaRequired) {
                        authenticationFlowViewModel.setInitialState(
                            AuthScreen.MfaRequired(authenticationFlowViewModel.username.value, state as DirectAuthenticationState.MfaRequired)
                        )
                    }

                    if (state is Continuation.OobPending) {
                        authenticationFlowViewModel.codeSent()
                    }

                    if (state !is DirectAuthenticationState.AuthorizationPending) {
                        displayedState = state
                    }
                }

                Box(modifier = Modifier.statusBarsPadding()) {
                    Crossfade(targetState = displayedState) { currentState ->
                        /**
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
                            is Continuation.OobPending -> {
                                when (selectAuthMethod) {
                                    AuthMethod.Mfa.Sms, AuthMethod.Mfa.Voice -> {
                                        AuthenticationFlow(viewModel, authenticationFlowViewModel)
                                    }

                                    else -> {
                                        OobPollingScreen(
                                            message = "Polling for OOB result...",
                                            countdownSeconds = currentState.expirationInSeconds,
                                            pollAction = { viewModel.pendingOob(currentState) },
                                            onCancel = viewModel::reset
                                        )
                                    }
                                }
                            }

                            // Server requires additional input during authentication (e.g., OTP code)
                            is Continuation.Prompt -> CodeEntryScreen(
                                username = username,
                                backToSignIn = viewModel::reset,
                                verifyWithSomethingElse = null,
                            ) { code ->
                                viewModel.prompt(currentState, code)
                            }

                            // Device transfer authentication (e.g., authenticate via Okta Verify)
                            is Continuation.Transfer -> OobPollingScreen(
                                message = "Please verify the code on your other device",
                                bindingCode = currentState.bindingCode,
                                countdownSeconds = currentState.expirationInSeconds,
                                pollAction = { viewModel.transfer(currentState) },
                                onCancel = viewModel::reset
                            )

                            // Passkey/WebAuthn authentication (not yet implemented)
                            is Continuation.WebAuthn -> TODO()

                            // Authentication error occurred - display error message
                            is DirectAuthenticationError -> ErrorScreen(currentState.asString(), viewModel::reset)

                            // Successfully authenticated - display token and sign-out option
                            is DirectAuthenticationState.Authenticated -> AuthenticatedScreen(
                                token = currentState.token.idToken,
                                onSignOut = {
                                    viewModel.reset()
                                    authenticationFlowViewModel.setInitialState(AuthScreen.UsernameInput(""))
                                }
                            )

                            // Initial authentication flow states - username then select authenticator to use.
                            DirectAuthenticationState.Canceled,
                            DirectAuthenticationState.Idle,
                            is DirectAuthenticationState.MfaRequired -> AuthenticationFlow(viewModel, authenticationFlowViewModel)

                            else -> {
                                // Do nothing for states that shouldn't update the UI, such as AuthorizationPending
                            }
                        }
                    }
                }
            }
        }
    }
}
