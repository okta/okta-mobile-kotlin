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
package com.okta.oidc.browserredirect

import android.content.Context
import android.net.Uri
import com.okta.oauth2.AuthorizationCodeFlow
import com.okta.oidc.OktaSdk
import com.okta.oidc.kotlin.events.EventCoordinator

class BrowserRedirectClient(
    val authorizationCodeFlow: AuthorizationCodeFlow,
    val eventCoordinator: EventCoordinator = OktaSdk.eventCoordinator,
    val webAuthenticationProvider: WebAuthenticationProvider = DefaultWebAuthenticationProvider(eventCoordinator),
) {
    fun login(context: Context): AuthorizationCodeFlow.Context {
        val authorizationCodeFlowContext = authorizationCodeFlow.start()
        webAuthenticationProvider.launch(context, authorizationCodeFlowContext.url)
        return authorizationCodeFlowContext
    }

    suspend fun resume(
        uri: Uri,
        webAuthenticationContext: AuthorizationCodeFlow.Context,
    ): AuthorizationCodeFlow.Result {
        return authorizationCodeFlow.resume(uri, webAuthenticationContext)
    }
}
