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
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.api.http.ApiRequestMethod
import com.okta.authfoundation.client.OAuth2ClientConfiguration
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.OAuth2EndpointOverrides

internal class EndpointDiscovery(
    private val configuration: OAuth2ClientConfiguration,
) {
    @OptIn(InternalAuthFoundationApi::class)
    suspend fun discover(): OAuth2ClientResult<OAuth2Endpoints> {
        val overrides = configuration.endpointOverrides

        // Skip discovery entirely when all 8 endpoint overrides are provided.
        if (OAuth2Endpoints.allFieldsNonNull(overrides)) {
            return OAuth2ClientResult.Success(overrides!!.toOAuth2Endpoints(configuration.issuerUrl))
        }

        return runCatching {
            val request =
                object : ApiRequest {
                    override fun method(): ApiRequestMethod = ApiRequestMethod.GET

                    override fun headers(): Map<String, List<String>> = mapOf("Accept" to listOf("application/json"))

                    override fun url(): String = configuration.issuerUrl + "/.well-known/openid-configuration"
                }
            val response = configuration.apiExecutor.execute(request).getOrThrow()
            val body =
                response.body?.decodeToString()
                    ?: throw IllegalStateException("Empty response from discovery endpoint")
            val endpoints = configuration.json.decodeFromString<SerializableOAuth2Endpoints>(body)
            endpoints.toEndpoints().merge(overrides)
        }.fold(
            onSuccess = { OAuth2ClientResult.Success(it) },
            onFailure = { OAuth2ClientResult.Error(it as Exception) }
        )
    }
}

/**
 * Converts an [OAuth2EndpointOverrides] with all fields set to an [OAuth2Endpoints].
 * The [issuer] is taken from the configuration's issuerUrl.
 */
@OptIn(InternalAuthFoundationApi::class)
private fun OAuth2EndpointOverrides.toOAuth2Endpoints(issuer: String): OAuth2Endpoints =
    OAuth2Endpoints(
        issuer = issuer,
        authorizationEndpoint = authorizationEndpoint,
        tokenEndpoint = tokenEndpoint!!,
        userInfoEndpoint = userInfoEndpoint,
        jwksUri = jwksUri,
        introspectionEndpoint = introspectionEndpoint,
        revocationEndpoint = revocationEndpoint,
        endSessionEndpoint = endSessionEndpoint,
        deviceAuthorizationEndpoint = deviceAuthorizationEndpoint
    )
