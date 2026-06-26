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

import android.content.Context
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.authfoundation.credential.Token
import com.okta.webauthenticationui.WebAuthentication

/**
 * Android implementation of browser-based OAuth2 login.
 *
 * Uses `web-authentication-ui`'s [WebAuthentication] which provides Chrome Custom Tabs
 * integration with proper activity lifecycle handling and redirect URI capture.
 *
 * Note: [WebAuthentication] uses the default [com.okta.authfoundation.client.OidcConfiguration]
 * set in the Application class, not the KMP [OAuth2Client]. The [client] parameter is accepted
 * for API consistency but is not used on Android.
 */
actual suspend fun platformBrowserLogin(
    platformContext: Any?,
    client: OAuth2Client,
    redirectUrl: String,
    scope: String,
): Result<TokenInfo> {
    val context = platformContext as Context
    val webAuthentication = WebAuthentication()
    return when (val result = webAuthentication.login(context = context, redirectUrl = redirectUrl, scope = scope)) {
        is OAuth2ClientResult.Success<Token> -> {
            // Token implements TokenInfo, so this cast is safe
            Result.success(result.result)
        }

        is OAuth2ClientResult.Error<Token> -> {
            Result.failure(result.exception)
        }
    }
}
