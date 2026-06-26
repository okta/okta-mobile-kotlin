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
import com.okta.oauth2.internal.performGetCaptureLocationHeader

/**
 * Default implementation of [SessionTokenFlow] using the KMP [OAuth2Client].
 *
 * @param client the [OAuth2Client] instance used for authorization and token requests.
 */
@OptIn(InternalAuthFoundationApi::class)
internal class SessionTokenFlowImpl(
    private val client: OAuth2Client,
) : SessionTokenFlow {
    override suspend fun start(
        sessionToken: String,
        redirectUrl: String,
        extraRequestParameters: Map<String, String>,
        scope: String?,
    ): Result<TokenInfo> =
        runCatching {
            val authorizationCodeFlow = AuthorizationCodeFlowImpl(client)
            val mutableParameters = extraRequestParameters.toMutableMap()
            mutableParameters["sessionToken"] = sessionToken

            val flowContext =
                authorizationCodeFlow
                    .start(
                        redirectUrl = redirectUrl,
                        extraRequestParameters = mutableParameters,
                        scope = scope
                    ).getOrThrow()

            val locationHeader =
                performGetCaptureLocationHeader(
                    apiExecutor = client.configuration.apiExecutor,
                    url = flowContext.url
                ).getOrThrow()

            authorizationCodeFlow.resume(locationHeader, flowContext).getOrThrow()
        }
}
