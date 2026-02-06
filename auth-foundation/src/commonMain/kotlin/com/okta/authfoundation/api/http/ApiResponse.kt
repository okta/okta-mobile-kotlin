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
package com.okta.authfoundation.api.http

/**
 * Represents the response from an HTTP API request executed by an [ApiExecutor].
 *
 * This interface provides access to all components of the response, including the
 * status code, headers, and body.
 */
interface ApiResponse {
    /** The HTTP status code from the server's response (e.g., 200 for OK). */
    val statusCode: Int

    /** The raw response body as a byte array, or null if there is no body. */
    val body: ByteArray?

    /** A map of all response headers. */
    val headers: Map<String, List<String>>

    /** The length of the response body in bytes, as reported by the `Content-Length` header. */
    val contentLength: Long

    /** The MIME type of the response body, as reported by the `Content-Type` header. */
    val contentType: String
}
