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
import com.okta.authfoundation.client.kmp.events.RateLimitExceededEvent
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

private val defaultHeaders: Map<String, List<String>> =
    mapOf("User-Agent" to listOf(UserAgent.value))

/**
 * Parses the Retry-After header value into seconds.
 *
 * Per RFC 7231, Retry-After can be either:
 * - A delay in seconds: "120"
 * - An HTTP-date: "Wed, 21 Oct 2025 07:28:00 GMT"
 *
 * @return the delay in seconds, or null if unparseable.
 */
internal fun parseRetryAfterHeader(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return try {
        // Try parsing as integer seconds first
        value.toLong()
    } catch (e: NumberFormatException) {
        // If not an integer, treat as HTTP-date and estimate from current time
        // For now, return null. In production, would parse HTTP-date.
        null
    }
}

/**
 * Executes a JSON GET request and deserializes the response.
 *
 * @param apiExecutor the HTTP executor.
 * @param json the JSON serializer.
 * @param url the request URL.
 * @param deserializer the response deserializer.
 * @param headers additional request headers.
 * @param onRateLimitExceeded optional callback fired when HTTP 429 is detected.
 */
@OptIn(InternalAuthFoundationApi::class)
internal suspend fun <T> performJsonGetRequest(
    apiExecutor: ApiExecutor,
    json: Json,
    url: String,
    deserializer: DeserializationStrategy<T>,
    headers: Map<String, List<String>> = mapOf("Accept" to listOf("application/json")),
    onRateLimitExceeded: ((RateLimitExceededEvent) -> Unit)? = null,
): OAuth2ClientResult<T> =
    runCatching {
        val mergedHeaders = defaultHeaders + headers
        val request =
            object : ApiRequest {
                override fun method(): ApiRequestMethod = ApiRequestMethod.GET

                override fun headers(): Map<String, List<String>> = mergedHeaders

                override fun url(): String = url
            }
        val response = apiExecutor.execute(request).getOrThrow()

        // Check for rate limit (HTTP 429)
        if (response.statusCode == 429) {
            val retryAfter = parseRetryAfterHeader(response.headers["Retry-After"]?.firstOrNull())
            val event = RateLimitExceededEvent(url, 429, response.headers, retryAfter)
            onRateLimitExceeded?.invoke(event)
            throw IllegalStateException("HTTP 429: Too Many Requests")
        }

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
 *
 * @param apiExecutor the HTTP executor.
 * @param json the JSON serializer.
 * @param url the request URL.
 * @param formParams the form parameters.
 * @param deserializer the response deserializer.
 * @param onRateLimitExceeded optional callback fired when HTTP 429 is detected.
 */
@OptIn(InternalAuthFoundationApi::class)
internal suspend fun <T> performJsonFormPost(
    apiExecutor: ApiExecutor,
    json: Json,
    url: String,
    formParams: Map<String, String>,
    deserializer: DeserializationStrategy<T>,
    onRateLimitExceeded: ((RateLimitExceededEvent) -> Unit)? = null,
): OAuth2ClientResult<T> =
    runCatching {
        val mergedHeaders = defaultHeaders + mapOf("Accept" to listOf("application/json"))
        val request =
            object : ApiFormRequest {
                override fun method(): ApiRequestMethod = ApiRequestMethod.POST

                override fun headers(): Map<String, List<String>> = mergedHeaders

                override fun url(): String = url

                override fun contentType(): String = "application/x-www-form-urlencoded"

                override fun formParameters(): Map<String, List<String>> = formParams.mapValues { (_, v) -> listOf(v) }
            }
        val response = apiExecutor.execute(request).getOrThrow()

        // Check for rate limit (HTTP 429)
        if (response.statusCode == 429) {
            val retryAfter = parseRetryAfterHeader(response.headers["Retry-After"]?.firstOrNull())
            val event = RateLimitExceededEvent(url, 429, response.headers, retryAfter)
            onRateLimitExceeded?.invoke(event)
            throw IllegalStateException("HTTP 429: Too Many Requests")
        }

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
 *
 * @param apiExecutor the HTTP executor.
 * @param url the request URL.
 * @param formParams the form parameters.
 * @param onRateLimitExceeded optional callback fired when HTTP 429 is detected.
 */
@OptIn(InternalAuthFoundationApi::class)
internal suspend fun performFormPost(
    apiExecutor: ApiExecutor,
    url: String,
    formParams: Map<String, String>,
    onRateLimitExceeded: ((RateLimitExceededEvent) -> Unit)? = null,
): OAuth2ClientResult<Unit> =
    runCatching {
        val request =
            object : ApiFormRequest {
                override fun method(): ApiRequestMethod = ApiRequestMethod.POST

                override fun headers(): Map<String, List<String>> = defaultHeaders

                override fun url(): String = url

                override fun contentType(): String = "application/x-www-form-urlencoded"

                override fun formParameters(): Map<String, List<String>> = formParams.mapValues { (_, v) -> listOf(v) }
            }
        val response = apiExecutor.execute(request).getOrThrow()

        // Check for rate limit (HTTP 429)
        if (response.statusCode == 429) {
            val retryAfter = parseRetryAfterHeader(response.headers["Retry-After"]?.firstOrNull())
            val event = RateLimitExceededEvent(url, 429, response.headers, retryAfter)
            onRateLimitExceeded?.invoke(event)
            throw IllegalStateException("HTTP 429: Too Many Requests")
        }

        if (response.statusCode !in 200..299) {
            throw IllegalStateException("Request failed with status ${response.statusCode}")
        }
    }.fold(
        onSuccess = { OAuth2ClientResult.Success(Unit) },
        onFailure = { OAuth2ClientResult.Error(it as Exception) }
    )
