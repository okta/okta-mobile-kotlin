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

import com.okta.authfoundation.client.OAuth2Client
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.client.internal.SdkVersionsRegistry
import com.okta.authfoundation.client.internal.performRequest
import com.okta.authfoundation.credential.Token
import kotlinx.coroutines.delay
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
class DeviceAuthorizationFlow(
    private val client: OAuth2Client,
) {
    companion object {
        init {
            SdkVersionsRegistry.register(SDK_VERSION)
        }
    }

    /**
     * Initializes a device authorization grant flow.
     */
    constructor() : this(OAuth2Client.default)

    /**
     * Initializes a device authorization grant flow using the [OidcConfiguration].
     *
     * @param oidcConfiguration the [OidcConfiguration] specifying the authorization servers.
     */
    constructor(oidcConfiguration: OidcConfiguration) : this(OAuth2Client.createFromConfiguration(oidcConfiguration))

    /**
     * A model representing the context and current state for an authorization session.
     */
    class Context internal constructor(
        /**
         * The URI the user should be prompted to open in order to authorize the application.
         */
        val verificationUri: String,
        /**
         * A convenience URI that combines the `verificationUri` and the `userCode`, to make a clickable link.
         */
        val verificationUriComplete: String?,
        /**
         * The code that should be displayed to the user.
         */
        val userCode: String,
        /**
         * The time in seconds after which the authorization context will expire.
         */
        val expiresIn: Int,

        internal val deviceCode: String,
        internal val interval: Int,
    )

    /**
     * An error due to a timeout.
     * The [DeviceAuthorizationFlow] limits the duration a user can poll for a successful authentication, see [Context.expiresIn].
     */
    class TimeoutException : Exception()

    @Serializable
    internal class SerializableResponse(
        @SerialName("verification_uri") val verificationUri: String,
        @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
        @SerialName("device_code") internal val deviceCode: String,
        @SerialName("user_code") val userCode: String,
        @SerialName("interval") internal val interval: Int = 5,
        @SerialName("expires_in") val expiresIn: Int,
    ) {
        fun asFlowContext(): Context {
            return Context(
                verificationUri = verificationUri,
                verificationUriComplete = verificationUriComplete,
                userCode = userCode,
                expiresIn = expiresIn,
                deviceCode = deviceCode,
                interval = interval,
            )
        }
    }

    internal var delayFunction: suspend (Long) -> Unit = ::delay

    /**
     * Initiates a device authorization flow.
     *
     * See [DeviceAuthorizationFlow.resume] for completing the flow.
     *
     * @param extraRequestParameters the extra key value pairs to send to the device authorize endpoint.
     *  See [Device Authorize Documentation](https://developer.okta.com/docs/reference/api/oidc/#device-authorize) for parameter
     *  options.
     * @param scope the scopes to request during sign in. Defaults to the configured [OAuth2Client] [OidcConfiguration.defaultScope].
     */
    suspend fun start(
        extraRequestParameters: Map<String, String> = emptyMap(),
        scope: String = client.configuration.defaultScope,
    ): OAuth2ClientResult<Context> {
        val endpoint = client.endpointsOrNull()?.deviceAuthorizationEndpoint ?: return client.endpointNotAvailableError()

        val formBodyBuilder = FormBody.Builder()

        for (entry in extraRequestParameters.entries) {
            formBodyBuilder.add(entry.key, entry.value)
        }

        formBodyBuilder
            .add("client_id", client.configuration.clientId)
            .add("scope", scope)

        val request = Request.Builder()
            .post(formBodyBuilder.build())
            .url(endpoint)
            .build()

        return client.performRequest(SerializableResponse.serializer(), request) { serializableResponse ->
            serializableResponse.asFlowContext()
        }
    }

    /**
     * Polls to determine when authorization completes, using the supplied [Context] instance.
     *
     * @param flowContext the [Context] created from a [DeviceAuthorizationFlow.start] call.
     */
    suspend fun resume(flowContext: Context): OAuth2ClientResult<Token> {
        val endpoints = client.endpointsOrNull() ?: return client.endpointNotAvailableError()

        val formBodyBuilder = FormBody.Builder()
            .add("client_id", client.configuration.clientId)
            .add("device_code", flowContext.deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")

        val request = Request.Builder()
            .post(formBodyBuilder.build())
            .url(endpoints.tokenEndpoint)
            .build()

        var timeLeft = flowContext.expiresIn

        var interval = flowContext.interval

        do {
            timeLeft -= interval
            delayFunction(interval.toLong() * 1000L)

            // https://datatracker.ietf.org/doc/html/rfc8628#section-3.5
            when (val tokenResult = client.tokenRequest(request)) {
                is OAuth2ClientResult.Error -> {
                    val error = (tokenResult.exception as? OAuth2ClientResult.Error.HttpResponseException)?.error
                    if (error == "authorization_pending") {
                        // Do another loop in the while, we're polling waiting for the user to authorize.
                        continue
                    } else if (error == "slow_down") {
                        // Increase the interval for all future requests according to the spec.
                        interval += 5
                        // Do another loop in the while, we're polling waiting for the user to authorize.
                        continue
                    } else {
                        return tokenResult
                    }
                }
                is OAuth2ClientResult.Success -> {
                    return tokenResult
                }
            }
        } while (timeLeft > 0)
        return OAuth2ClientResult.Error(TimeoutException())
    }
}
