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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.okta.directauth.app.model.OAuth2FlowState
import com.okta.directauth.app.ui.theme.Dimens

/**
 * Screen for the Device Authorization grant flow.
 *
 * Displays three states:
 * - **Idle**: Shows a "Start Device Authorization" button.
 * - **Loading**: Shows a progress indicator while requesting the device code.
 * - **Polling**: Displays the user code and verification URI, with instructions and a cancel button.
 *
 * @param flowState the current OAuth2 flow state
 * @param onStart callback to initiate the device authorization flow
 * @param onCancel callback to cancel and return to the home menu
 */
@Composable
fun DeviceAuthorizationScreen(
    flowState: OAuth2FlowState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Device Authorization Flow",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.spaceLarge))

        when (flowState) {
            is OAuth2FlowState.Idle -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Start Device Authorization")
                }
            }

            is OAuth2FlowState.Loading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(Dimens.spaceMedium))
                Text(text = "Requesting device code...")
            }

            is OAuth2FlowState.DeviceAuthPolling -> {
                DeviceCodePollingContent(
                    userCode = flowState.userCode,
                    verificationUri = flowState.verificationUri,
                    expiresIn = flowState.expiresIn
                )
            }

            else -> {
                Unit
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spaceLarge))
        TextButton(onClick = onCancel) {
            Text(text = "Cancel")
        }
    }
}

@Composable
private fun DeviceCodePollingContent(
    userCode: String,
    verificationUri: String,
    expiresIn: Int,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Visit the URL below and enter the code:",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Dimens.spaceMedium))
            Text(
                text = verificationUri,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Dimens.spaceLarge))
            Text(
                text = userCode,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Dimens.spaceMedium))
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(Dimens.spaceSmall))
            Text(
                text = "Waiting for approval... (expires in ${expiresIn}s)",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
