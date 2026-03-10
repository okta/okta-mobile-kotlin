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
package com.okta.directauth.app.platform

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.okta.directauth.api.WebAuthnCeremonyHandler
import com.okta.directauth.webauthn.AndroidWebAuthnCeremonyHandler

@Composable
actual fun rememberWebAuthnCeremonyHandler(): WebAuthnCeremonyHandler? {
    val context = LocalContext.current
    return remember { (context as? Activity)?.let { AndroidWebAuthnCeremonyHandler(it) } }
}
