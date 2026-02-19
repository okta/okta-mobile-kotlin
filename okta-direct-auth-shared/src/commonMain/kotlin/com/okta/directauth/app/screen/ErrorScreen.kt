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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.okta.directauth.app.ui.theme.DirectAuthAppTheme
import com.okta.directauth.app.util.AppLogger
import com.okta.directauth.model.DirectAuthenticationError
import java.io.PrintWriter
import java.io.StringWriter

private const val TAG = "ErrorScreen"

@Composable
fun ErrorScreen(
    error: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        val scrollState = rememberScrollState()
        AppLogger.write(TAG, error)
        Text(
            text = error,
            fontFamily = FontFamily.Monospace,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1.0f)
                    .padding(16.dp)
                    .verticalScroll(scrollState)
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(onClick = onBack) {
                Text(text = "Go Back")
            }
        }
    }
}

@Preview
@Composable
private fun ErrorScreenPreview() {
    DirectAuthAppTheme {
        ErrorScreen(
            error = "Http API Error\nError Code: E0000011\nError Summary: Invalid token provided",
            onBack = {}
        )
    }
}

internal fun DirectAuthenticationError.asString(): String =
    when (this) {
        is DirectAuthenticationError.HttpError.ApiError -> {
            """ 
            Http API Error
            |Error Id: $errorId
            |Error Summary: $errorSummary
            |Error Code: $errorCode
            |Error Link: $errorLink
            |Error Causes: $errorCauses
            """.trimIndent()
        }

        is DirectAuthenticationError.HttpError.Oauth2Error -> {
            """ 
            OAuth2Error
            |Error: $error
            |Error Description: $errorDescription
            """.trimIndent()
        }

        is DirectAuthenticationError.InternalError -> {
            val stringWriter = StringWriter()
            throwable.printStackTrace(PrintWriter(stringWriter))
            stringWriter.toString()
        }
    }
