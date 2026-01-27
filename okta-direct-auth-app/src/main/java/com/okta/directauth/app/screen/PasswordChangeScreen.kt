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

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.okta.directauth.app.ui.theme.DirectAuthAppTheme
import com.okta.directauth.app.util.BLANK_FIELD_ERROR
import com.okta.directauth.app.util.rememberCancellableJob
import com.okta.directauth.app.viewModel.MainViewModel
import kotlinx.coroutines.Job

private const val TAG = "PasswordChangeScreen"
private const val PASSWORD_MISMATCH_ERROR = "Passwords do not match"

/**
 * Password change screen for self-service password recovery (SSPR) flow.
 *
 * This screen is displayed after the user has authenticated with the okta.myAccount.password.manage
 * scope. It allows the user to set a new password by entering it twice for verification.
 *
 * The screen displays:
 * - A "Change your password" title
 * - Username display
 * - New password text field with show/hide toggle icon
 * - Confirm password text field with show/hide toggle icon
 * - A "Change Password" button to submit
 * - A "Back to sign in" link to cancel and return to username entry
 *
 * Features:
 * - Password visibility toggle for both fields (show/hide password)
 * - Validates that both password fields are not blank
 * - Validates that both passwords match before submitting
 * - Disables UI during password change request to prevent multiple submissions
 * - Automatically cancels ongoing request if user navigates away
 * - Supports keyboard "Done" action for quick submission
 *
 * @param username The username being authenticated. Displayed on screen.
 * @param backToSignIn Callback to return to the username entry screen.
 * @param onSuccess Callback invoked when password change succeeds.
 * @param next Callback invoked when user submits valid passwords.
 *             Returns a Job that can be cancelled if the user navigates away.
 *             Parameter: (newPassword: String)
 */
@Composable
fun PasswordChangeScreen(
    username: String,
    backToSignIn: () -> Unit,
    passwordChangeResult: MainViewModel.PasswordChangeResult,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    next: (String) -> Job,
) {
    LaunchedEffect(Unit) {
        Log.i(TAG, "PasswordChangeScreen displayed - username: $username")
    }

    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newPasswordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val jobState = rememberCancellableJob()

    /**
     * Validates both password fields and submits if valid.
     */
    fun validateAndSubmit() {
        var isValid = true

        // Validate new password
        if (newPassword.isBlank()) {
            newPasswordError = BLANK_FIELD_ERROR
            isValid = false
        }

        // Validate confirm password
        if (confirmPassword.isBlank()) {
            confirmPasswordError = BLANK_FIELD_ERROR
            isValid = false
        }

        // Check if passwords match
        if (isValid && newPassword != confirmPassword) {
            confirmPasswordError = PASSWORD_MISMATCH_ERROR
            isValid = false
        }

        if (isValid) {
            focusManager.clearFocus()
            jobState.addJob(next(newPassword))
        }
    }

    AuthenticatorScreenScaffold(
        title = "Change your password",
        username = username,
        backToSignIn = {
            jobState.cancel()
            backToSignIn()
        },
        verifyWithSomethingElse = null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            // New Password Field
            Text(
                text = "New Password",
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = {
                    newPassword = it
                    newPasswordError = null
                },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                isError = newPasswordError != null,
                enabled = !jobState.isActive,
                trailingIcon = {
                    val image =
                        if (newPasswordVisible) {
                            Icons.Filled.Visibility
                        } else {
                            Icons.Filled.VisibilityOff
                        }

                    val description = if (newPasswordVisible) "Hide password" else "Show password"

                    IconButton(
                        onClick = { newPasswordVisible = !newPasswordVisible },
                        enabled = !jobState.isActive
                    ) {
                        Icon(imageVector = image, description)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            if (newPasswordError != null) {
                Text(
                    text = newPasswordError!!,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Confirm Password Field
            Text(
                text = "Confirm New Password",
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    confirmPasswordError = null
                },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                isError = confirmPasswordError != null,
                enabled = !jobState.isActive,
                trailingIcon = {
                    val image =
                        if (confirmPasswordVisible) {
                            Icons.Filled.Visibility
                        } else {
                            Icons.Filled.VisibilityOff
                        }

                    val description = if (confirmPasswordVisible) "Hide password" else "Show password"

                    IconButton(
                        onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                        enabled = !jobState.isActive
                    ) {
                        Icon(imageVector = image, description)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(
                        onDone = { validateAndSubmit() }
                    )
            )
            if (confirmPasswordError != null) {
                Text(
                    text = confirmPasswordError!!,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { validateAndSubmit() },
            enabled = !jobState.isActive,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Change Password")
        }
    }

    // Show error dialog if present
    when (passwordChangeResult) {
        is MainViewModel.PasswordChangeResult.Error -> {
            PasswordChangeErrorDialog(
                error = passwordChangeResult,
                onDismiss = onDismissError
            )
        }

        else -> {
            Unit
        } // No error dialog to show
    }
}

/**
 * Error dialog composable that displays detailed error information from password change failures.
 *
 * @param error The error state containing either OktaApiError or NetworkError
 * @param onDismiss Callback invoked when user dismisses the dialog
 */
@Composable
fun PasswordChangeErrorDialog(
    error: MainViewModel.PasswordChangeResult.Error,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Password Change Failed",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                when (error) {
                    is MainViewModel.PasswordChangeResult.Error.OktaApiError -> {
                        val oktaError = error.oktaErrorResponse

                        // Error Code
                        Text(
                            text = "Error Code:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = oktaError.errorCode,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        // Error Summary
                        oktaError.errorSummary?.let { summary ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Summary:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Error ID
                        oktaError.errorId?.let { id ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Error ID:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = id,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Error Link
                        oktaError.errorLink?.let { link ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "More Info:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = link,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Error Causes
                        oktaError.errorCauses?.takeIf { it.isNotEmpty() }?.let { causes ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Causes:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            causes.forEach { cause ->
                                Text(
                                    text = "• ${cause.errorSummary}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                                )
                            }
                        }

                        // Status Code
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "HTTP Status:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = error.statusCode.toString(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    is MainViewModel.PasswordChangeResult.Error.NetworkError -> {
                        Text(
                            text = error.message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

@PreviewLightDark
@Composable
private fun PasswordChangeScreenPreview() {
    DirectAuthAppTheme {
        PasswordChangeScreen(
            username = "user1@okta.com",
            backToSignIn = {},
            passwordChangeResult = MainViewModel.PasswordChangeResult.Idle,
            onDismissError = {},
            next = { Job().apply { complete() } }
        )
    }
}

@PreviewLightDark
@Composable
private fun PasswordChangeErrorDialogPreview() {
    DirectAuthAppTheme {
        PasswordChangeErrorDialog(
            MainViewModel.PasswordChangeResult.Error.NetworkError("network error"),
            onDismiss = {}
        )
    }
}
