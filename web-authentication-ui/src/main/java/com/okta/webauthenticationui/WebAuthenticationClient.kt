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

import android.app.Activity
import android.content.Context
import androidx.annotation.VisibleForTesting
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.client.internal.SdkVersionsRegistry
import com.okta.authfoundation.credential.Token
import com.okta.oauth2.AuthorizationCodeFlow
import com.okta.oauth2.AuthorizationCodeFlow.Companion.createAuthorizationCodeFlow
import com.okta.oauth2.RedirectEndSessionFlow
import com.okta.oauth2.RedirectEndSessionFlow.Companion.createRedirectEndSessionFlow
import com.okta.webauthenticationui.events.CustomizeBrowserEvent
import com.okta.webauthenticationui.events.CustomizeCustomTabsEvent

/**
 * Authentication coordinator that simplifies signing users in using browser-based OIDC authentication flows.
 *
 * This simple class encapsulates the details of launching the browser, and coordinating with OAuth2 endpoints.
 *
 * To customize the flow, please read more about customization options: [WebAuthenticationProvider], [CustomizeBrowserEvent],
 * [CustomizeCustomTabsEvent].
 * To further customize the authentication flow, please read more about the underlying flows: [AuthorizationCodeFlow],
 * [RedirectEndSessionFlow].
 */
class WebAuthenticationClient private constructor(
    private val oidcClient: OidcClient,
    private val webAuthenticationProvider: WebAuthenticationProvider,
) {
    companion object {
        init {
            SdkVersionsRegistry.register(SDK_VERSION)
        }

        /**
         * Initializes a web authentication client using the [OidcClient].
         *
         * @receiver the [OidcClient] used to perform the low level OIDC requests, as well as with which to use the configuration from.
         * @param webAuthenticationProvider the [WebAuthenticationProvider] which will be used to show the UI when performing the
         * redirect flows.
         */
        fun OidcClient.createWebAuthenticationClient(
            webAuthenticationProvider: WebAuthenticationProvider = DefaultWebAuthenticationProvider(configuration.eventCoordinator)
        ): WebAuthenticationClient {
            return WebAuthenticationClient(this, webAuthenticationProvider)
        }
    }

    private val authorizationCodeFlow: AuthorizationCodeFlow = oidcClient.createAuthorizationCodeFlow()
    private val redirectEndSessionFlow: RedirectEndSessionFlow = oidcClient.createRedirectEndSessionFlow()

    @VisibleForTesting internal var redirectCoordinator: RedirectCoordinator = SingletonRedirectCoordinator

    /**
     * Used in a [OidcClientResult.Error.exception].
     *
     * Indicates the requested flow was cancelled.
     */
    class FlowCancelledException internal constructor() : Exception("Flow cancelled.")

    /**
     * Initiates the OIDC Authorization Code redirect flow.
     *
     * @param context the Android [Activity] [Context] which is used to display the login flow via the configured
     * [WebAuthenticationProvider].
     * @param redirectUrl the redirect URL.
     * @param extraRequestParameters the extra key value pairs to send to the authorize endpoint.
     *  See [Authorize Documentation](https://developer.okta.com/docs/reference/api/oidc/#authorize) for parameter options.
     * @param scope the scopes to request during sign in. Defaults to the configured [OidcClient] [OidcConfiguration.defaultScope].
     */
    suspend fun login(
        context: Context,
        redirectUrl: String,
        extraRequestParameters: Map<String, String> = emptyMap(),
        scope: String = oidcClient.configuration.defaultScope,
    ): OidcClientResult<Token> {
        val initializationResult = redirectCoordinator.initialize(webAuthenticationProvider, context) {
            when (val result = authorizationCodeFlow.start(redirectUrl, extraRequestParameters, scope)) {
                is OidcClientResult.Success -> {
                    RedirectInitializationResult.Success(result.result.url, result.result)
                }
                is OidcClientResult.Error -> {
                    RedirectInitializationResult.Error(result.exception)
                }
            }
        }

        val flowContext = when (initializationResult) {
            is RedirectInitializationResult.Error -> {
                return OidcClientResult.Error(initializationResult.exception)
            }
            is RedirectInitializationResult.Success -> {
                initializationResult.flowContext
            }
        }

        val uri = when (val redirectResult = redirectCoordinator.listenForResult()) {
            is RedirectResult.Error -> {
                return OidcClientResult.Error(redirectResult.exception)
            }
            is RedirectResult.Redirect -> {
                redirectResult.uri
            }
        }

        return authorizationCodeFlow.resume(uri, flowContext)
    }

    /**
     * Initiates the OIDC logout redirect flow.
     *
     * > Note: OIDC Logout terminology is nuanced, see [Logout Documentation](https://github.com/okta/okta-mobile-kotlin#logout) for additional details.
     *
     * @param context the Android [Activity] [Context] which is used to display the logout flow via the configured
     * [WebAuthenticationProvider].
     * @param redirectUrl the redirect URL.
     * @param idToken the token used to identify the session to log the user out of.
     */
    suspend fun logoutOfBrowser(context: Context, redirectUrl: String, idToken: String): OidcClientResult<Unit> {
        val initializationResult = redirectCoordinator.initialize(webAuthenticationProvider, context) {
            when (val result = redirectEndSessionFlow.start(redirectUrl, idToken)) {
                is OidcClientResult.Success -> {
                    RedirectInitializationResult.Success(result.result.url, result.result)
                }
                is OidcClientResult.Error -> {
                    RedirectInitializationResult.Error(result.exception)
                }
            }
        }

        val flowContext = when (initializationResult) {
            is RedirectInitializationResult.Error -> {
                return OidcClientResult.Error(initializationResult.exception)
            }
            is RedirectInitializationResult.Success -> {
                initializationResult.flowContext
            }
        }

        val uri = when (val redirectResult = redirectCoordinator.listenForResult()) {
            is RedirectResult.Error -> {
                return OidcClientResult.Error(redirectResult.exception)
            }
            is RedirectResult.Redirect -> {
                redirectResult.uri
            }
        }

        return redirectEndSessionFlow.resume(uri, flowContext)
    }
}
