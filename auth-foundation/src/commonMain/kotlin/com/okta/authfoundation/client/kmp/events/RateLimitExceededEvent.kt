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
package com.okta.authfoundation.client.kmp.events

import com.okta.authfoundation.events.Event

/**
 * Emitted when an HTTP 429 (Too Many Requests) response is received from the
 * authorization server.
 *
 * Consumers can collect this event from [com.okta.authfoundation.client.kmp.OAuth2Client.events]
 * to implement observability, logging, or custom retry logic.
 *
 * @param requestUrl the URL of the request that was rate-limited.
 * @param statusCode the HTTP status code (429).
 * @param responseHeaders all response headers from the rate-limited response.
 * @param retryAfterSeconds the parsed `Retry-After` header value in seconds, or `null` if absent.
 */
class RateLimitExceededEvent internal constructor(
    val requestUrl: String,
    val statusCode: Int,
    val responseHeaders: Map<String, List<String>>,
    val retryAfterSeconds: Long?,
) : Event
