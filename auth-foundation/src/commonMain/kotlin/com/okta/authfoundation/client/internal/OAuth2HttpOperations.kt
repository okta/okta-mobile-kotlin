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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.toInstant
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

private val defaultHeaders: Map<String, List<String>> =
    mapOf("User-Agent" to listOf(UserAgent.value))

@Serializable
internal class ErrorResponse(
    @SerialName("error") val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

private val HTTP_DATE_MONTHS =
    mapOf(
        "Jan" to 1,
        "Feb" to 2,
        "Mar" to 3,
        "Apr" to 4,
        "May" to 5,
        "Jun" to 6,
        "Jul" to 7,
        "Aug" to 8,
        "Sep" to 9,
        "Oct" to 10,
        "Nov" to 11,
        "Dec" to 12
    )

/**
 * Parses the Retry-After header value into delay seconds.
 *
 * Per RFC 7231 §7.1.3, Retry-After can be either:
 * - A delay in seconds: "120"
 * - An HTTP-date: "Wed, 21 Oct 2025 07:28:00 GMT"
 *
 * All three HTTP-date formats from RFC 7231 §7.1.1 are supported:
 * - IMF-fixdate: "Wed, 21 Oct 2025 07:28:00 GMT"
 * - RFC 850 (obsolete): "Wednesday, 21-Oct-25 07:28:00 GMT"
 * - ANSI C asctime (obsolete): "Wed Oct 21 07:28:00 2025"
 *
 * @param currentEpochSeconds the current time in epoch seconds, used to compute the delay
 *   from an HTTP-date. Defaults to the system clock.
 * @return the delay in seconds (≥ 0), or null if unparseable.
 */
internal fun parseRetryAfterHeader(
    value: String?,
    currentEpochSeconds: Long = Clock.System.now().epochSeconds,
): Long? {
    if (value.isNullOrBlank()) return null

    // 1. Try delay-seconds format (e.g., "120")
    value.toLongOrNull()?.let { return it }

    // 2. Try HTTP-date formats per RFC 7231 §7.1.1
    val targetEpochSeconds = parseHttpDate(value) ?: return null
    val delaySeconds = targetEpochSeconds - currentEpochSeconds
    return if (delaySeconds > 0) delaySeconds else 0L
}

/**
 * Parses an HTTP-date string per RFC 7231 §7.1.1 and returns epoch seconds, or null on failure.
 *
 * Tries the three formats defined by RFC 7231 in preference order:
 * 1. IMF-fixdate: "Wed, 21 Oct 2015 07:28:00 GMT"
 * 2. RFC 850 (obsolete): "Wednesday, 21-Oct-15 07:28:00 GMT"
 * 3. ANSI C asctime (obsolete): "Wed Oct 21 07:28:00 2015"
 */
internal fun parseHttpDate(value: String): Long? {
    // IMF-fixdate: handled by kotlinx-datetime's RFC_1123 parser.
    // Also accepts omitted day-of-week, "UT", "Z", and UTC offset variants.
    runCatching {
        DateTimeComponents.Formats.RFC_1123
            .parse(value)
            .toInstantUsingOffset()
            .epochSeconds
    }.getOrNull()?.let { return it }

    parseRfc850(value)?.let { return it }
    parseAsctime(value)?.let { return it }

    return null
}

/**
 * Parses RFC 850 HTTP-date: "DAYNAME, DD-Mon-YY HH:MM:SS GMT"
 *
 * Per RFC 7231 §7.1.1, 2-digit years ≥ 70 map to 1970–1999; years < 70 map to 2000–2069.
 */
private fun parseRfc850(value: String): Long? {
    val commaIdx = value.indexOf(", ")
    if (commaIdx < 0) return null
    val rest = value.substring(commaIdx + 2)
    // "21-Oct-15 07:28:00 GMT" → split on "-", " " and ":"
    val parts = rest.split("-", " ", ":")
    if (parts.size < 7) return null
    return runCatching {
        val day = parts[0].toInt()
        val month = HTTP_DATE_MONTHS[parts[1]] ?: return null
        val twoDigitYear = parts[2].toInt()
        val year = if (twoDigitYear >= 70) 1900 + twoDigitYear else 2000 + twoDigitYear
        val hour = parts[3].toInt()
        val minute = parts[4].toInt()
        val second = parts[5].toInt()
        LocalDateTime(year, month, day, hour, minute, second).toInstant(TimeZone.UTC).epochSeconds
    }.getOrNull()
}

/**
 * Parses ANSI C asctime HTTP-date: "DDD Mon [D]D HH:MM:SS YYYY"
 *
 * The day-of-month may be space-padded (single digit with leading space), which
 * split(Regex("\\s+")) handles naturally.
 */
private fun parseAsctime(value: String): Long? {
    val parts = value.trim().split(Regex("\\s+"))
    if (parts.size != 5) return null
    return runCatching {
        val month = HTTP_DATE_MONTHS[parts[1]] ?: return null
        val day = parts[2].toInt()
        val timeParts = parts[3].split(":")
        if (timeParts.size != 3) return null
        val hour = timeParts[0].toInt()
        val minute = timeParts[1].toInt()
        val second = timeParts[2].toInt()
        val year = parts[4].toInt()
        LocalDateTime(year, month, day, hour, minute, second).toInstant(TimeZone.UTC).epochSeconds
    }.getOrNull()
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
            throw OAuth2ClientResult.Error.RateLimitException(url, retryAfter)
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
            throw OAuth2ClientResult.Error.RateLimitException(url, retryAfter)
        }

        val body =
            response.body?.decodeToString()
                ?: throw IllegalStateException("Empty response body")

        if (response.statusCode !in 200..299) {
            val errorResponse = runCatching { json.decodeFromString(ErrorResponse.serializer(), body) }.getOrNull()
            throw OAuth2ClientResult.Error.HttpResponseException(
                responseCode = response.statusCode,
                error = errorResponse?.error,
                errorDescription = errorResponse?.errorDescription
            )
        }

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
            throw OAuth2ClientResult.Error.RateLimitException(url, retryAfter)
        }

        if (response.statusCode !in 200..299) {
            throw IllegalStateException("Request failed with status ${response.statusCode}")
        }
    }.fold(
        onSuccess = { OAuth2ClientResult.Success(Unit) },
        onFailure = { OAuth2ClientResult.Error(it as Exception) }
    )
