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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.okta.directauth.app.platform.AppStrings
import com.okta.directauth.app.platform.appLogoPainter
import com.okta.directauth.app.ui.theme.Dimens
import com.okta.directauth.app.ui.theme.DirectAuthAppTheme
import com.okta.directauth.app.util.AppLogger
import com.okta.directauth.app.util.BLANK_FIELD_ERROR
import com.okta.directauth.app.util.validateNotBlank

private const val TAG = "UsernameScreen"

/**
 * The initial sign-in screen where users enter their username.
 *
 * This is the entry point of the authentication flow. It displays:
 * - An app logo and "Sign In" header
 * - A username text field with validation
 * - A "Remember Me" checkbox to save the username for future sessions
 * - A "Next" button to proceed to the next step
 *
 * Features:
 * - Validates that the username field is not blank before proceeding
 * - Remembers the username across app sessions if "Remember Me" is checked
 * - Automatically populates the username field if a saved username exists
 * - Supports keyboard "Next" action for quick navigation
 *
 * @param savedUsername The previously saved username, if any. Pre-fills the username field.
 * @param onNext Callback invoked when the user submits a valid username.
 *               Parameters: (username: String, rememberMe: Boolean)
 */
@Composable
fun UsernameScreen(
    savedUsername: String?,
    onNext: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        AppLogger.write(TAG, "UsernameScreen displayed - savedUsername: ${savedUsername?.let { "present" } ?: "none"}")
    }

    var username by remember(savedUsername) { mutableStateOf(savedUsername ?: "") }
    var rememberMe by remember(savedUsername) { mutableStateOf(savedUsername != null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = appLogoPainter(),
            contentDescription = AppStrings.APP_NAME,
            modifier = Modifier.size(Dimens.logoSize)
        )
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))
        Text(
            text = "Sign In",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))
        Text(
            text = "Username",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                usernameError = null
            },
            modifier = Modifier.fillMaxWidth(),
            isError = usernameError != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions =
                KeyboardActions(
                    onNext = {
                        username.validateNotBlank(
                            onError = { usernameError = BLANK_FIELD_ERROR },
                            onSuccess = {
                                focusManager.clearFocus()
                                onNext(it, rememberMe)
                            }
                        )
                    }
                )
        )
        usernameError?.let {
            Text(
                modifier = Modifier.align(Alignment.Start),
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(
            modifier =
                Modifier
                    .align(Alignment.Start)
                    .offset(x = (-13).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = rememberMe,
                onCheckedChange = { rememberMe = it }
            )
            Text(text = AppStrings.REMEMBER_ME)
        }
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))
        Button(
            onClick = {
                username.validateNotBlank(
                    onError = { usernameError = BLANK_FIELD_ERROR },
                    onSuccess = { onNext(it, rememberMe) }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Next")
        }
    }
}

@Preview
@Composable
private fun UsernameScreenPreview() {
    DirectAuthAppTheme {
        UsernameScreen(savedUsername = "", onNext = { _, _ -> })
    }
}
