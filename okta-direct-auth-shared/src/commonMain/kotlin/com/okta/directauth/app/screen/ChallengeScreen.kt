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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import com.okta.directauth.app.ui.theme.DirectAuthAppTheme
import com.okta.directauth.app.util.AppLogger
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
 * @param onChallenge Callback invoked when the user clicks the challenge button.
 *                    Returns a Job that can be cancelled if the user navigates away.
 */
@Composable
context(nav: AuthenticatorNavContext)
fun ChallengeScreen(
    title: String,
    buttonText: String,
    username: String,
    onChallenge: () -> Job,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        AppLogger.write(TAG, "ChallengeScreen displayed - username: $username")
    }

    val focusManager = LocalFocusManager.current
    val jobState = rememberCancellableJob()

    context(
        AuthenticatorNavContext(
            backToSignIn = {
                jobState.cancel()
                nav.backToSignIn()
            },
            verifyWithSomethingElse =
                nav.verifyWithSomethingElse?.let {
                    {
                        jobState.cancel()
                        it()
                    }
                }
        )
    ) {
        AuthenticatorScreenScaffold(title = title, username = username) {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    jobState.addJob(onChallenge())
                },
                enabled = !jobState.isActive,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = buttonText)
            }
        }
    }
}

@Preview
@Composable
private fun ChallengeScreenPreview() {
    DirectAuthAppTheme {
        context(AuthenticatorNavContext(backToSignIn = {}, verifyWithSomethingElse = {})) {
            ChallengeScreen(
                title = "Get a push notification",
                buttonText = "Send push",
                username = "test.user@example.com",
                onChallenge = { Job().apply { complete() } }
            )
        }
    }
}
