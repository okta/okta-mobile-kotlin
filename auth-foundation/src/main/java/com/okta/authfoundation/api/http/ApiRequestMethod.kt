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
 * Represents the standard HTTP methods that can be used in an [ApiRequest].
 */
enum class ApiRequestMethod {
    /** The HTTP GET method, used to request data from a specified resource. */
    GET,

    /** The HTTP POST method, used to send data to a server to create a new resource. */
    POST,

    /** The HTTP PUT method, used to update a resource or create a new one if it does not exist. */
    PUT,

    /** The HTTP DELETE method, used to delete a specified resource. */
    DELETE,

    /** The HTTP PATCH method, used to apply partial modifications to a resource. */
    PATCH,

    /** The HTTP HEAD method, used to retrieve the headers of a resource without the body. */
    HEAD,
}
