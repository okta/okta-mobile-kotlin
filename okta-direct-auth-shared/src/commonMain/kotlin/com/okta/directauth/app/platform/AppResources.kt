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

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import okta_mobile_kotlin.okta_direct_auth_shared.generated.resources.Res
import okta_mobile_kotlin.okta_direct_auth_shared.generated.resources.ic_launcher_foreground
import org.jetbrains.compose.resources.painterResource

object AppStrings {
    const val APP_NAME = "DirectAuthApp"
    const val REMEMBER_ME = "Keep me signed in"
    const val VERIFY_WITH_SOMETHING_ELSE = "Verify with something else"
    const val BACK_TO_SIGN_IN = "Back to sign in"
    const val FORGOT_PASSWORD = "Forgot password?"
}

@Composable
fun appLogoPainter(): Painter = painterResource(Res.drawable.ic_launcher_foreground)
