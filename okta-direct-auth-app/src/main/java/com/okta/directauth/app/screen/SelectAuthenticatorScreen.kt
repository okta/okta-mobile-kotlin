package com.okta.directauth.app.screen

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.okta.directauth.app.R
import com.okta.directauth.app.model.AuthMethod
import com.okta.directauth.app.ui.theme.Dimens
import com.okta.directauth.app.ui.theme.DirectAuthAppTheme

private const val TAG = "SelectAuthenticatorScreen"

/**
 * Generic authenticator selection screen for choosing an authentication method.
 *
 * This screen displays a list of available authentication methods and allows the user
 * to select one to proceed with authentication. It's used in two scenarios:
 * 1. Initial authenticator selection (after username entry)
 * 2. MFA method selection (when multiple MFA methods are available)
 *
 * The screen displays:
 * - An app logo and header
 * - Username display
 * - Instructions ("Select from the following options:")
 * - A list of available authentication methods with "Select" buttons
 * - A "Back to sign in" link to return to the username screen
 *
 * Features:
 * - Generic design: Works with any AuthMethod type (AuthMethod or AuthMethod.Mfa)
 * - Type-safe: Uses Kotlin generics to ensure compile-time type safety
 * - Reusable: Single implementation serves both initial auth and MFA selection
 *
 * @param T The type of AuthMethod (must extend AuthMethod)
 * @param username The username being authenticated. Displayed on screen.
 * @param supportedAuthenticators List of available authentication methods to choose from.
 * @param onBackToSignIn Callback invoked when user wants to return to username entry.
 * @param onAuthenticatorSelected Callback invoked when user selects an authentication method.
 *                                Parameters: (username: String, selectedMethod: T)
 */
@Composable
fun <T : AuthMethod> SelectAuthenticatorScreen(
    username: String,
    supportedAuthenticators: List<T>,
    onBackToSignIn: () -> Unit,
    onAuthenticatorSelected: (String, T) -> Unit
) {
    LaunchedEffect(Unit) {
        val methods = supportedAuthenticators.joinToString(", ") { it.label }
        Log.i(TAG, "SelectAuthenticatorScreen displayed - username: $username, methods: [$methods]")
    }

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(id = R.string.app_name),
            modifier = Modifier.size(Dimens.logoSize)
        )
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))

        Text(
            text = "Verify it's you with a security method",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = username,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Select from the following options:",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            supportedAuthenticators.forEach { method ->
                Row(
                    modifier = Modifier.fillMaxWidth(0.8f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = method.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = {
                        onAuthenticatorSelected(username, method)
                    }) {
                        Text("Select")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Back to sign in",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBackToSignIn),
            textAlign = TextAlign.Start,
        )
    }
}

@PreviewLightDark
@Composable
fun SelectAuthenticatorScreenPreview() {
    DirectAuthAppTheme {
        SelectAuthenticatorScreen(
            username = "test.user@example.com",
            supportedAuthenticators = listOf(AuthMethod.Password, AuthMethod.Mfa.OktaVerify),
            onBackToSignIn = {},
            onAuthenticatorSelected = { _, _ -> }
        )
    }
}
