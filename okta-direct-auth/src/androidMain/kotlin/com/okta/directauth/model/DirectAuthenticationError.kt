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
    class InternalError internal constructor(val errorCode: String, val description: String? = null, val throwable: Throwable) : DirectAuthenticationError()

    /**
     * Represents an HTTP error that occurred during the direct authentication process.
     *
     * @param error A string representing the error.
     * @param httpStatusCode The [HttpStatusCode] associated with this error.
     */
    sealed class HttpError(val httpStatusCode: HttpStatusCode) : DirectAuthenticationError() {

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