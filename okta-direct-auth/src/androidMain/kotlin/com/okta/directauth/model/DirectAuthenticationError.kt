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
package com.okta.directauth.model

import io.ktor.http.HttpStatusCode

/**
 * Represents the various error states of direct authentication.
 */
sealed class DirectAuthenticationError : DirectAuthenticationState {
    /**
     * Represents an internal error that occurred during the direct authentication process.
     * This is typically used for non-HTTP related errors caused by unexpected conditions such as network failures or JSON parsing issues.
     *
     * @param errorCode A string representing the specific error code.
     * @param description An optional description of the error.
     * @param throwable The [Throwable] that caused this error.
     */
    class InternalError internal constructor(
        val errorCode: String,
        val description: String? = null,
        val throwable: Throwable,
    ) : DirectAuthenticationError()

    /**
     * Represents an HTTP error that occurred during the direct authentication process.
     *
     * @param error A string representing the error.
     * @param httpStatusCode The [HttpStatusCode] associated with this error.
     */
    sealed class HttpError(
        val httpStatusCode: HttpStatusCode,
    ) : DirectAuthenticationError() {
        /**
         * An API-specific error that occurred during the direct authentication process.
         *
         * https://developer.okta.com/docs/reference/api/error-codes/
         *
         * @param errorCode A string representing the specific error code.
         * @param errorSummary An optional summary of the error.
         * @param errorLink An optional link to more information about the error.
         * @param errorId An optional unique identifier for the error instance.
         * @param errorCauses An optional list of causes for the error.
         * @param httpStatusCode The [HttpStatusCode] associated with this error.
         */
        class ApiError(
            val errorCode: String,
            val errorSummary: String?,
            val errorLink: String?,
            val errorId: String?,
            val errorCauses: List<String>?,
            httpStatusCode: HttpStatusCode,
        ) : HttpError(httpStatusCode)

        /**
         * An OAuth2-specific error that occurred during the direct authentication process.
         *
         * https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1
         *
         * @param error A string representing the error.
         * @param errorDescription An optional description of the error.
         * @param httpStatusCode The [HttpStatusCode] associated with this error.
         */
        class Oauth2Error(
            val error: String,
            httpStatusCode: HttpStatusCode,
            val errorDescription: String?,
        ) : HttpError(httpStatusCode)
    }
}
