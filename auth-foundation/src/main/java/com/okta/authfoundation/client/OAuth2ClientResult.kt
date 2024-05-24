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

import com.okta.authfoundation.InternalAuthFoundationApi

/**
 * Describes the result from the OAuth2Client.
 */
sealed class OAuth2ClientResult<T> {
    /** An error result. */
    class Error<T> @InternalAuthFoundationApi constructor(
        /** The exception associated with the error. */
        val exception: Exception,
    ) : OAuth2ClientResult<T>() {
        /**
         * The response type used to represent a completed HTTP response, but a non successful status code.
         */
        class HttpResponseException internal constructor(
            /** The HTTP response code associated with the error. */
            val responseCode: Int,
            /** The error returned by the Authorization Server. */
            val error: String?,
            /** The error description returned by the Authorization Server. */
            val errorDescription: String?,
        ) : Exception(errorDescription ?: error ?: "HTTP Error: status code - $responseCode")

        /**
         * The response failed due to no [OidcEndpoints].
         *
         * This can happen due to a misconfigured setup, or just a common HTTP error.
         */
        class OidcEndpointsNotAvailableException internal constructor() : Exception("OIDC Endpoints not available.")
    }

    /** Success with the expected result. */
    class Success<T> @InternalAuthFoundationApi constructor(
        /** The result of the success result. */
        val result: T,
    ) : OAuth2ClientResult<T>()

    /**
     * Returns the encapsulated value if this instance represents [Success] or throws the encapsulated [Exception] if it is [Error].
     */
    fun getOrThrow(): T {
        when (this) {
            is Error -> {
                throw exception
            }
            is Success -> {
                return result
            }
        }
    }
}
