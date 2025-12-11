package com.okta.directauth.app.screen

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.okta.directauth.app.ui.theme.DirectAuthAppTheme
import com.okta.directauth.app.util.rememberCancellableJob
import kotlinx.coroutines.Job

private const val TAG = "ChallengeScreen"

/**
 * A generic challenge screen for MFA authentication.
 *
 * This screen is displayed when the user chooses an MFA method that requires a challenge, such as
 * Okta Verify push notifications, SMS, or voice calls. The user can trigger the challenge and then
 * proceed to the next step of authentication.
 *
 * The screen displays:
 * - A title describing the challenge
 * - Username display
 * - A button to trigger the challenge
 * - A "Verify with something else" link to choose a different authentication method
 * - A "Back to sign in" link to return to username entry
 *
 * Features:
 * - Initiates the challenge when the user clicks the button
 * - Disables UI during processing to prevent multiple requests
 * - Automatically cancels the ongoing operation if the user navigates away
 *
 * @param title The title text to display on the screen.
 * @param buttonText The text to display on the challenge button.
 * @param username The username being authenticated. Displayed on screen.
 * @param backToSignIn Callback to return to the username entry screen.
 * @param verifyWithSomethingElse Callback to select a different authentication method.
 * @param onChallenge Callback invoked when the user clicks the challenge button.
 *                    Returns a Job that can be cancelled if the user navigates away.
 */
@Composable
fun ChallengeScreen(
    title: String,
    buttonText: String,
    username: String,
    backToSignIn: () -> Unit,
    verifyWithSomethingElse: () -> Unit,
    onChallenge: () -> Job,
) {
    LaunchedEffect(Unit) {
        Log.i(TAG, "ChallengeScreen displayed - username: $username")
    }

    val focusManager = LocalFocusManager.current
    val jobState = rememberCancellableJob()

    AuthenticatorScreenScaffold(
        title = title,
        username = username,
        backToSignIn = {
            jobState.cancel()
            backToSignIn()
        },
        verifyWithSomethingElse = {
            jobState.cancel()
            verifyWithSomethingElse()
        },
    ) {
        Button(
            onClick = {
                focusManager.clearFocus()
                jobState.addJob(onChallenge())
            },
            enabled = !jobState.isActive,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = buttonText)
        }
    }
}

@PreviewLightDark
@Composable
private fun ChallengeScreenPreview() {
    DirectAuthAppTheme {
        ChallengeScreen(
            title = "Get a push notification",
            buttonText = "Send push",
            username = "test.user@example.com",
            backToSignIn = {},
            verifyWithSomethingElse = {},
            onChallenge = { Job().apply { complete() } },
        )
    }
}