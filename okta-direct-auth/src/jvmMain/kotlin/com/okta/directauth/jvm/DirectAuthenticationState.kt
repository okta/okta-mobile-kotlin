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
package com.okta.directauth.jvm

import com.okta.authfoundation.client.TokenInfo
import com.okta.directauth.model.DirectAuthContinuation
import com.okta.directauth.model.DirectAuthenticationError
import io.ktor.http.HttpStatusCode
import com.okta.directauth.model.DirectAuthenticationState as KotlinDirectAuthenticationState

/**
 * A Java-friendly base type for authentication states returned at JVM API boundaries.
 *
 * Every Kotlin [KotlinDirectAuthenticationState] subtype has a corresponding JVM wrapper class
 * that Java callers can use with `instanceof` checks, avoiding name collisions with the
 * Kotlin sealed interface.
 *
 * Use [state] to access the underlying Kotlin [KotlinDirectAuthenticationState].
 *
 * @param state The underlying Kotlin [KotlinDirectAuthenticationState].
 */
sealed class DirectAuthenticationState(
    val state: KotlinDirectAuthenticationState,
) {
    /**
     * The initial state of the authentication flow, before any action has been taken.
     */
    class Idle internal constructor(
        delegate: KotlinDirectAuthenticationState.Idle,
    ) : DirectAuthenticationState(delegate)

    /**
     * The authentication flow has been canceled.
     */
    class Canceled internal constructor(
        delegate: KotlinDirectAuthenticationState.Canceled,
    ) : DirectAuthenticationState(delegate)

    /**
     * The authentication process is still pending and awaiting user action.
     *
     * @param timestamp The time in milliseconds when the authorization became pending.
     */
    class AuthorizationPending internal constructor(
        delegate: KotlinDirectAuthenticationState.AuthorizationPending,
    ) : DirectAuthenticationState(delegate) {
        val timestamp: Long = delegate.timestamp
    }

    /**
     * The user has been authenticated and tokens have been issued.
     *
     * @param token The [TokenInfo] containing the access, refresh, and ID tokens.
     */
    class Authenticated internal constructor(
        delegate: KotlinDirectAuthenticationState.Authenticated,
    ) : DirectAuthenticationState(delegate) {
        val token: TokenInfo = delegate.token
    }

    /**
     * A Java-friendly base type for authentication error states.
     */
    sealed class Error(
        delegate: DirectAuthenticationError,
    ) : DirectAuthenticationState(delegate) {
        /**
         * An internal error caused by unexpected conditions such as network failures or JSON parsing issues.
         *
         * @param errorCode A string representing the specific error code.
         * @param description An optional description of the error.
         * @param throwable The [Throwable] that caused this error.
         */
        class InternalError internal constructor(
            delegate: DirectAuthenticationError.InternalError,
        ) : Error(delegate) {
            val errorCode: String = delegate.errorCode
            val description: String? = delegate.description
            val throwable: Throwable = delegate.throwable
        }

        /**
         * A Java-friendly base type for HTTP error states.
         *
         * @param httpStatusCode The HTTP status code associated with this error.
         */
        sealed class HttpError(
            delegate: DirectAuthenticationError.HttpError,
        ) : Error(delegate) {
            val httpStatusCode: HttpStatusCode = delegate.httpStatusCode

            /**
             * An Okta API-specific error.
             *
             * @see <a href="https://developer.okta.com/docs/reference/api/error-codes/">Okta Error Codes</a>
             */
            class ApiError internal constructor(
                delegate: DirectAuthenticationError.HttpError.ApiError,
            ) : HttpError(delegate) {
                val errorCode: String = delegate.errorCode
                val errorSummary: String? = delegate.errorSummary
                val errorLink: String? = delegate.errorLink
                val errorId: String? = delegate.errorId
                val errorCauses: List<String>? = delegate.errorCauses
            }

            /**
             * An OAuth2-specific error.
             *
             * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1">RFC 6749</a>
             */
            class Oauth2Error internal constructor(
                delegate: DirectAuthenticationError.HttpError.Oauth2Error,
            ) : HttpError(delegate) {
                val error: String = delegate.error
                val errorDescription: String? = delegate.errorDescription
            }
        }
    }
}

/**
 * Wraps a Kotlin [KotlinDirectAuthenticationState] in a [DirectAuthenticationState] for Java consumption.
 *
 * Each Kotlin state subtype is mapped to its corresponding JVM wrapper class.
 */
internal fun KotlinDirectAuthenticationState.toJvm(): DirectAuthenticationState =
    when (this) {
        is KotlinDirectAuthenticationState.Idle -> DirectAuthenticationState.Idle(this)
        is KotlinDirectAuthenticationState.Canceled -> DirectAuthenticationState.Canceled(this)
        is KotlinDirectAuthenticationState.AuthorizationPending -> DirectAuthenticationState.AuthorizationPending(this)
        is KotlinDirectAuthenticationState.Authenticated -> DirectAuthenticationState.Authenticated(this)
        is KotlinDirectAuthenticationState.MfaRequired -> MfaRequired(this)
        is DirectAuthContinuation.WebAuthn -> WebAuthnContinuation(this)
        is DirectAuthContinuation.Prompt -> PromptContinuation(this)
        is DirectAuthContinuation.Transfer -> TransferContinuation(this)
        is DirectAuthContinuation.OobPending -> OobPendingContinuation(this)
        is DirectAuthenticationError.HttpError.ApiError -> DirectAuthenticationState.Error.HttpError.ApiError(this)
        is DirectAuthenticationError.HttpError.Oauth2Error -> DirectAuthenticationState.Error.HttpError.Oauth2Error(this)
        is DirectAuthenticationError.InternalError -> DirectAuthenticationState.Error.InternalError(this)
    }
