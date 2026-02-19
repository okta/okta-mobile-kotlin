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

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.okta.directauth.app.ui.theme.DirectAuthAppTheme

@Composable
fun PasskeysScreen(
    username: String,
    backToSignIn: () -> Unit,
    verifyWithSomethingElse: () -> Unit,
) {
    AuthenticatorScreenScaffold(
        title = "Passkeys is not yet implemented",
        username = username,
        backToSignIn = backToSignIn,
        verifyWithSomethingElse = verifyWithSomethingElse
    ) {
        // TODO(implement passkey flow)
    }
}

@Preview
@Composable
private fun PasskeysScreenPreview() {
    DirectAuthAppTheme {
        PasskeysScreen(
            username = "test.user@example.com",
            backToSignIn = {},
            verifyWithSomethingElse = {}
        )
    }
}
