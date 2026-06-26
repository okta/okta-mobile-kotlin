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
 * Default implementation of [ResourceOwnerFlow] using the KMP [OAuth2Client].
 *
 * @param client the [OAuth2Client] instance used for token requests.
 */
internal class ResourceOwnerFlowImpl(
    private val client: OAuth2Client,
) : ResourceOwnerFlow {
    @OptIn(InternalAuthFoundationApi::class)
    override suspend fun start(
        username: String,
        password: String,
        scope: String?,
    ): Result<TokenInfo> =
        client.tokenRequest(
            formParams =
                mapOf(
                    "grant_type" to "password",
                    "username" to username,
                    "password" to password,
                    "scope" to (scope ?: client.configuration.defaultScope),
                    "client_id" to client.configuration.clientId
                )
        )
}
