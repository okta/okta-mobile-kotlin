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

import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.credential.Token as CredentialToken
import okhttp3.FormBody
import okhttp3.Request

/**
 * An authentication flow class that implements the Resource Owner Flow exchange.
 *
 * This simple authentication flow permits a suer to authenticate using a simple username and password. As such, the configuration is straightforward.
 *
 * > Important: Resource Owner authentication does not support MFA or other more secure authentication models, and is not recommended for production applications.
 */
class ResourceOwnerFlow private constructor(
    private val oidcClient: OidcClient,
) {
    companion object {
        /**
         * Initializes a resource owner flow using the [OidcClient].
         *
         * @receiver the [OidcClient] used to perform the low level OIDC requests, as well as with which to use the configuration from.
         */
        fun OidcClient.resourceOwnerFlow(): ResourceOwnerFlow {
            return ResourceOwnerFlow(this)
        }
    }

    /**
     * A model representing all the possible states of a [ResourceOwnerFlow.start] call.
     */
    sealed class Result {
        /**
         * An error resulting from an interaction with the Authorization Server.
         */
        class Error internal constructor(
            /**
             * An error message intended to be displayed to the user.
             */
            val message: String,
            /**
             * The exception, if available which caused the error.
             */
            val exception: Exception? = null,
        ) : Result()

        /**
         * Represents a successful authentication, and contains the [CredentialToken] returned.
         */
        class Token internal constructor(
            /**
             * The [CredentialToken] representing the user logged in via the [ResourceOwnerFlow].
             */
            val token: CredentialToken,
        ) : Result()
    }

    /**
     * Initiates the Resource Owner flow.
     *
     * @param username the username
     * @param password the password
     * @param scopes the scopes to request during sign in. Defaults to the configured [OidcClient] [OidcConfiguration.defaultScopes].
     */
    suspend fun start(
        username: String,
        password: String,
        scopes: Set<String> = oidcClient.configuration.defaultScopes,
    ): Result {
        val endpoints = oidcClient.endpointsOrNull() ?: return Result.Error("Endpoints not available.")

        val formBodyBuilder = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .add("client_id", oidcClient.configuration.clientId)
            .add("grant_type", "password")
            .add("scope", scopes.joinToString(" "))

        val request = Request.Builder()
            .post(formBodyBuilder.build())
            .url(endpoints.tokenEndpoint)
            .build()

        return when (val tokenResult = oidcClient.tokenRequest(request)) {
            is OidcClientResult.Error -> {
                Result.Error("Token request failed.", tokenResult.exception)
            }
            is OidcClientResult.Success -> {
                Result.Token(tokenResult.result)
            }
        }
    }
}
