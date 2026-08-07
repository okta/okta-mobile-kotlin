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
package com.okta.directauth.model

import com.okta.authfoundation.GrantType
import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.log.AuthFoundationLogger
import com.okta.authfoundation.client.ClientAssertion
import com.okta.authfoundation.client.ClientAssertionProvider
import com.okta.authfoundation.client.OidcClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

internal data class DirectAuthenticationContext(
    val issuerUrl: String,
    val clientId: String,
    val scope: List<String>,
    val authorizationServerId: String,
    val clientSecret: String,
    val clientAssertionProvider: ClientAssertionProvider? = null,
    val computeDispatcher: CoroutineContext = Dispatchers.Default,
    val grantTypes: List<GrantType>,
    val acrValues: List<String>,
    val directAuthenticationIntent: DirectAuthenticationIntent,
    val apiExecutor: ApiExecutor,
    val logger: AuthFoundationLogger,
    val clock: OidcClock,
    val additionalParameters: Map<String, String>,
) {
    val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }

    val authenticationStateFlow: MutableStateFlow<DirectAuthenticationState> = MutableStateFlow(DirectAuthenticationState.Idle)

    override fun toString(): String =
        "DirectAuthenticationContext(issuerUrl=$issuerUrl, clientId=$clientId, scope=$scope, " +
            "authorizationServerId=$authorizationServerId, clientSecret=***, " +
            "clientAssertionProvider=${clientAssertionProvider?.let { "***" }}, grantTypes=$grantTypes, " +
            "acrValues=$acrValues, directAuthenticationIntent=$directAuthenticationIntent, " +
            "additionalParameters=$additionalParameters)"
}

/**
 * Resolves a fresh [ClientAssertion] from [DirectAuthenticationContext.clientAssertionProvider] for
 * the given [audience] (the endpoint URL of the request about to be sent), or `null` if no provider
 * is configured.
 *
 * Invoked anew for every client-authenticated request — including each iteration of an OOB poll —
 * so the returned assertion can carry a unique `jti` and a correctly scoped, non-expired `exp`/`aud`.
 * Never cache or reuse the result across requests.
 */
internal suspend fun DirectAuthenticationContext.resolveClientAssertion(audience: String): ClientAssertion? =
    clientAssertionProvider?.let { provider -> withContext(computeDispatcher) { provider.provide(audience) } }

/**
 * Builds the `client_secret` or `client_assertion_type`/`client_assertion` form parameters for a
 * request, preferring [clientAssertion] (from [resolveClientAssertion]) over
 * [DirectAuthenticationContext.clientSecret].
 */
internal fun DirectAuthenticationContext.clientAuthenticationFormParameters(clientAssertion: ClientAssertion?): Map<String, List<String>> =
    when {
        clientAssertion != null -> {
            mapOf(
                "client_assertion_type" to listOf(clientAssertion.type),
                "client_assertion" to listOf(clientAssertion.assertion)
            )
        }

        clientSecret.isNotBlank() -> {
            mapOf("client_secret" to listOf(clientSecret))
        }

        else -> {
            emptyMap()
        }
    }

/**
 * The full URL for a Direct Authentication endpoint, honoring
 * [DirectAuthenticationContext.authorizationServerId] when set.
 */
internal fun DirectAuthenticationContext.endpointUrl(pathSuffix: String): String =
    issuerUrl.trimEnd('/') + if (authorizationServerId.isBlank()) "/oauth2/v1/$pathSuffix" else "/oauth2/$authorizationServerId/v1/$pathSuffix"
