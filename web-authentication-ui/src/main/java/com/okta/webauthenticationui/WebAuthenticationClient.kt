/*
 * Copyright 2021-Present Okta, Inc.
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
package com.okta.webauthenticationui

import android.content.Context
import android.net.Uri
import com.okta.oauth2.AuthorizationCodeFlow
import com.okta.authfoundation.client.OidcClient
import com.okta.oauth2.AuthorizationCodeFlow.Companion.authorizationCodeFlow
import com.okta.oauth2.RedirectEndSessionFlow
import com.okta.oauth2.RedirectEndSessionFlow.Companion.redirectEndSessionFlow

class WebAuthenticationClient private constructor(
    oidcClient: OidcClient,
    private val webAuthenticationProvider: WebAuthenticationProvider,
) {
    companion object {
        fun OidcClient.webAuthenticationClient(
            webAuthenticationProvider: WebAuthenticationProvider = DefaultWebAuthenticationProvider(configuration.eventCoordinator)
        ): WebAuthenticationClient {
            return WebAuthenticationClient(this, webAuthenticationProvider)
        }
    }

    private val authorizationCodeFlow = oidcClient.authorizationCodeFlow()
    private val redirectEndSessionFlow = oidcClient.redirectEndSessionFlow()

    fun login(context: Context): AuthorizationCodeFlow.Context {
        val flowContext = authorizationCodeFlow.start()
        webAuthenticationProvider.launch(context, flowContext.url)
        return flowContext
    }

    suspend fun resume(
        uri: Uri,
        flowContext: AuthorizationCodeFlow.Context,
    ): AuthorizationCodeFlow.Result {
        return authorizationCodeFlow.resume(uri, flowContext)
    }

    fun logout(context: Context, idToken: String): RedirectEndSessionFlow.Context {
        val flowContext = redirectEndSessionFlow.start(idToken)
        webAuthenticationProvider.launch(context, flowContext.url)
        return flowContext
    }

    fun resume(uri: Uri, flowContext: RedirectEndSessionFlow.Context): RedirectEndSessionFlow.Result {
        return redirectEndSessionFlow.resume(uri, flowContext)
    }
}
