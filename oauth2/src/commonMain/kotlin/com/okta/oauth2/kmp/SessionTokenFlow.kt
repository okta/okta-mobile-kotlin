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
 * Implements the Session Token authentication flow.
 *
 * Exchanges a session token (obtained from the Okta legacy Authn API) for OAuth2 tokens by:
 * 1. Building an authorization URL with the session token as an extra parameter.
 * 2. Making a GET request to the authorization URL and capturing the redirect `Location` header.
 * 3. Exchanging the authorization code in the redirect URI for tokens.
 */
interface SessionTokenFlow {
    /**
     * Initiates the session token flow and returns a [TokenInfo] on success.
     *
     * @param sessionToken the session token obtained from Okta legacy Authn APIs.
     * @param redirectUrl the redirect URL registered with the authorization server.
     * @param extraRequestParameters additional key-value pairs appended to the authorization URL.
     * @param scope the space-delimited scopes to request.
     * @return [Result.success] with [TokenInfo] on success, or [Result.failure] on error.
     */
    suspend fun start(
        sessionToken: String,
        redirectUrl: String,
        extraRequestParameters: Map<String, String> = emptyMap(),
        scope: String = "openid profile email offline_access",
    ): Result<TokenInfo>

    companion object {
        /**
         * Creates a [SessionTokenFlow] backed by the given [OAuth2Client].
         *
         * @param client the [OAuth2Client] used for token requests.
         */
        operator fun invoke(client: OAuth2Client): SessionTokenFlow = SessionTokenFlowImpl(client)
    }
}
