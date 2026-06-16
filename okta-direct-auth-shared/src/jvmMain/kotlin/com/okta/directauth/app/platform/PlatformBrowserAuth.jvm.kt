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

import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.directauth.app.AppConfig
import com.okta.oauth2.kmp.AuthorizationCodeFlow
import com.okta.oauth2.kmp.LocalhostBrowserRedirectHandler
import java.net.URI

/**
 * JVM Desktop implementation of browser-based OAuth2 login.
 *
 * Uses [LocalhostBrowserRedirectHandler] to start a local HTTP server, opens the system
 * browser for Okta authorization, and captures the redirect callback.
 *
 * Uses [AppConfig.DESKTOP_SIGN_IN_REDIRECT_URI] (e.g. `http://localhost:8080/callback`)
 * rather than the Android custom-scheme URI passed via [redirectUrl].
 */
actual suspend fun platformBrowserLogin(
    platformContext: Any?,
    client: OAuth2Client,
    redirectUrl: String,
    scope: String,
): Result<TokenInfo> =
    runCatching {
        val desktopRedirectUri = AppConfig.DESKTOP_SIGN_IN_REDIRECT_URI
        require(desktopRedirectUri.isNotBlank()) {
            "desktopSignInRedirectUri is not set in local.properties. " +
                "Add a localhost redirect URI, e.g. http://localhost:8080/callback, " +
                "and register it in your Okta app's Sign-in redirect URIs."
        }

        val uri = URI(desktopRedirectUri)
        val port = if (uri.port > 0) uri.port else 8080
        val path = uri.path.ifBlank { "/callback" }

        val handler = LocalhostBrowserRedirectHandler(port = port, path = path)
        val flow = AuthorizationCodeFlow(client)

        val context = flow.start(desktopRedirectUri, scope = scope).getOrThrow()
        val callbackUri = handler.handleRedirect(context.url)
        flow.resume(callbackUri, context).getOrThrow()
    }
