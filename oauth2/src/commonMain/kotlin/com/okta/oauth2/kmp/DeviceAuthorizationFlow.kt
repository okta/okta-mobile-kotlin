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

import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.kmp.OAuth2Client

/**
 * Implements the [Device Authorization Grant](https://datatracker.ietf.org/doc/html/rfc8628) flow.
 *
 * Use [start] to obtain a [DeviceAuthorizationFlowContext] containing the user code and verification
 * URI to display to the user, then poll [resume] until the user completes authorization or the
 * session expires.
 *
 * ```kotlin
 * val flow = DeviceAuthorizationFlow(client)
 * val ctx = flow.start().getOrThrow()
 * println("Visit ${ctx.verificationUriComplete} and enter ${ctx.userCode}")
 * val tokenInfo = flow.resume(ctx).getOrThrow()
 * ```
 */
interface DeviceAuthorizationFlow {
    /**
     * Initiates the device authorization flow.
     *
     * @param scope the OAuth2 scopes to request.
     * @return [Result.success] with a [DeviceAuthorizationFlowContext], or [Result.failure] with:
     * - [com.okta.authfoundation.client.OAuth2ClientResult.Error.OidcEndpointsNotAvailableException] if the device authorization endpoint is unavailable.
     * - Other exceptions for network or parse failures.
     */
    suspend fun start(scope: String = "openid profile email offline_access"): Result<DeviceAuthorizationFlowContext>

    /**
     * Polls the token endpoint until the user authorizes or the session expires.
     *
     * Should be called repeatedly after [start]. Retries automatically on
     * `authorization_pending` and `slow_down` errors per RFC 8628 §3.5.
     *
     * @param flowContext the context returned from [start].
     * @return [Result.success] with [TokenInfo] on authorization, or [Result.failure] with:
     * - [TimeoutException] if [DeviceAuthorizationFlowContext.expiresIn] elapses.
     * - [com.okta.authfoundation.client.OAuth2ClientResult.Error.HttpResponseException] on non-recoverable server errors.
     * - Other exceptions for network failures.
     */
    suspend fun resume(flowContext: DeviceAuthorizationFlowContext): Result<TokenInfo>

    /**
     * An error due to the authorization session expiring before the user completed sign in.
     */
    class TimeoutException : Exception("Device authorization timed out.")

    companion object {
        /**
         * Creates a [DeviceAuthorizationFlow] backed by the given [OAuth2Client].
         */
        operator fun invoke(client: OAuth2Client): DeviceAuthorizationFlow = DeviceAuthorizationFlowImpl(client)
    }
}
