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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
 * Screen for the Token Exchange (Native SSO) flow.
 *
 * Allows the user to enter an existing ID token and device secret, then exchanges
 * them for new OAuth2 tokens.
 *
 * @param onStartExchange callback invoked with ID token and device secret
 * @param onBack callback to navigate back to the home menu
 */
@Composable
fun TokenExchangeScreen(
    onStartExchange: (idToken: String, deviceSecret: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var idToken by remember { mutableStateOf("") }
    var deviceSecret by remember { mutableStateOf("") }

    Column(
        modifier =
            Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Token Exchange Flow",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))
        Text(
            text = "Exchange an existing ID token and device secret for new tokens (Native SSO).",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.spaceLarge))

        Text(
            text = "ID Token",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
        OutlinedTextField(
            value = idToken,
            onValueChange = { idToken = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5
        )
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))

        Text(
            text = "Device Secret",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
        OutlinedTextField(
            value = deviceSecret,
            onValueChange = { deviceSecret = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 3
        )
        Spacer(modifier = Modifier.height(Dimens.spaceLarge))

        Button(
            onClick = { onStartExchange(idToken, deviceSecret) },
            modifier = Modifier.fillMaxWidth(),
            enabled = idToken.isNotBlank() && deviceSecret.isNotBlank()
        ) {
            Text(text = "Start Token Exchange")
        }
        Spacer(modifier = Modifier.height(Dimens.spaceSmall))

        TextButton(onClick = onBack) {
            Text(text = "Back to Home")
        }
    }
}
