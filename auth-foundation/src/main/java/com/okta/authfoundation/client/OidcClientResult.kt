/*
 * Copyright 2021-Present Okta, Inc.
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
package com.okta.authfoundation.client

/**
 * Describes the result from the OidcClient.
 */
sealed class OidcClientResult<T> {
    /** An error result. */
    data class Error<T> internal constructor(
        /** The exception associated with the error. */
        val exception: Exception,
    ) : OidcClientResult<T>() {
        /**
         * The response type used to represent a completed HTTP response, but a non successful status code.
         */
        data class HttpResponseException internal constructor(
            /** The HTTP response code associated with the error. */
            val responseCode: Int,
            /** The error returned by the Authorization Server. */
            val error: String?,
            /** The error description returned by the Authorization Server. */
            val errorDescription: String?,
        ) : Exception()
    }

    /** Success with the expected result. */
    data class Success<T> internal constructor(
        /** The result of the success result. */
        val result: T,
    ) : OidcClientResult<T>()
}
