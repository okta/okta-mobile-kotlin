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
package com.okta.authfoundation.client.internal

import com.okta.authfoundation.InternalAuthFoundationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Internal endpoint representation using String URLs for cross-platform use.
 */
@InternalAuthFoundationApi
class OAuth2Endpoints(
    val issuer: String,
    val authorizationEndpoint: String?,
    val tokenEndpoint: String,
    val userInfoEndpoint: String?,
    val jwksUri: String?,
    val introspectionEndpoint: String?,
    val revocationEndpoint: String?,
    val endSessionEndpoint: String?,
    val deviceAuthorizationEndpoint: String?,
)

@Serializable
internal class SerializableOAuth2Endpoints(
    @SerialName("issuer") val issuer: String,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String? = null,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("userinfo_endpoint") val userInfoEndpoint: String? = null,
    @SerialName("jwks_uri") val jwksUri: String? = null,
    @SerialName("introspection_endpoint") val introspectionEndpoint: String? = null,
    @SerialName("revocation_endpoint") val revocationEndpoint: String? = null,
    @SerialName("end_session_endpoint") val endSessionEndpoint: String? = null,
    @SerialName("device_authorization_endpoint") val deviceAuthorizationEndpoint: String? = null,
) {
    @OptIn(InternalAuthFoundationApi::class)
    fun toEndpoints(): OAuth2Endpoints =
        OAuth2Endpoints(
            issuer = issuer,
            authorizationEndpoint = authorizationEndpoint,
            tokenEndpoint = tokenEndpoint,
            userInfoEndpoint = userInfoEndpoint,
            jwksUri = jwksUri,
            introspectionEndpoint = introspectionEndpoint,
            revocationEndpoint = revocationEndpoint,
            endSessionEndpoint = endSessionEndpoint,
            deviceAuthorizationEndpoint = deviceAuthorizationEndpoint
        )
}
