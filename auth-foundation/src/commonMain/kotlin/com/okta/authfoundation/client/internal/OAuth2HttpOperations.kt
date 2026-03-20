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
import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.ApiFormRequest
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.api.http.ApiRequestMethod
import com.okta.authfoundation.client.OAuth2ClientResult
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

/**
 * Executes a JSON GET request and deserializes the response.
 */
@OptIn(InternalAuthFoundationApi::class)
internal suspend fun <T> performJsonGetRequest(
    apiExecutor: ApiExecutor,
    json: Json,
    url: String,
    deserializer: DeserializationStrategy<T>,
    headers: Map<String, List<String>> = mapOf("Accept" to listOf("application/json")),
): OAuth2ClientResult<T> =
    runCatching {
        val request =
            object : ApiRequest {
                override fun method(): ApiRequestMethod = ApiRequestMethod.GET

                override fun headers(): Map<String, List<String>> = headers

                override fun url(): String = url
            }
        val response = apiExecutor.execute(request).getOrThrow()
        val body =
            response.body?.decodeToString()
                ?: throw IllegalStateException("Empty response body")
        json.decodeFromString(deserializer, body)
    }.fold(
        onSuccess = { OAuth2ClientResult.Success(it) },
        onFailure = { OAuth2ClientResult.Error(it as Exception) }
    )

/**
 * Executes a JSON POST form request and deserializes the response.
 */
@OptIn(InternalAuthFoundationApi::class)
internal suspend fun <T> performJsonFormPost(
    apiExecutor: ApiExecutor,
    json: Json,
    url: String,
    formParams: Map<String, String>,
    deserializer: DeserializationStrategy<T>,
): OAuth2ClientResult<T> =
    runCatching {
        val request =
            object : ApiFormRequest {
                override fun method(): ApiRequestMethod = ApiRequestMethod.POST

                override fun headers(): Map<String, List<String>> = mapOf("Accept" to listOf("application/json"))

                override fun url(): String = url

                override fun contentType(): String = "application/x-www-form-urlencoded"

                override fun formParameters(): Map<String, List<String>> = formParams.mapValues { (_, v) -> listOf(v) }
            }
        val response = apiExecutor.execute(request).getOrThrow()
        val body =
            response.body?.decodeToString()
                ?: throw IllegalStateException("Empty response body")
        json.decodeFromString(deserializer, body)
    }.fold(
        onSuccess = { OAuth2ClientResult.Success(it) },
        onFailure = { OAuth2ClientResult.Error(it as Exception) }
    )

/**
 * Executes a POST form request that doesn't return JSON (e.g., revocation).
 */
@OptIn(InternalAuthFoundationApi::class)
internal suspend fun performFormPost(
    apiExecutor: ApiExecutor,
    url: String,
    formParams: Map<String, String>,
): OAuth2ClientResult<Unit> =
    runCatching {
        val request =
            object : ApiFormRequest {
                override fun method(): ApiRequestMethod = ApiRequestMethod.POST

                override fun headers(): Map<String, List<String>> = emptyMap()

                override fun url(): String = url

                override fun contentType(): String = "application/x-www-form-urlencoded"

                override fun formParameters(): Map<String, List<String>> = formParams.mapValues { (_, v) -> listOf(v) }
            }
        val response = apiExecutor.execute(request).getOrThrow()
        if (response.statusCode !in 200..299) {
            throw IllegalStateException("Request failed with status ${response.statusCode}")
        }
    }.fold(
        onSuccess = { OAuth2ClientResult.Success(Unit) },
        onFailure = { OAuth2ClientResult.Error(it as Exception) }
    )
