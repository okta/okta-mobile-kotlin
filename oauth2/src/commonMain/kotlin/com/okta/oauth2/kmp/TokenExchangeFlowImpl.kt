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
 * Default implementation of [TokenExchangeFlow] using the KMP [OAuth2Client].
 *
 * @param client the [OAuth2Client] instance used for token requests.
 */
internal class TokenExchangeFlowImpl(
    private val client: OAuth2Client,
) : TokenExchangeFlow {
    @OptIn(InternalAuthFoundationApi::class)
    override suspend fun start(
        idToken: String,
        deviceSecret: String,
        audience: String?,
        scope: String?,
    ): Result<TokenInfo> {
        val formParams =
            buildMap {
                if (audience != null) put("audience", audience)
                put("subject_token_type", "urn:ietf:params:oauth:token-type:id_token")
                put("subject_token", idToken)
                put("actor_token_type", "urn:x-oath:params:oauth:token-type:device-secret")
                put("actor_token", deviceSecret)
                put("client_id", client.configuration.clientId)
                put("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                put("scope", scope ?: client.configuration.defaultScope)
            }
        return client.tokenRequest(formParams = formParams)
    }
}
