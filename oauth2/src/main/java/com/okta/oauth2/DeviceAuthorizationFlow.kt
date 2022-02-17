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
import com.okta.authfoundation.client.internal.endpointsOrNull
import com.okta.authfoundation.client.internal.internalTokenRequest
import com.okta.authfoundation.client.internal.performRequest
import kotlinx.coroutines.delay
import com.okta.authfoundation.credential.Token as CredentialToken
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.Request

class DeviceAuthorizationFlow private constructor(
    private val oidcClient: OidcClient,
) {
    companion object {
        fun OidcClient.deviceAuthorizationFlow(): DeviceAuthorizationFlow {
            return DeviceAuthorizationFlow(this)
        }
    }

    class Context internal constructor(
        internal val deviceCode: String,
        internal val interval: Int,
        internal val expiresIn: Int,
    )

    sealed class StartResult {
        class Error internal constructor(val message: String, val exception: Exception? = null) : StartResult()
        class Success internal constructor(val response: Response, val context: Context) : StartResult()
    }

    sealed class ResumeResult {
        class Error internal constructor(val message: String, val exception: Exception? = null) : ResumeResult()
        class Token internal constructor(val token: CredentialToken) : ResumeResult()
        object Timeout : ResumeResult()
    }

    @Serializable
    data class Response(
        @SerialName("verification_uri") val verificationUri: String,
        @SerialName("verification_uri_complete") val verificationUriComplete: String,
        @SerialName("device_code") internal val deviceCode: String,
        @SerialName("user_code") val userCode: String,
        @SerialName("interval") internal val interval: Int,
        @SerialName("expires_in") val expiresIn: Int,
    )

    internal var delayFunction: suspend (Long) -> Unit = ::delay

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

            when (val tokenResult = oidcClient.internalTokenRequest(request)) {
                is OidcClientResult.Error -> {
                    if ((tokenResult.exception as? OidcClientResult.Error.HttpResponseException)?.error == "authorization_pending") {
                        // Do another loop in the while, we're polling waiting for the user to authorize.
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
