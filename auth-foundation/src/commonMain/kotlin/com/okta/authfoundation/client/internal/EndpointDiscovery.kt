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

internal class EndpointDiscovery(
    private val configuration: OAuth2ClientConfiguration,
) {
    @OptIn(InternalAuthFoundationApi::class)
    suspend fun discover(): OAuth2ClientResult<OAuth2Endpoints> =
        runCatching {
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
            endpoints.toEndpoints()
        }.fold(
            onSuccess = { OAuth2ClientResult.Success(it) },
            onFailure = { OAuth2ClientResult.Error(it as Exception) }
        )
}
