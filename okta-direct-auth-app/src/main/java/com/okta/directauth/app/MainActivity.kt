package com.okta.directauth.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.okta.directauth.app.model.AuthMethod
import com.okta.directauth.app.screen.AuthenticatedScreen
import com.okta.directauth.app.screen.ErrorScreen
import com.okta.directauth.app.screen.MfaRequiredScreen
import com.okta.directauth.app.screen.OobPollingScreen
import com.okta.directauth.app.screen.PromptForCodeScreen
import com.okta.directauth.app.screen.SignInScreen
import com.okta.directauth.app.screen.asString
import com.okta.directauth.app.viewModel.MainViewModel
import com.okta.directauth.model.Continuation
import com.okta.directauth.model.DirectAuthenticationError
import com.okta.directauth.model.DirectAuthenticationState

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val state by viewModel.directAuth.authenticationState.collectAsState()
            var displayedState by remember { mutableStateOf(state) }

            LaunchedEffect(state) {
                if (state !is DirectAuthenticationState.AuthorizationPending) {
                    displayedState = state
                }
            }

            Box {
                Crossfade(targetState = displayedState) { currentState ->
                    when (currentState) {
                        is Continuation.OobPending -> OobPollingScreen(
                            message = "Polling for OOB result...",
                            countdownSeconds = currentState.expirationInSeconds,
                            pollAction = { viewModel.pendingOob(currentState) },
                            onCancel = viewModel::reset
                        )

                        is Continuation.Prompt -> PromptForCodeScreen { code ->
                            viewModel.prompt(currentState, code)
                        }
                        is Continuation.Transfer -> OobPollingScreen(
                            message = "Please verify the code on your other device: ${currentState.bindingCode}",
                            countdownSeconds = currentState.expirationInSeconds,
                            pollAction = { viewModel.transfer(currentState) },
                            onCancel = viewModel::reset
                        )
                        is Continuation.WebAuthn -> TODO()
                        is DirectAuthenticationError -> ErrorScreen(onBack = viewModel::reset, error = currentState.asString())

                        is DirectAuthenticationState.Authenticated -> AuthenticatedScreen(
                            token = currentState.token.idToken,
                            onSignOut = viewModel::reset
                        )
                        DirectAuthenticationState.Canceled, DirectAuthenticationState.Idle -> SignInScreen { username, password, authMethod ->
                            viewModel.signIn(username, password, authMethod)
                        }

                        is DirectAuthenticationState.MfaRequired -> MfaRequiredScreen { otp, mfaMethod ->
                            viewModel.resume(otp, mfaMethod, currentState)
                        }

                        else -> {
                            // Do nothing for states that shouldn't update the UI, such as AuthorizationPending
                        }
                    }
                }
            }
        }
    }
}
