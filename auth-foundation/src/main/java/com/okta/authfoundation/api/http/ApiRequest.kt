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
 * A specification for an HTTP API request to be executed by an [ApiExecutor].
 *
 * This interface defines all the necessary components of a request, such as the
 * target URI, HTTP method, headers, and body.
 */
interface ApiRequest {
    /**
     * The HTTP method to be used for the request (e.g., GET, POST).
     *
     * @return The HTTP method as an [ApiRequestMethod].
     */
    fun method(): ApiRequestMethod

    /**
     * Returns a map of HTTP headers to be included with the request.
     *
     * @return A map of header names to their corresponding values.
     */
    fun headers(): Map<String, List<String>>

    /**
     * Returns the complete URL for the request, including the scheme, host, and path.
     *
     * @return The request URL as a string.
     */
    fun url(): String

    /**
     * Returns a map of URL query parameters to be appended to the request URI.
     *
     * @return A map of query parameters, or null if there are none.
     */
    fun query(): Map<String, String>? = null
}
