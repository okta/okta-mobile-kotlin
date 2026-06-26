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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.okta.directauth.app.platform.AppStrings
import com.okta.directauth.app.platform.appLogoPainter
import com.okta.directauth.app.ui.theme.Dimens

/**
 * Home menu screen presenting all available authentication flow options.
 *
 * This is the app's entry point, allowing the user to choose between the existing
 * Direct Authentication flow and the OAuth2 flows.
 *
 * @param onDirectAuth navigate to the existing Direct Authentication flow
 * @param onResourceOwner navigate to the Resource Owner Password flow
 * @param onDeviceAuthorization navigate to the Device Authorization flow
 * @param onBrowserAuth navigate to the Browser Sign-In (Authorization Code + PKCE) flow
 * @param onTokenExchange navigate to the Token Exchange flow
 * @param onSessionToken navigate to the Session Token flow
 */
@Composable
fun HomeMenuScreen(
    onDirectAuth: () -> Unit,
    onResourceOwner: () -> Unit,
    onDeviceAuthorization: () -> Unit,
    onBrowserAuth: () -> Unit,
    onTokenExchange: () -> Unit,
    onSessionToken: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
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
            text = "Choose Authentication Flow",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.spaceLarge))

        Button(
            onClick = onDirectAuth,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Direct Authentication")
        }
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))

        Text(
            text = "OAuth2 Flows",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
        Spacer(modifier = Modifier.height(Dimens.spaceSmall))

        OutlinedButton(
            onClick = onResourceOwner,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Resource Owner Flow")
        }
        Spacer(modifier = Modifier.height(Dimens.spaceSmall))

        OutlinedButton(
            onClick = onDeviceAuthorization,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Device Authorization Flow")
        }
        Spacer(modifier = Modifier.height(Dimens.spaceSmall))

        OutlinedButton(
            onClick = onBrowserAuth,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Browser Sign In")
        }
        Spacer(modifier = Modifier.height(Dimens.spaceSmall))

        OutlinedButton(
            onClick = onTokenExchange,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Token Exchange Flow")
        }
        Spacer(modifier = Modifier.height(Dimens.spaceSmall))

        OutlinedButton(
            onClick = onSessionToken,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Session Token Flow")
        }
    }
}
