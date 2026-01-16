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
import com.okta.authfoundation.api.http.log.AuthFoundationLogger
import com.okta.authfoundation.client.OidcClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json

internal data class DirectAuthenticationContext(
    val issuerUrl: String,
    val clientId: String,
    val scope: List<String>,
    val authorizationServerId: String,
    val clientSecret: String,
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
}
