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
 * Encapsulates the behavior required to authenticate using the OIDC Authorization Code flow with PKCE.
 *
 * See [Authorization Code Flow documentation](https://developer.okta.com/docs/guides/implement-grant-type/authcodepkce/main/)
 */
interface AuthorizationCodeFlow {
    /**
     * Thrown by [resume] when the authorization server returns an error.
     *
     * @property errorId the OAuth2 error code returned by the server.
     */
    class ResumeException(
        message: String,
        val errorId: String,
    ) : Exception(message)

    /**
     * Thrown by [resume] when the redirect URI scheme does not match the configured redirect URL.
     */
    class RedirectSchemeMismatchException : Exception()

    /**
     * Thrown by [resume] when no authorization code is present in the redirect URI.
     */
    class MissingResultCodeException : Exception()

    /**
     * Initiates the Authorization Code redirect flow.
     *
     * @param redirectUrl the registered redirect URI for this client.
     * @param extraRequestParameters additional query parameters for the authorization endpoint.
     * @param scope the scopes to request. Defaults to the client's configured default scope when null.
     * @return a [Result] containing an [AuthorizationCodeFlowContext] with the authorization URL and state.
     */
    suspend fun start(
        redirectUrl: String,
        extraRequestParameters: Map<String, String> = emptyMap(),
        scope: String? = null,
    ): Result<AuthorizationCodeFlowContext>

    /**
     * Completes the Authorization Code redirect flow by exchanging the authorization code for tokens.
     *
     * @param uri the redirect URI received from the browser after authorization.
     * @param flowContext the [AuthorizationCodeFlowContext] returned by [start].
     * @return a [Result] containing [TokenInfo] on success.
     */
    suspend fun resume(
        uri: String,
        flowContext: AuthorizationCodeFlowContext,
    ): Result<TokenInfo>

    companion object {
        /**
         * Creates an [AuthorizationCodeFlow] backed by the given [OAuth2Client].
         */
        operator fun invoke(client: OAuth2Client): AuthorizationCodeFlow = AuthorizationCodeFlowImpl(client)
    }
}
