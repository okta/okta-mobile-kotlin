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
package com.okta.directauth.model

import com.okta.directauth.api.WebAuthnCeremonyHandler
import com.okta.directauth.http.DirectAuthTokenRequest
import com.okta.directauth.http.EXCEPTION
import com.okta.directauth.http.handlers.TokenStepHandler
import com.okta.directauth.http.model.WebAuthnChallengeResponse
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/**
 * Represents a state in the Direct Authentication flow where further action is required to proceed.
 *
 * This is a special type of [DirectAuthenticationState] that indicates the flow is paused,
 * pending input from the user or another client-side process.
 *
 * In the case of a failure, the state will be an instance of [DirectAuthenticationError].
 * This can be an [DirectAuthenticationError.HttpError] for API-related issues (e.g., invalid
 * credentials) or an [DirectAuthenticationError.InternalError] for unexpected client-side
 * problems (e.g., network failures, response parsing errors).
 *
 * @param context The [DirectAuthenticationContext] associated with this state.
 * @param mfaContext An optional context for MFA flows, which may be present if the continuation
 * is part of a multi-factor sequence.
 */
sealed class DirectAuthContinuation(
    internal val context: DirectAuthenticationContext,
    internal val mfaContext: MfaContext? = null,
) : DirectAuthenticationState {
    /**
     * Shared implementation for polling an out-of-band (OOB) authentication endpoint.
     *
     * This function initiates a polling loop that continues as long as the server responds with
     * [DirectAuthenticationState.AuthorizationPending]. The loop is governed by a timeout defined
     * in the [bindingContext].
     *
     * On each iteration, it makes a request to the token endpoint and updates the central
     * [DirectAuthenticationContext.authenticationStateFlow]. If a terminal state (e.g.,
     * [DirectAuthenticationState.Authenticated]) is received, the loop terminates and that state
     * is returned.
     *
     * The polling will fail with an [DirectAuthenticationError.InternalError] if the total time
     * exceeds `bindingContext.expiresIn`. If the calling coroutine is canceled, the loop will
     * terminate and return [DirectAuthenticationState.Canceled].
     *
     * @param bindingContext The context containing the `oobCode`, `expiresIn`, and `interval` for polling.
     * @param context The main [DirectAuthenticationContext] for the flow.
     * @param mfaContext An optional context for MFA flows.
     * @return The final [DirectAuthenticationState] after the polling completes, is canceled, or fails.
     */
    internal suspend fun poll(
        bindingContext: BindingContext,
        context: DirectAuthenticationContext,
        mfaContext: MfaContext?,
    ): DirectAuthenticationState {
        val state =
            runCatching {
                withTimeout(bindingContext.expiresIn.seconds) {
                    while (isActive) {
                        val request =
                            mfaContext?.let {
                                DirectAuthTokenRequest.OobMfa(context, bindingContext.oobCode, it)
                            } ?: DirectAuthTokenRequest.Oob(context, bindingContext.oobCode)

                        val currentState = TokenStepHandler(request, context).process()

                        if (currentState !is DirectAuthenticationState.AuthorizationPending) return@withTimeout currentState

                        context.authenticationStateFlow.value = currentState
                        // if delay interval is not specified then default to 5 seconds
                        val interval = bindingContext.interval ?: 5
                        delay(interval.seconds)
                    }
                    // This is reached if the loop exits due to the job being canceled
                    DirectAuthenticationState.Canceled
                }
            }.getOrElse {
                when (it) {
                    is TimeoutCancellationException -> DirectAuthenticationError.InternalError(EXCEPTION, "Polling timed out after ${bindingContext.expiresIn} seconds.", it)
                    is CancellationException -> DirectAuthenticationState.Canceled
                    else -> DirectAuthenticationError.InternalError(EXCEPTION, it.message, it)
                }
            }
        context.authenticationStateFlow.value = state
        return state
    }

    /**
     * A continuation that requires a WebAuthn (passkey) challenge to be completed.
     *
     * This state provides the necessary challenge data to interact with the platform's
     * WebAuthn/passkey APIs. There are two ways to proceed:
     *
     * 1. **Recommended**: Use [proceed] with a [WebAuthnCeremonyHandler] to let the SDK
     *    orchestrate the platform ceremony and token exchange.
     * 2. **Lower-level**: Perform the ceremony yourself and pass the result to [proceed]
     *    with a [WebAuthnAssertionResponse].
     *
     * @param context The [DirectAuthenticationContext] associated with this state.
     * @param mfaContext An optional context for MFA flows.
     */
    class WebAuthn internal constructor(
        private val webAuthnChallengeResponse: WebAuthnChallengeResponse,
        context: DirectAuthenticationContext,
        mfaContext: MfaContext? = null,
    ) : DirectAuthContinuation(context, mfaContext) {
        /**
         * Retrieves the raw JSON string representing the WebAuthn challenge data.
         *
         * The challenge data is required to perform the WebAuthn ceremony (assertion) on the platform.
         *
         * The returned [Result] wraps the operation to handle potential parsing or serialization errors.
         * If the challenge data cannot be extracted or serialized to JSON, a [Result.failure]
         * is returned containing the exception details.
         */
        fun challengeData(): Result<String> =
            runCatching {
                Result.success(webAuthnChallengeResponse.challengeData(context.json, context.issuerUrl))
            }.getOrElse {
                Result.failure(it)
            }

        /**
         * The list of authenticator enrollments returned from the Okta server.
         */
        val authenticatorEnrollment = webAuthnChallengeResponse.authenticatorEnrollments

        /**
         * Proceeds with the authentication flow by delegating the WebAuthn ceremony to the
         * provided [handler], then exchanging the assertion response for tokens.
         *
         * This is the recommended approach. The SDK orchestrates the full flow.
         *
         * @param handler A platform-specific [WebAuthnCeremonyHandler] (e.g., `AndroidWebAuthnCeremonyHandler`).
         * @return The next [DirectAuthenticationState] in the flow.
         */
        suspend fun proceed(handler: WebAuthnCeremonyHandler): DirectAuthenticationState {
            val data =
                challengeData().getOrElse {
                    return DirectAuthenticationError.InternalError(EXCEPTION, "Challenge data retrieval failed: ${it.message}", it)
                }
            val assertionResponse =
                handler.performAssertion(data).getOrElse {
                    val error = DirectAuthenticationError.InternalError(EXCEPTION, "WebAuthn ceremony failed: ${it.message}", it)
                    context.authenticationStateFlow.value = error
                    return error
                }
            return proceed(assertionResponse)
        }

        /**
         * Proceeds with the authentication flow using a pre-obtained WebAuthn assertion response.
         *
         * Use this when the application performs the platform WebAuthn ceremony itself and
         * wants to exchange the result for tokens.
         *
         * @param assertionResponse The [WebAuthnAssertionResponse] from the platform's WebAuthn API.
         * @return The next [DirectAuthenticationState] in the flow.
         */
        suspend fun proceed(assertionResponse: WebAuthnAssertionResponse): DirectAuthenticationState {
            val request =
                mfaContext?.let {
                    DirectAuthTokenRequest.WebAuthnMfa(context, mfaContext, assertionResponse)
                } ?: DirectAuthTokenRequest.WebAuthn(context, assertionResponse)

            val result =
                runCatching { TokenStepHandler(request, context).process() }.getOrElse {
                    if (it is CancellationException) {
                        DirectAuthenticationState.Canceled
                    } else {
                        DirectAuthenticationError.InternalError(EXCEPTION, it.message, it)
                    }
                }
            context.authenticationStateFlow.value = result
            return result
        }
    }

    /**
     * A continuation that requires the user to enter a code, such as an OTP from an
     * authenticator app or a code sent via email/SMS.
     *
     * @param bindingContext Internal context required to continue the flow.
     * @param context The [DirectAuthenticationContext] associated with this state.
     * @param mfaContext An optional context for MFA flows.
     */
    class Prompt internal constructor(
        internal val bindingContext: BindingContext,
        context: DirectAuthenticationContext,
        mfaContext: MfaContext? = null,
    ) : DirectAuthContinuation(context, mfaContext) {
        /**
         * The number of seconds until the OOB challenge expires.
         */
        val expirationInSeconds: Int = bindingContext.expiresIn

        /**
         * Proceeds with the authentication flow with the user-provided code.
         *
         * @param code The one-time code entered by the user.
         * @return The next [DirectAuthenticationState] in the flow.
         */
        suspend fun proceed(code: String): DirectAuthenticationState {
            val request =
                mfaContext?.let {
                    DirectAuthTokenRequest.OobMfa(context, bindingContext.oobCode, it, code)
                } ?: DirectAuthTokenRequest.Oob(context, bindingContext.oobCode, code)

            val result =
                runCatching { TokenStepHandler(request, context).process() }.getOrElse {
                    if (it is CancellationException) {
                        DirectAuthenticationState.Canceled
                    } else {
                        DirectAuthenticationError.InternalError(EXCEPTION, it.message, it)
                    }
                }
            context.authenticationStateFlow.value = result
            return result
        }
    }

    /**
     * A continuation for an out-of-band (OOB) flow, where authentication happens on another device.
     *
     * This state is common for flows like "Okta Verify Push" where the user selects the matching [bindingCode] prompt.
     *
     * @param bindingContext Internal context required to continue the flow.
     * @param context The [DirectAuthenticationContext] associated with this state.
     * @param mfaContext An optional context for MFA flows.
     */
    class Transfer internal constructor(
        internal val bindingContext: BindingContext,
        context: DirectAuthenticationContext,
        mfaContext: MfaContext? = null,
    ) : DirectAuthContinuation(context, mfaContext) {
        /** The number of seconds until the OOB challenge expires. */
        val expirationInSeconds: Int = bindingContext.expiresIn

        /** A code that can be displayed to the user to link devices or verify the transaction. */
        val bindingCode = bindingContext.bindingCode

        /**
         * Proceeds with the authentication flow by polling the server to check the status of the
         * out-of-band authentication.
         *
         * @return The next [DirectAuthenticationState] in the flow.
         */
        suspend fun proceed(): DirectAuthenticationState = poll(bindingContext, context, mfaContext)
    }

    /**
     * Represents a state where an out-of-band (OOB) challenge has been initiated, and the
     * client must poll to determine the result.
     *
     * This state occurs after initiating an OOB flow, such as sending a push notification to
     * Okta Verify, when no specific binding method (like a code to display) is used.
     * The client should use the [proceed] method to check if the user has
     * completed the authentication step.
     *
     * @param bindingContext Internal context required to continue the flow.
     * @param context The [DirectAuthenticationContext] associated with this state.
     * @param mfaContext An optional context for MFA flows.
     */
    class OobPending internal constructor(
        internal val bindingContext: BindingContext,
        context: DirectAuthenticationContext,
        mfaContext: MfaContext? = null,
    ) : DirectAuthContinuation(context, mfaContext) {
        /** The number of seconds until the OOB challenge expires. */
        val expirationInSeconds: Int = bindingContext.expiresIn

        /**
         * Polls the token endpoint to check the status of the OOB authentication.
         *
         * @return The next [DirectAuthenticationState] in the flow.
         */
        suspend fun proceed(): DirectAuthenticationState = poll(bindingContext, context, mfaContext)
    }
}
