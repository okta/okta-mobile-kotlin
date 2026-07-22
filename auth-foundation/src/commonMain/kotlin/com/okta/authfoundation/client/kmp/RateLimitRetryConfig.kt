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
package com.okta.authfoundation.client.kmp

/**
 * A non-negative count of retry attempts, enforced at construction time.
 *
 * @param value the number of retries; must be >= 0.
 */
@JvmInline
value class MaxRetries(
    val value: Int,
) {
    init {
        require(value >= 0) { "maxRetries must be >= 0, was $value" }
    }
}

/**
 * A non-negative minimum delay in seconds between retry attempts, enforced at construction time.
 *
 * @param value the delay in seconds; must be >= 0.
 */
@JvmInline
value class MinDelaySeconds(
    val value: Long,
) {
    init {
        require(value >= 0) { "minDelaySeconds must be >= 0, was $value" }
    }
}

/**
 * Configuration returned by [OAuth2ClientConfiguration.rateLimitRetryCallback] to control
 * retry behavior after an HTTP 429 rate-limit response.
 *
 * Return this from the callback to retry; return `null` to stop retrying and surface the failure.
 *
 * @param maxRetries the maximum number of retry attempts after the initial 429 response.
 * @param minDelaySeconds the minimum wait time in seconds before each retry attempt. The actual
 *   delay is `max(retryAfterHeader, minDelaySeconds.value)`.
 */
data class RateLimitRetryConfig(
    val maxRetries: MaxRetries,
    val minDelaySeconds: MinDelaySeconds,
)
