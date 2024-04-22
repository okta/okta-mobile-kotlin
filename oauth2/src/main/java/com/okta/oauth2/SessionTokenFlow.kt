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
import com.okta.authfoundation.client.OAuth2Client
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.client.internal.performRequest
import com.okta.authfoundation.credential.Token
import okhttp3.Request

/**
 * [SessionTokenFlow] encapsulates the behavior required to authentication using a session token obtained from the Okta Legacy Authn
 * APIs.
 */
class SessionTokenFlow(
    private val client: OAuth2Client,
) {

    /**
     * Initializes a session token flow.
     */
    constructor() : this(OAuth2Client.default)

    /**
     * Initializes a session token flow using the [OidcConfiguration].
     *
     * @param oidcConfiguration the [OidcConfiguration] specifying the authorization servers.
     */
    constructor(oidcConfiguration: OidcConfiguration) : this(OAuth2Client.createFromConfiguration(oidcConfiguration))

    /**
     * Initiates the Session Token Flow.
     *
     * @param sessionToken the session token obtained from Okta legacy Authn APIs.
     * @param redirectUrl the redirect URL.
     * @param extraRequestParameters the extra key value pairs to send to the authorize endpoint.
     *  See [Authorize Documentation](https://developer.okta.com/docs/reference/api/oidc/#authorize) for parameter options.
     * @param scope the scopes to request during sign in. Defaults to the configured [client] [OidcConfiguration.defaultScope].
     */
    suspend fun start(
        sessionToken: String,
        redirectUrl: String,
        extraRequestParameters: Map<String, String> = emptyMap(),
        scope: String = client.configuration.defaultScope,
    ): OAuth2ClientResult<Token> {
        val authorizationCodeFlow = AuthorizationCodeFlow(client)
        val mutableParameters = extraRequestParameters.toMutableMap()
        mutableParameters["sessionToken"] = sessionToken
        val flowContext = when (val startResult = authorizationCodeFlow.start(redirectUrl, mutableParameters, scope)) {
            is OAuth2ClientResult.Error -> {
                return OAuth2ClientResult.Error(startResult.exception)
            }
            is OAuth2ClientResult.Success -> {
                startResult.result
            }
        }

        val request = Request.Builder()
            .url(flowContext.url)
            .build()

        val uri = when (val result = client.performRequest(request)) {
            is OAuth2ClientResult.Error -> {
                return OAuth2ClientResult.Error(result.exception)
            }
            is OAuth2ClientResult.Success -> {
                val locationHeader = result.result.header("location")
                    ?: return OAuth2ClientResult.Error(IllegalStateException("No location header."))
                Uri.parse(locationHeader)
            }
        }

        return authorizationCodeFlow.resume(uri, flowContext)
    }
}
