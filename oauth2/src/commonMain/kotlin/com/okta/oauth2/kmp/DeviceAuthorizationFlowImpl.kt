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
package com.okta.oauth2.kmp

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.kmp.OAuth2Client
import kotlinx.coroutines.delay

/**
 * Default implementation of [DeviceAuthorizationFlow] using the KMP [OAuth2Client].
 *
 * @param client the [OAuth2Client] instance used for token requests.
 */
internal class DeviceAuthorizationFlowImpl(
    private val client: OAuth2Client,
) : DeviceAuthorizationFlow {
    internal var delayFunction: suspend (Long) -> Unit = ::delay

    override suspend fun start(scope: String?): Result<DeviceAuthorizationFlowContext> =
        client
            .deviceAuthorizationRequest(
                formParams =
                    mapOf(
                        "client_id" to client.configuration.clientId,
                        "scope" to (scope ?: client.configuration.defaultScope)
                    )
            ).map { info ->
                DeviceAuthorizationFlowContext(
                    verificationUri = info.verificationUri,
                    verificationUriComplete = info.verificationUriComplete,
                    userCode = info.userCode,
                    expiresIn = info.expiresIn,
                    deviceCode = info.deviceCode,
                    interval = info.interval
                )
            }

    @OptIn(InternalAuthFoundationApi::class)
    override suspend fun resume(flowContext: DeviceAuthorizationFlowContext): Result<TokenInfo> =
        runCatching {
            client.endpointsOrNull()
                ?: throw IllegalStateException("OIDC Endpoints not available.")

            val formParams =
                mapOf(
                    "client_id" to client.configuration.clientId,
                    "device_code" to flowContext.deviceCode,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
                )

            var timeLeft = flowContext.expiresIn
            var interval = flowContext.interval

            while (timeLeft > 0) {
                timeLeft -= interval
                delayFunction(interval.toLong() * 1000L)

                val tokenResult = client.tokenRequest(formParams = formParams)
                if (tokenResult.isSuccess) {
                    return@runCatching tokenResult.getOrThrow()
                }

                val error =
                    (tokenResult.exceptionOrNull() as? OAuth2ClientResult.Error.HttpResponseException)?.error
                when (error) {
                    "authorization_pending" -> {
                        continue
                    }

                    "slow_down" -> {
                        interval += 5
                        continue
                    }

                    else -> {
                        tokenResult.getOrThrow()
                    } // re-throw the original exception
                }
            }

            throw DeviceAuthorizationFlow.TimeoutException()
        }
}
