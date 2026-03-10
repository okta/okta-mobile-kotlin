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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.okta.directauth.app.ui.theme.DirectAuthAppTheme

@Composable
fun PasskeysScreen(
    username: String,
    backToSignIn: () -> Unit,
    verifyWithSomethingElse: () -> Unit,
    onStartCeremony: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnStartCeremony by rememberUpdatedState(onStartCeremony)
    var ceremonyStarted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!ceremonyStarted) {
            ceremonyStarted = true
            currentOnStartCeremony()
        }
    }

    AuthenticatorScreenScaffold(
        title = "Passkeys",
        username = username,
        backToSignIn = backToSignIn,
        verifyWithSomethingElse = verifyWithSomethingElse,
        modifier = modifier
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Waiting for passkey authentication...")
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                ceremonyStarted = true
                onStartCeremony()
            }
        ) {
            Text("Retry")
        }
    }
}

@Preview
@Composable
private fun PasskeysScreenPreview() {
    DirectAuthAppTheme {
        PasskeysScreen(
            username = "test.user@example.com",
            backToSignIn = {},
            verifyWithSomethingElse = {},
            onStartCeremony = {}
        )
    }
}
