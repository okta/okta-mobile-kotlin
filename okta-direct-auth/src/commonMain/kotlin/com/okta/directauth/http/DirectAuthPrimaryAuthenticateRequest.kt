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

import com.okta.authfoundation.GrantType
import com.okta.authfoundation.api.http.ApiRequestMethod
import com.okta.directauth.model.DirectAuthenticationContext

/**
 * HTTP request to the `/primary-authenticate` endpoint for primary WebAuthn flows.
 *
 * The response is a [com.okta.directauth.http.model.WebAuthnChallengeResponse] containing
 * the WebAuthn credential request options.
 */
internal class DirectAuthPrimaryAuthenticateRequest(
    internal val context: DirectAuthenticationContext,
    val loginHint: String? = null,
) : DirectAuthStartRequest {
    private val path =
        if (context.authorizationServerId.isBlank()) {
            "/oauth2/v1/primary-authenticate"
        } else {
            "/oauth2/${context.authorizationServerId}/v1/primary-authenticate"
        }

    override fun url(): String = context.issuerUrl.trimEnd('/') + path

    override fun method(): ApiRequestMethod = ApiRequestMethod.POST

    override fun contentType(): String = "application/x-www-form-urlencoded"

    override fun query(): Map<String, String>? = context.additionalParameters.takeIf { it.isNotEmpty() }

    override fun formParameters(): Map<String, List<String>> =
        buildMap {
            if (context.clientSecret.isNotBlank()) put("client_secret", listOf(context.clientSecret))
            put("client_id", listOf(context.clientId))
            put("challenge_hint", listOf(GrantType.WebAuthn.value))
            loginHint?.let { put("login_hint", listOf(it)) }
            put("scope", listOf(context.scope.joinToString(" ")))
            put("grant_types_supported", listOf(context.grantTypes.joinToString(" ") { it.value }))
        }
}
