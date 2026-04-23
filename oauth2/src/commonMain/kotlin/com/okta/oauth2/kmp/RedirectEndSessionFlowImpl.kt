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
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.oauth2.internal.generateUuid
import com.okta.oauth2.internal.parseQueryParameter
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom

@OptIn(InternalAuthFoundationApi::class)
internal class RedirectEndSessionFlowImpl(
    private val client: OAuth2Client,
) : RedirectEndSessionFlow {
    override suspend fun start(
        idToken: String,
        redirectUrl: String,
        extraRequestParameters: Map<String, String>,
    ): Result<RedirectEndSessionFlowContext> =
        runCatching {
            val endpoints =
                client.endpointsOrNull()
                    ?: throw IllegalStateException("OIDC Endpoints not available.")
            val endSessionEndpoint =
                endpoints.endSessionEndpoint
                    ?: throw IllegalStateException("End session endpoint not available.")

            val state = generateUuid()

            val urlBuilder = URLBuilder().takeFrom(endSessionEndpoint)

            for ((key, value) in extraRequestParameters) {
                urlBuilder.parameters.append(key, value)
            }

            urlBuilder.parameters.append("id_token_hint", idToken)
            urlBuilder.parameters.append("post_logout_redirect_uri", redirectUrl)
            urlBuilder.parameters.append("state", state)

            RedirectEndSessionFlowContext(
                url = urlBuilder.buildString(),
                redirectUrl = redirectUrl,
                state = state
            )
        }

    override fun resume(
        uri: String,
        flowContext: RedirectEndSessionFlowContext,
    ): Result<Unit> =
        runCatching {
            if (!uri.startsWith(flowContext.redirectUrl)) {
                throw RedirectEndSessionFlow.ResumeException("Redirect scheme mismatch.")
            }

            val error = parseQueryParameter(uri, "error")
            if (error != null) {
                val errorDescription = parseQueryParameter(uri, "error_description") ?: "An error occurred."
                throw RedirectEndSessionFlow.ResumeException(errorDescription)
            }

            val stateParam = parseQueryParameter(uri, "state")
            if (flowContext.state != stateParam) {
                throw RedirectEndSessionFlow.ResumeException("Failed due to state mismatch.")
            }
        }
}
