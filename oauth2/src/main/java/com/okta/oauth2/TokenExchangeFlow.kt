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
 * An authentication flow class that implements the Token Exchange Flow.
 *
 * As an example, consider [SSO for Native Apps](https://developer.okta.com/docs/guides/configure-native-sso/main/#native-sso-flow) where a client exchanges the ID and the Device Secret tokens to get access to the resource.
 *
 * See the [specification](https://openid.net/specs/openid-connect-native-sso-1_0.html)
 */
class TokenExchangeFlow(
    private val client: OAuth2Client,
) {
    companion object {
        init {
            SdkVersionsRegistry.register(SDK_VERSION)
        }
    }

    /**
     * Initializes a token exchange flow.
     */
    constructor() : this(OAuth2Client.default)

    /**
     * Initializes a token exchange flow using the [OidcConfiguration].
     *
     * @param oidcConfiguration the [OidcConfiguration] specifying the authorization servers.
     */
    constructor(oidcConfiguration: OidcConfiguration) : this(OAuth2Client.createFromConfiguration(oidcConfiguration))

    /**
     * Initiates the Token Exchange flow.
     *
     * @param idToken the id token for the user to create a new token for.
     * @param deviceSecret the [Token.deviceSecret] obtained via another authentication flow.
     * @param audience the audience of the authorization server. Defaults to `api://default`.
     * @param scope the scopes to request during sign in. Defaults to the configured [client] [OidcConfiguration.defaultScope].
     */
    suspend fun start(
        idToken: String,
        deviceSecret: String,
        audience: String = "api://default",
        scope: String = client.configuration.defaultScope,
    ): OAuth2ClientResult<Token> {
        val endpoints = client.endpointsOrNull() ?: return client.endpointNotAvailableError()

        val formBodyBuilder = FormBody.Builder()
            .add("audience", audience)
            .add("subject_token_type", "urn:ietf:params:oauth:token-type:id_token")
            .add("subject_token", idToken)
            .add("actor_token_type", "urn:x-oath:params:oauth:token-type:device-secret")
            .add("actor_token", deviceSecret)
            .add("client_id", client.configuration.clientId)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
            .add("scope", scope)

        val request = Request.Builder()
            .post(formBodyBuilder.build())
            .url(endpoints.tokenEndpoint)
            .build()

        return client.tokenRequest(request)
    }
}
