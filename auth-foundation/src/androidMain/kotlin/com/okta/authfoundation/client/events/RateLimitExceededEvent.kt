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
package com.okta.authfoundation.client.events

import com.okta.authfoundation.events.Event
import com.okta.authfoundation.events.EventHandler
import okhttp3.Request
import okhttp3.Response

/**
 * Emitted via [EventHandler.onEvent] when a network request receives 429 HTTP status code (rate limit exceeded).
 * See [Rate Limits Overview](https://developer.okta.com/docs/reference/rate-limits/) for additional details.
 *
 * The default implementation retries the request 3 times, with a minimum delay of 1 seconds between each request.
 * See [maxRetries] and [minDelaySeconds]
 */
class RateLimitExceededEvent internal constructor(
    /**
     * The [Request] that caused the event.
     */
    val request: Request,
    /**
     * The [Response] with 429 HTTP status code.
     */
    val response: Response,
    /**
     * Number of retry attempts for [request] so far.
     */
    val retryCount: Int,
    /**
     * Allows the app developer to change how many times, at most, [request] should be retried
     * when a 429 status code is received.
     */
    var maxRetries: Int = 3,
    /**
     * Allows the app developer to change the minimum amount of time, in seconds, to wait before
     * retrying [request] when a 429 status code is received.
     */
    var minDelaySeconds: Long = 1L,
) : Event
