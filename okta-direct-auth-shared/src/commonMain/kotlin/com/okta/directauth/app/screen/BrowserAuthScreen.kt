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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.okta.directauth.app.ui.theme.Dimens

/**
 * Screen for the Authorization Code + PKCE (browser) flow.
 *
 * Shows a "Sign In with Browser" button. When tapped, opens the system browser
 * for Okta authorization and waits for the redirect callback.
 *
 * @param onSignIn callback to initiate the browser authentication flow
 * @param onBack callback to navigate back to the home menu
 * @param isLoading whether the browser flow is currently in progress
 */
@Composable
fun BrowserAuthScreen(
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
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
            text = "Browser Sign In",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))
        Text(
            text = "Sign in using your browser via Authorization Code + PKCE flow.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.spaceLarge))

        if (isLoading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(Dimens.spaceMedium))
            Text(text = "Waiting for browser redirect...")
        } else {
            Button(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Sign In with Browser")
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spaceLarge))
        TextButton(onClick = onBack) {
            Text(text = "Back to Home")
        }
    }
}
