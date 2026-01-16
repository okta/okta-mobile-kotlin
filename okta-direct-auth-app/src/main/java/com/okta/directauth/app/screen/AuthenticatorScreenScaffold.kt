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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.okta.directauth.app.R
import com.okta.directauth.app.ui.theme.Dimens

@Composable
fun AuthenticatorScreenScaffold(
    title: String,
    username: String,
    backToSignIn: () -> Unit,
    verifyWithSomethingElse: (() -> Unit)?,
    modifier: Modifier = Modifier,
    forgotPassword: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit),
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
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
            text = title,
            fontWeight = FontWeight.Bold
        )
        Text(text = username)
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))
        content()
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            forgotPassword?.invoke()
            if (verifyWithSomethingElse != null) {
                Text(
                    text = stringResource(id = R.string.verify_with_something_else),
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .clickable(onClick = verifyWithSomethingElse)
                )
            }
            Text(
                text = stringResource(id = R.string.back_to_sign_in),
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .clickable(onClick = backToSignIn)
            )
        }
    }
}
