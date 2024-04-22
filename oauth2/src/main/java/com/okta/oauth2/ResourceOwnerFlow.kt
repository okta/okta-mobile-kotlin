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

import com.okta.authfoundation.client.OAuth2Client
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.client.internal.SdkVersionsRegistry
import com.okta.authfoundation.credential.Token
import okhttp3.FormBody
import okhttp3.Request

/**
 * An authentication flow class that implements the Resource Owner Flow exchange.
 *
 * This simple authentication flow permits a user to authenticate using a simple username and password. As such, the configuration is straightforward.
 *
 * > Important: Resource Owner authentication does not support MFA or other more secure authentication models, and is not recommended for production applications.
 */
class ResourceOwnerFlow(
    private val client: OAuth2Client,
) {
    companion object {
        init {
            SdkVersionsRegistry.register(SDK_VERSION)
        }
    }

    /**
     * Initializes a resource owner flow.
     */
    constructor() : this(OAuth2Client.default)

    /**
     * Initializes a resource owner flow using the [OidcConfiguration].
     *
     * @param oidcConfiguration the [OidcConfiguration] specifying the authorization servers.
     */
    constructor(oidcConfiguration: OidcConfiguration) : this(OAuth2Client.createFromConfiguration(oidcConfiguration))

    /**
     * Initiates the Resource Owner flow.
     *
     * @param username the username
     * @param password the password
     * @param scope the scopes to request during sign in. Defaults to the configured [client] [OidcConfiguration.defaultScope].
     */
    suspend fun start(
        username: String,
        password: String,
        scope: String = client.configuration.defaultScope,
    ): OAuth2ClientResult<Token> {
        val endpoints = client.endpointsOrNull() ?: return client.endpointNotAvailableError()

        val formBodyBuilder = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .add("client_id", client.configuration.clientId)
            .add("grant_type", "password")
            .add("scope", scope)

        val request = Request.Builder()
            .post(formBodyBuilder.build())
            .url(endpoints.tokenEndpoint)
            .build()

        return client.tokenRequest(request)
    }
}
