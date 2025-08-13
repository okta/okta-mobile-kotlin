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
 * An extension of [ApiRequest] for requests that include a body.
 */
interface ApiRequestBody : ApiRequest {
    /**
     * The MIME type of the request body (e.g., `application/json`).
     *
     * This is typically used for methods like POST or PUT to indicate the format of the body content.
     */
    fun contentType(): String

    /**
     * Returns the body of the request as a byte array.
     *
     * This is typically used for methods like POST or PUT with content types like `application/json`.
     *
     * @return The request body, or null if the request has no body.
     */
    fun body(): ByteArray
}
