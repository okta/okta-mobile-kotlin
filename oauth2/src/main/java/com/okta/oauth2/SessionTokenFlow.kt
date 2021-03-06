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
package com.okta.oauth2

import android.net.Uri
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.client.internal.performRequest
import com.okta.authfoundation.credential.Token
import com.okta.oauth2.AuthorizationCodeFlow.Companion.createAuthorizationCodeFlow
import okhttp3.Request

/**
 * [SessionTokenFlow] encapsulates the behavior required to authentication using a session token obtained from the Okta Legacy Authn
 * APIs.
 */
class SessionTokenFlow private constructor(
    private val oidcClient: OidcClient,
) {
    companion object {
        /**
         * Initializes a session token flow using the [OidcClient].
         *
         * @receiver the [OidcClient] used to perform the low level OIDC requests, as well as with which to use the configuration from.
         */
        fun OidcClient.createSessionTokenFlow(): SessionTokenFlow {
            return SessionTokenFlow(this)
        }
    }

    /**
     * Initiates the Session Token Flow.
     *
     * @param sessionToken the session token obtained from Okta legacy Authn APIs.
     * @param redirectUrl the redirect URL.
     * @param extraRequestParameters the extra key value pairs to send to the authorize endpoint.
     *  See [Authorize Documentation](https://developer.okta.com/docs/reference/api/oidc/#authorize) for parameter options.
     * @param scope the scopes to request during sign in. Defaults to the configured [OidcClient] [OidcConfiguration.defaultScope].
     */
    suspend fun start(
        sessionToken: String,
        redirectUrl: String,
        extraRequestParameters: Map<String, String> = emptyMap(),
        scope: String = oidcClient.configuration.defaultScope,
    ): OidcClientResult<Token> {
        val authorizationCodeFlow = oidcClient.createAuthorizationCodeFlow()
        val mutableParameters = extraRequestParameters.toMutableMap()
        mutableParameters["sessionToken"] = sessionToken
        val flowContext = when (val startResult = authorizationCodeFlow.start(redirectUrl, mutableParameters, scope)) {
            is OidcClientResult.Error -> {
                return OidcClientResult.Error(startResult.exception)
            }
            is OidcClientResult.Success -> {
                startResult.result
            }
        }

        val request = Request.Builder()
            .url(flowContext.url)
            .build()

        val uri = when (val result = oidcClient.performRequest(request)) {
            is OidcClientResult.Error -> {
                return OidcClientResult.Error(result.exception)
            }
            is OidcClientResult.Success -> {
                val locationHeader = result.result.header("location")
                    ?: return OidcClientResult.Error(IllegalStateException("No location header."))
                Uri.parse(locationHeader)
            }
        }

        return authorizationCodeFlow.resume(uri, flowContext)
    }
}
