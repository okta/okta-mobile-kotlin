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

class RedirectEndSessionFlow private constructor(
    private val oidcClient: OidcClient,
) {
    companion object {
        fun OidcClient.redirectEndSessionFlow(): RedirectEndSessionFlow {
            return RedirectEndSessionFlow(this)
        }
    }

    class Context internal constructor(
        internal val state: String,
        val url: HttpUrl,
    )

    sealed class Result {
        object RedirectSchemeMismatch : Result()
        class Error(val message: String, val exception: Exception? = null) : Result()
        object MissingResultCode : Result()
        object Success : Result()
    }

    fun start(idToken: String): Context {
        return start(idToken, UUID.randomUUID().toString())
    }

    internal fun start(
        idToken: String,
        state: String,
    ): Context {
        val urlBuilder = oidcClient.endpoints.endSessionEndpoint.newBuilder()
        urlBuilder.addQueryParameter("id_token_hint", idToken)
        urlBuilder.addQueryParameter("post_logout_redirect_uri", oidcClient.configuration.signOutRedirectUri)
        urlBuilder.addQueryParameter("state", state)

        val event = CustomizeLogoutUrlEvent(urlBuilder)
        oidcClient.configuration.eventCoordinator.sendEvent(event)

        return Context(state, urlBuilder.build())
    }

    fun resume(uri: Uri, context: Context): Result {
        if (!uri.toString().startsWith(oidcClient.configuration.signOutRedirectUri)) {
            return Result.RedirectSchemeMismatch
        }

        val errorQueryParameter = uri.getQueryParameter("error")
        if (errorQueryParameter != null) {
            val errorDescription = uri.getQueryParameter("error_description") ?: "An error occurred."
            return Result.Error(errorDescription)
        }

        val stateQueryParameter = uri.getQueryParameter("state")
        if (context.state != stateQueryParameter) {
            val error = "Failed due to state mismatch."
            return Result.Error(error)
        }

        return Result.Success
    }
}
