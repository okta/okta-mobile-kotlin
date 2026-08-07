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

import com.okta.authfoundation.api.http.ApiRequestMethod
import com.okta.authfoundation.client.ClientAssertion
import com.okta.directauth.model.DirectAuthenticationContext
import com.okta.directauth.model.OobChannel
import com.okta.directauth.model.clientAuthenticationFormParameters
import com.okta.directauth.model.endpointUrl

internal class DirectAuthOobAuthenticateRequest(
    internal val context: DirectAuthenticationContext,
    val loginHint: String,
    val oobChannel: OobChannel,
    private val clientAssertion: ClientAssertion? = null,
) : DirectAuthStartRequest {
    override fun url(): String = context.endpointUrl("oob-authenticate")

    override fun method(): ApiRequestMethod = ApiRequestMethod.POST

    override fun contentType(): String = "application/x-www-form-urlencoded"

    override fun query(): Map<String, String>? = context.additionalParameters.takeIf { it.isNotEmpty() }

    override fun formParameters(): Map<String, List<String>> =
        buildMap {
            putAll(context.clientAuthenticationFormParameters(clientAssertion))
            put("client_id", listOf(context.clientId))
            put("login_hint", listOf(loginHint))
            put("channel_hint", listOf(oobChannel.value))
        }
}
