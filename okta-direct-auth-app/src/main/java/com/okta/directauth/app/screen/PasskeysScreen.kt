package com.okta.directauth.app.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
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
        verifyWithSomethingElse = verifyWithSomethingElse,
    ) {
        // TODO(implement passkey flow)
    }
}

@PreviewLightDark
@Composable
fun PasskeysScreenPreview() {
    DirectAuthAppTheme {
        PasskeysScreen(
            username = "test.user@example.com",
            backToSignIn = {},
            verifyWithSomethingElse = {},
        )
    }
}