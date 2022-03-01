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
import com.okta.authfoundation.client.internal.performRequest
import kotlinx.coroutines.delay
import com.okta.authfoundation.credential.Token as CredentialToken
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.Request

/**
 * An authentication flow class that implements the Device Authorization Grant flow exchange.
 *
 * The Device Authorization Grant flow permits a user to sign in securely from a headless or other similar device (e.g. set-top boxes, Smart TVs, or other devices with limited keyboard input). Using this flow, a user is presented with a screen that provides two pieces of information:
 * 1. A URL the user should visit from another device.
 * 2. A simple user code they can easily enter on that secondary device.
 *
 * Upon visiting that URL and entering in the code, the user is prompted to sign in using their standard credentials. Upon completing authentication, the device automatically signs the user in, without any direct interaction on the user's part.
 */
class DeviceAuthorizationFlow private constructor(
    private val oidcClient: OidcClient,
) {
    companion object {
        /**
         * Initializes a device authorization grant flow using the [OidcClient].
         *
         * @receiver the [OidcClient] used to perform the low level OIDC requests, as well as with which to use the configuration from.
         */
        fun OidcClient.deviceAuthorizationFlow(): DeviceAuthorizationFlow {
            return DeviceAuthorizationFlow(this)
        }
    }

    /**
     * A model representing the context and current state for an authorization session.
     */
    class Context internal constructor(
        internal val deviceCode: String,
        internal val interval: Int,
        internal val expiresIn: Int,
    )

    /**
     * A model representing all the possible states of a [DeviceAuthorizationFlow.start] call.
     */
    sealed class StartResult {
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
        ) : StartResult()

        /**
         * Represents a successful start.
         */
        class Success internal constructor(
            /**
             * The [Response] which contains the information that should be shown to the user.
             */
            val response: Response,

            /**
             * The [Context] which is used to resume the flow.
             */
            val context: Context,
        ) : StartResult()
    }

    /**
     * A model representing all the possible states of a [DeviceAuthorizationFlow.resume] call.
     */
    sealed class ResumeResult {
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
            val exception: Exception? = null
        ) : ResumeResult()

        /**
         * Represents a successful authentication, and contains the [CredentialToken] returned.
         */
        class Token internal constructor(
            /**
             * The [CredentialToken] representing the user logged in via the [DeviceAuthorizationFlow].
             */
            val token: CredentialToken,
        ) : ResumeResult()

        /**
         * An error due to a timeout.
         * The [DeviceAuthorizationFlow] limits the duration a user can poll for a successful authentication, see [Response.expiresIn].
         */
        object Timeout : ResumeResult()
    }

    /**
     * A model from the Authorization Server describing the details to be displayed to the user.
     */
    @Serializable
    data class Response(
        /**
         * The URI the user should be prompted to open in order to authorize the application.
         */
        @SerialName("verification_uri") val verificationUri: String,
        /**
         * A convenience URI that combines the `verificationUri` and the `userCode`, to make a clickable link.
         */
        @SerialName("verification_uri_complete") val verificationUriComplete: String,
        @SerialName("device_code") internal val deviceCode: String,
        /**
         * The code that should be displayed to the user.
         */
        @SerialName("user_code") val userCode: String,
        @SerialName("interval") internal val interval: Int,
        /**
         * The time in seconds after which the authorization context will expire.
         */
        @SerialName("expires_in") val expiresIn: Int,
    )

    internal var delayFunction: suspend (Long) -> Unit = ::delay

    /**
     * Initiates a device authorization flow.
     *
     * See [DeviceAuthorizationFlow.resume] for completing the flow.
     *
     * @param scopes the scopes to request during sign in. Defaults to the configured [OidcClient] [OidcConfiguration.defaultScopes].
     */
    suspend fun start(
        scopes: Set<String> = oidcClient.configuration.defaultScopes,
    ): StartResult {
        val endpoints = oidcClient.endpointsOrNull() ?: return StartResult.Error("Endpoints not available.")

        val deviceAuthorizationEndpoint = endpoints.deviceAuthorizationEndpoint
            ?: return StartResult.Error("Device authorization endpoint is null.")

        val formBodyBuilder = FormBody.Builder()
            .add("client_id", oidcClient.configuration.clientId)
            .add("scope", scopes.joinToString(" "))

        val request = Request.Builder()
            .post(formBodyBuilder.build())
            .url(deviceAuthorizationEndpoint)
            .build()

        return when (val result = oidcClient.configuration.performRequest(Response.serializer(), request)) {
            is OidcClientResult.Error -> {
                StartResult.Error("Device authorization request failed.", result.exception)
            }
            is OidcClientResult.Success -> {
                val response = result.result
                StartResult.Success(response, Context(response.deviceCode, response.interval, response.expiresIn))
            }
        }
    }

    /**
     * Polls to determine when authorization completes, using the supplied [Context] instance.
     *
     * @param flowContext the [Context] created from a [DeviceAuthorizationFlow.start] call.
     */
    suspend fun resume(flowContext: Context): ResumeResult {
        val endpoints = oidcClient.endpointsOrNull() ?: return ResumeResult.Error("Endpoints not available.")

        val formBodyBuilder = FormBody.Builder()
            .add("client_id", oidcClient.configuration.clientId)
            .add("device_code", flowContext.deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")

        val request = Request.Builder()
            .post(formBodyBuilder.build())
            .url(endpoints.tokenEndpoint)
            .build()

        var timeLeft = flowContext.expiresIn

        do {
            timeLeft -= flowContext.interval
            delayFunction(flowContext.interval.toLong() * 1000L)

            when (val tokenResult = oidcClient.tokenRequest(request)) {
                is OidcClientResult.Error -> {
                    if ((tokenResult.exception as? OidcClientResult.Error.HttpResponseException)?.error == "authorization_pending") {
                        // Do another loop in the while, we're polling waiting for the user to authorize.
                        continue
                    } else {
                        return ResumeResult.Error("Token request failed.", tokenResult.exception)
                    }
                }
                is OidcClientResult.Success -> {
                    return ResumeResult.Token(tokenResult.result)
                }
            }
        } while (timeLeft > 0)
        return ResumeResult.Timeout
    }
}
