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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.okta.directauth.app.ui.theme.DirectAuthAppTheme
import com.okta.directauth.app.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

private const val TAG = "OobPollingScreen"

/**
 * Out-of-Band (OOB) polling screen that waits for user action on another device.
 *
 * This screen is displayed during OOB authentication flows when the user needs to complete
 * an action on a different device or channel. The screen polls the server in the background
 * to check if the user has completed the required action.
 *
 * Use cases:
 * 1. **Push notification (Okta Verify)**: User receives push, approves on mobile device
 * 2. **SMS authentication**: User clicks link sent via SMS to verify
 * 3. **Voice call**: User answers call and follows voice prompts
 * 4. **Device transfer**: User verifies binding code and approves on another device
 *
 * The screen displays:
 * - A message explaining what the user should do (e.g., "Check your phone for a push notification")
 * - A binding code (optional) for device transfer scenarios
 * - A circular progress indicator showing polling is active
 * - A countdown timer showing when the request expires
 * - A "Cancel" button to abort the operation
 *
 * Features:
 * - Automatically starts polling when the screen is displayed
 * - Shows countdown timer that decrements every second until expiration
 * - Displays binding code in large text for device transfer scenarios
 * - Cancels polling job when user clicks "Cancel" or navigates away
 * - Continues polling until authentication succeeds, fails, or expires
 *
 * @param modifier Modifier for the root composable
 * @param message Instructions for the user (e.g., "Polling for OOB result...")
 * @param bindingCode Optional binding code to display for device transfer (e.g., "123456")
 * @param countdownSeconds Optional countdown timer in seconds before the request expires
 * @param pollAction Optional callback that initiates server polling. Called once when screen loads.
 *                   Returns a Job that can be cancelled when user aborts or navigates away.
 * @param onCancel Callback invoked when user clicks "Cancel" to abort the authentication.
 */
@Composable
fun OobPollingScreen(
    message: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    bindingCode: String? = null,
    countdownSeconds: Int? = null,
    pollAction: (() -> Job?)? = null,
) {
    var remainingTime by remember(countdownSeconds) { mutableIntStateOf(countdownSeconds ?: 0) }
    var pollingJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        AppLogger.write(TAG, "OobPollingScreen displayed - message: \"$message\", bindingCode: ${bindingCode ?: "none"}, countdown: ${countdownSeconds}s")
        pollingJob = pollAction?.invoke()
        if (countdownSeconds != null) {
            while (remainingTime > 0) {
                delay(1000)
                remainingTime--
            }
            AppLogger.write(TAG, "OOB polling countdown expired")
        }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(message)

            Spacer(modifier = Modifier.height(16.dp))

            if (bindingCode != null) {
                Text(
                    text = bindingCode,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            CircularProgressIndicator(modifier = Modifier.padding(16.dp))

            if (countdownSeconds != null) {
                Text("Expires in: $remainingTime seconds")
            }

            Button(
                onClick = {
                    pollingJob?.cancel()
                    onCancel()
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Cancel")
            }
        }
    }
}

@Preview
@Composable
private fun OobPollingScreenPreview() {
    DirectAuthAppTheme {
        OobPollingScreen(message = "Polling for result...", onCancel = {}, countdownSeconds = 60, pollAction = { null })
    }
}

@Preview
@Composable
private fun OobPollingScreenWithBindingCodePreview() {
    DirectAuthAppTheme {
        OobPollingScreen(
            message = "Please verify the code on your other device",
            onCancel = {},
            bindingCode = "123456",
            countdownSeconds = 60,
            pollAction = { null }
        )
    }
}
