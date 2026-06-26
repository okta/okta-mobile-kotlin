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

/**
 * Platform-specific browser-based OAuth2 login.
 *
 * On Android, this uses the `web-authentication-ui` module with Chrome Custom Tabs.
 * On JVM Desktop, this uses [com.okta.oauth2.kmp.LocalhostBrowserRedirectHandler]
 * with the KMP [com.okta.oauth2.kmp.AuthorizationCodeFlow].
 *
 * @param client the shared OAuth2Client for this flow
 * @param redirectUrl the registered redirect URI
 * @param scope the OAuth2 scopes to request
 * @return [Result] wrapping the [TokenInfo] on success or an exception on failure
 */
expect suspend fun platformBrowserLogin(
    platformContext: Any?,
    client: OAuth2Client,
    redirectUrl: String,
    scope: String,
): Result<TokenInfo>
