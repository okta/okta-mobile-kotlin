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
package com.okta.oauth2

import android.net.Uri
import com.okta.authfoundation.client.OidcClient
import com.okta.oauth2.events.CustomizeLogoutUrlEvent
import okhttp3.HttpUrl
import java.util.UUID

/**
 * [RedirectEndSessionFlow] encapsulates the behavior required to logout using an OIDC Browser redirect flow.
 *
 * > Note: OIDC Logout terminology is nuanced, see [Logout Documentation](https://github.com/okta/okta-mobile-kotlin#logout) for additional details.
 */
class RedirectEndSessionFlow private constructor(
    private val oidcClient: OidcClient,
) {
    companion object {
        /**
         * Initializes an end session redirect flow using the [OidcClient].
         *
         * @receiver the [OidcClient] used to perform the low level OIDC requests, as well as with which to use the configuration from.
         */
        fun OidcClient.createRedirectEndSessionFlow(): RedirectEndSessionFlow {
            return RedirectEndSessionFlow(this)
        }
    }

    /**
     * A model representing all the possible states of a [RedirectEndSessionFlow.resume] call.
     */
    sealed class ResumeResult {
        /**
         * An error that occurs when the [OidcClient] hasn't been properly configured, or a network error occurs.
         */
        object EndpointsNotAvailable : ResumeResult()

        /**
         * A model representing the context and current state for a logout flow.
         */
        class Context internal constructor(
            /**
             * The url which should be used to log the user out.
             */
            val url: HttpUrl,
            internal val state: String,
        ) : ResumeResult()
    }

    /**
     * A model representing all possible states of a [RedirectEndSessionFlow.resume] call.
     */
    sealed class Result {
        /**
         * An error indicating the redirect scheme of the supplied url doesn't match the configured redirect scheme.
         *
         * This could be due to the supplied url being intended for another feature of the app, a misconfiguration, or an attempted
         * attack.
         */
        object RedirectSchemeMismatch : Result()

        /**
         * An error resulting from an interaction with the Authorization Server.
         */
        class Error internal constructor(
            /**
             * An error message intended to be displayed to the user.
             */
            val message: String,
        ) : Result()

        /**
         * Represents a successful logout.
         */
        object Success : Result()
    }

    /**
     * Initiates the logout redirect flow.
     *
     * See [RedirectEndSessionFlow.resume] for completing the flow.
     *
     * @param idToken the token used to identify the session to log the user out of.
     */
    suspend fun start(idToken: String): ResumeResult {
        return start(idToken, UUID.randomUUID().toString())
    }

    internal suspend fun start(
        idToken: String,
        state: String,
    ): ResumeResult {
        val endpoints = oidcClient.endpointsOrNull() ?: return ResumeResult.EndpointsNotAvailable

        val urlBuilder = endpoints.endSessionEndpoint.newBuilder()
        urlBuilder.addQueryParameter("id_token_hint", idToken)
        urlBuilder.addQueryParameter("post_logout_redirect_uri", oidcClient.configuration.signOutRedirectUri)
        urlBuilder.addQueryParameter("state", state)

        val event = CustomizeLogoutUrlEvent(urlBuilder)
        oidcClient.configuration.eventCoordinator.sendEvent(event)

        return ResumeResult.Context(urlBuilder.build(), state)
    }

    /**
     * Resumes the logout redirect flow.
     *
     * @param uri the redirect [Uri] which includes the state to validate the logout was successful.
     * @param flowContext the [RedirectEndSessionFlow.ResumeResult.Context] used internally to maintain state.
     */
    fun resume(uri: Uri, flowContext: ResumeResult.Context): Result {
        if (!uri.toString().startsWith(oidcClient.configuration.signOutRedirectUri)) {
            return Result.RedirectSchemeMismatch
        }

        val errorQueryParameter = uri.getQueryParameter("error")
        if (errorQueryParameter != null) {
            val errorDescription = uri.getQueryParameter("error_description") ?: "An error occurred."
            return Result.Error(errorDescription)
        }

        val stateQueryParameter = uri.getQueryParameter("state")
        if (flowContext.state != stateQueryParameter) {
            val error = "Failed due to state mismatch."
            return Result.Error(error)
        }

        return Result.Success
    }
}
