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
import com.okta.authfoundation.client.OidcClientResult
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
     * A model representing the context and current state for a logout flow.
     */
    class Context internal constructor(
        /**
         * The url which should be used to log the user out.
         */
        val url: HttpUrl,
        internal val state: String,
    )

    /**
     * Used in a [OidcClientResult.Error.exception] from [resume].
     *
     * Includes a message giving more information as to what went wrong.
     */
    class ResumeException internal constructor(message: String) : Exception(message)

    /**
     * Used in a [OidcClientResult.Error.exception] from [resume].
     *
     * The redirect scheme of the [Uri] didn't match the one configured for the associated [OidcClient].
     */
    class RedirectSchemeMismatchException internal constructor() : Exception()

    /**
     * Initiates the logout redirect flow.
     *
     * See [RedirectEndSessionFlow.resume] for completing the flow.
     *
     * @param idToken the token used to identify the session to log the user out of.
     */
    suspend fun start(idToken: String): OidcClientResult<Context> {
        return start(idToken, UUID.randomUUID().toString())
    }

    internal suspend fun start(
        idToken: String,
        state: String,
    ): OidcClientResult<Context> {
        val endpoint = oidcClient.endpointsOrNull()?.endSessionEndpoint ?: return oidcClient.endpointNotAvailableError()

        val urlBuilder = endpoint.newBuilder()
        urlBuilder.addQueryParameter("id_token_hint", idToken)
        urlBuilder.addQueryParameter("post_logout_redirect_uri", oidcClient.configuration.signOutRedirectUri)
        urlBuilder.addQueryParameter("state", state)

        return OidcClientResult.Success(Context(urlBuilder.build(), state))
    }

    /**
     * Resumes the logout redirect flow.
     *
     * @param uri the redirect [Uri] which includes the state to validate the logout was successful.
     * @param flowContext the [RedirectEndSessionFlow.Context] used internally to maintain state.
     */
    fun resume(uri: Uri, flowContext: Context): OidcClientResult<Unit> {
        if (!uri.toString().startsWith(oidcClient.configuration.signOutRedirectUri)) {
            return OidcClientResult.Error(RedirectSchemeMismatchException())
        }

        val errorQueryParameter = uri.getQueryParameter("error")
        if (errorQueryParameter != null) {
            val errorDescription = uri.getQueryParameter("error_description") ?: "An error occurred."
            return OidcClientResult.Error(ResumeException(errorDescription))
        }

        val stateQueryParameter = uri.getQueryParameter("state")
        if (flowContext.state != stateQueryParameter) {
            val error = "Failed due to state mismatch."
            return OidcClientResult.Error(ResumeException(error))
        }

        return OidcClientResult.Success(Unit)
    }
}
