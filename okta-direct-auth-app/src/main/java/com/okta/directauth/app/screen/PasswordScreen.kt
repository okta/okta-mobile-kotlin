package com.okta.directauth.app.screen

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.okta.directauth.app.R
import com.okta.directauth.app.ui.theme.DirectAuthAppTheme
import com.okta.directauth.app.util.BLANK_FIELD_ERROR
import com.okta.directauth.app.util.rememberCancellableJob
import com.okta.directauth.app.util.validateNotBlank
import kotlinx.coroutines.Job

private const val TAG = "PasswordScreen"

/**
 * Password authentication screen where users enter their password to sign in.
 *
 * This screen is displayed when password authentication is selected or required.
 * It provides a secure password entry field with visibility toggle and validation.
 *
 * The screen displays:
 * - A "Verify with your password" title
 * - Username display
 * - A password text field with show/hide toggle icon
 * - A "Forgot password" link
 * - A "Verify with something else" link to choose a different authentication method
 * - A "Next" button to submit the password
 * - A "Back to sign in" link to return to username entry
 *
 * Features:
 * - Password visibility toggle (show/hide password)
 * - Validates that the password field is not blank before submitting
 * - Disables UI during password verification to prevent multiple submissions
 * - Automatically cancels ongoing verification if user navigates away
 * - Supports keyboard "Done" action for quick submission
 *
 * @param username The username being authenticated. Displayed on screen.
 * @param backToSignIn Callback to return to the username entry screen.
 * @param verifyWithSomethingElse Callback to select a different authentication method.
 * @param forgotPassword Callback invoked when user clicks "Forgot password" link.
 * @param next Callback invoked when user submits a valid password.
 *             Returns a Job that can be cancelled if the user navigates away.
 *             Parameter: (password: String)
 */
@Composable
fun PasswordScreen(
    username: String,
    backToSignIn: () -> Unit,
    verifyWithSomethingElse: () -> Unit,
    forgotPassword: () -> Unit,
    next: (String) -> Job
) {
    LaunchedEffect(Unit) {
        Log.i(TAG, "PasswordScreen displayed - username: $username")
    }

    var password by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val jobState = rememberCancellableJob()

    AuthenticatorScreenScaffold(
        title = "Verify with your password",
        username = username,
        backToSignIn = {
            jobState.cancel()
            backToSignIn()
        },
        verifyWithSomethingElse = {
            jobState.cancel()
            verifyWithSomethingElse()
        },
        forgotPassword = {
            Text(
                text = stringResource(id = R.string.forgot_password),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(
                        enabled = !jobState.isActive,
                        onClick = forgotPassword,
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "Password",
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                isError = showError,
                enabled = !jobState.isActive,
                trailingIcon = {
                    val image = if (passwordVisible)
                        Icons.Filled.Visibility
                    else Icons.Filled.VisibilityOff

                    val description = if (passwordVisible) "Hide password" else "Show password"

                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        enabled = !jobState.isActive,
                    ) {
                        Icon(imageVector = image, description)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        password.validateNotBlank(
                            onError = { showError = true },
                            onSuccess = {
                                focusManager.clearFocus()
                                jobState.addJob(next(it))
                            }
                        )
                    }
                )
            )
            if (showError) {
                Text(BLANK_FIELD_ERROR, color = Color.Red)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                password.validateNotBlank(
                    onError = { showError = true },
                    onSuccess = { jobState.addJob(next(it)) }
                )
            },
            enabled = !jobState.isActive,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Next")
        }
    }
}

@PreviewLightDark
@Composable
fun PasswordScreenPreview() {
    DirectAuthAppTheme {
        PasswordScreen(
            username = "user1@okta.com",
            backToSignIn = {},
            verifyWithSomethingElse = {},
            forgotPassword = {},
            next = { Job().apply { complete() }}
        )
    }
}
