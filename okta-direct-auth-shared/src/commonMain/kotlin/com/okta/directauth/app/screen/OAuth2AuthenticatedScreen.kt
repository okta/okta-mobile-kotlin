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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.okta.authfoundation.client.TokenInfo
import com.okta.directauth.app.util.AppLogger
import com.okta.directauth.app.viewModel.OAuth2FlowViewModel
import io.jsonwebtoken.Jwts

/**
 * Displays the token claims from a successful OAuth2 flow.
 *
 * Parses the ID token JWT to extract and display common claims (iss, sub, name, email).
 * Also shows the access token and optional refresh token / device secret.
 *
 * @param tokenInfo the token response from the completed OAuth2 flow
 * @param onBackToHome callback to navigate back to the home menu
 */
@Composable
fun OAuth2AuthenticatedScreen(
    tokenInfo: TokenInfo,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayText = buildTokenDisplayText(tokenInfo)

    Column(modifier = Modifier.padding(16.dp)) {
        LazyColumn(
            modifier =
                Modifier
                    .padding(16.dp)
                    .weight(1f)
        ) {
            items(1) {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(displayText)
                    }
                }
            }
        }

        Button(
            onClick = onBackToHome,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Back to Home")
        }
    }
}

private fun buildTokenDisplayText(tokenInfo: TokenInfo): String {
    val idToken = tokenInfo.idToken
    val claimsText =
        if (idToken.isNullOrBlank()) {
            "No ID token present (openid scope may not have been included)"
        } else {
            runCatching {
                val (header, body, _) = idToken.split(".")
                val urlSafeNoPad =
                    kotlin.io.encoding.Base64.UrlSafe
                        .withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT)
                val unsecureHeader =
                    urlSafeNoPad
                        .decode(header)
                        .decodeToString()
                        .replace("RS256", "none")
                        .replace("ES256", "none")
                val idTokenJwt = urlSafeNoPad.encode(unsecureHeader.toByteArray()) + "." + body + "."
                val payload =
                    Jwts
                        .parser()
                        .unsecured()
                        .build()
                        .parseUnsecuredClaims(idTokenJwt)
                        .payload

                buildString {
                    appendLine("=== ID Token Claims ===")
                    appendLine("iss: ${payload.get("iss", String::class.java)}")
                    appendLine("sub: ${payload.get("sub", String::class.java)}")
                    appendLine("name: ${payload.get("name", String::class.java)}")
                    appendLine("email: ${payload.get("email", String::class.java)}")
                }
            }.getOrElse { "Failed to parse ID token: ${it.message}" }
        }

    return buildString {
        append(claimsText)
        appendLine()
        appendLine("=== Token Info ===")
        appendLine("Token Type: ${tokenInfo.tokenType}")
        appendLine("Expires In: ${tokenInfo.expiresIn}s")
        appendLine("Scope: ${tokenInfo.scope}")
        appendLine("Access Token: ${tokenInfo.accessToken.take(20)}...")
        tokenInfo.refreshToken?.let { appendLine("Refresh Token: ${it.take(20)}...") }
        tokenInfo.idToken?.let { AppLogger.write("OAuth2AuthenticatedScreen", "ID Token: $it") }
        tokenInfo.deviceSecret?.let {
            appendLine("Device Secret: ${it.take(20)}...")
            AppLogger.write("OAuth2AuthenticatedScreen", "Device Secret: $it")
        }
    }
}
