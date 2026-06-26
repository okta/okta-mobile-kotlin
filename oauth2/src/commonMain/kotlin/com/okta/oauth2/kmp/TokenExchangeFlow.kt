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
package com.okta.oauth2.kmp

import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.kmp.OAuth2Client

/**
 * Implements the [Token Exchange](https://openid.net/specs/openid-connect-native-sso-1_0.html) flow
 * for Native SSO.
 *
 * Use [start] to exchange an existing ID token and device secret for new tokens:
 *
 * ```kotlin
 * val flow = TokenExchangeFlow(client)
 * val tokenInfo = flow.start(idToken = "...", deviceSecret = "...").getOrThrow()
 * ```
 */
interface TokenExchangeFlow {
    /**
     * Initiates the token exchange flow.
     *
     * @param idToken the ID token for the user.
     * @param deviceSecret the device secret obtained from a previous authentication flow.
     * @param audience the audience of the authorization server. Defaults to `api://default`.
     * @param scope the OAuth2 scopes to request. Defaults to the client's configured default scope when null.
     * @return [Result.success] with [TokenInfo] on success, or [Result.failure] with:
     * - [com.okta.authfoundation.client.OAuth2ClientResult.Error.OidcEndpointsNotAvailableException] if endpoints are unavailable.
     * - [com.okta.authfoundation.client.OAuth2ClientResult.Error.HttpResponseException] on server errors.
     * - Other exceptions for network or parsing failures.
     */
    suspend fun start(
        idToken: String,
        deviceSecret: String,
        audience: String? = null,
        scope: String? = null,
    ): Result<TokenInfo>

    companion object {
        /**
         * Creates a [TokenExchangeFlow] backed by the given [OAuth2Client].
         */
        operator fun invoke(client: OAuth2Client): TokenExchangeFlow = TokenExchangeFlowImpl(client)
    }
}
