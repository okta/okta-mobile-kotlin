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
package com.okta.directauth.http

import com.okta.authfoundation.ChallengeGrantType
import com.okta.authfoundation.api.http.ApiRequestMethod
import com.okta.directauth.model.DirectAuthenticationContext
import com.okta.directauth.model.MfaContext
import com.okta.directauth.model.OobChannel

internal class DirectAuthChallengeRequest(
    internal val context: DirectAuthenticationContext,
    private val mfaContext: MfaContext,
    private val challengeTypesSupported: List<ChallengeGrantType>,
    private val oobChannel: OobChannel?,
) : DirectAuthRequest {
    private val path = if (context.authorizationServerId.isBlank()) "/oauth2/v1/challenge" else "/oauth2/${context.authorizationServerId}/v1/challenge"

    override fun url(): String = context.issuerUrl.trimEnd('/') + path

    override fun headers(): Map<String, List<String>> = mapOf("Accept" to listOf("application/json"))

    override fun method(): ApiRequestMethod = ApiRequestMethod.POST

    override fun contentType(): String = "application/x-www-form-urlencoded"

    override fun query(): Map<String, String>? = context.additionalParameters.takeIf { it.isNotEmpty() }

    override fun formParameters(): Map<String, List<String>> =
        buildMap {
            if (context.clientSecret.isNotBlank()) put("client_secret", listOf(context.clientSecret))
            put("client_id", listOf(context.clientId))
            put("mfa_token", listOf(mfaContext.mfaToken))
            put("challenge_types_supported", listOf(challengeTypesSupported.joinToString(" ") { it.value }))
            oobChannel?.let { put("channel_hint", listOf(it.value)) }
        }
}
