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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.okta.directauth.app.ui.theme.Dimens

/**
 * Screen for the Session Token flow.
 *
 * Allows the user to enter a pre-obtained session token (from the Okta Authn API
 * or another source) and exchange it for OAuth2 tokens via a server-side redirect.
 *
 * @param onStartFlow callback invoked with the session token
 * @param onBack callback to navigate back to the home menu
 */
@Composable
fun SessionTokenScreen(
    onStartFlow: (sessionToken: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sessionToken by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Session Token Flow",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))
        Text(
            text = "Exchange a session token for OAuth2 tokens. Obtain a session token from the Okta Authn API first.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.spaceLarge))

        Text(
            text = "Session Token",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
        OutlinedTextField(
            value = sessionToken,
            onValueChange = { sessionToken = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )
        Spacer(modifier = Modifier.height(Dimens.spaceLarge))

        Button(
            onClick = { onStartFlow(sessionToken) },
            modifier = Modifier.fillMaxWidth(),
            enabled = sessionToken.isNotBlank()
        ) {
            Text(text = "Start Session Token Flow")
        }
        Spacer(modifier = Modifier.height(Dimens.spaceSmall))

        TextButton(onClick = onBack) {
            Text(text = "Back to Home")
        }
    }
}
