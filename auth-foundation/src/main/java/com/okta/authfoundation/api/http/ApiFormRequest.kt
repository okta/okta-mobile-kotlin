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
 * An extension of [ApiRequest] for requests that include form parameters in the body.
 */
interface ApiFormRequest : ApiRequest {
    /**
     * The MIME type of the request body (e.g., `application/x-www-form-urlencoded`).
     *
     * This is typically used for methods like POST or PUT to indicate the format of the body content.
     */
    fun contentType(): String

    /**
     * Returns a map of form parameters to be sent as the request body with a
     * `application/x-www-form-urlencoded` content type.
     *
     * This allows for multiple values per key, which will be encoded as repeated key-value pairs
     * in the request body (e.g., `key=value1&key=value2`).
     *
     * @return A map of form parameters, or null if there are none.
     */
    fun formParameters(): Map<String, List<String>>
}
