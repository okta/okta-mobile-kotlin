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

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.kmp.OAuth2Client

/**
 * An authentication flow that implements the Resource Owner Password Grant.
 *
 * This simple authentication flow permits a user to authenticate using a username and password.
 *
 * > Important: Resource Owner authentication does not support MFA or other more secure
 * > authentication models, and is not recommended for production applications.
 *
 * Example usage:
 * ```kotlin
 * val flow = ResourceOwnerFlowImpl(client)
 * val result = flow.start("user@example.com", "password", "openid profile")
 * result.onSuccess { tokenInfo -> println("Access token: ${tokenInfo.accessToken}") }
 * result.onFailure { error -> println("Authentication failed: $error") }
 * ```
 *
 * @see ResourceOwnerFlowImpl
 */
interface ResourceOwnerFlow {
    /**
     * Initiates the Resource Owner flow by exchanging credentials for tokens.
     *
     * @param username the user's username or email.
     * @param password the user's password.
     * @param scope the scopes to request. Defaults to the client's configured default scope.
     * @return [Result.success] with [TokenInfo] on successful authentication,
     *   or [Result.failure] with the error.
     */
    suspend fun start(
        username: String,
        password: String,
        scope: String,
    ): Result<TokenInfo>

    companion object {
        /**
         * Creates a [ResourceOwnerFlow] backed by the given [client].
         *
         * @param client the [OAuth2Client] used to perform the flow.
         * @return a [ResourceOwnerFlow] instance.
         */
        @OptIn(InternalAuthFoundationApi::class)
        operator fun invoke(client: OAuth2Client): ResourceOwnerFlow = ResourceOwnerFlowImpl(client)
    }
}
