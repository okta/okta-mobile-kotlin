package com.okta.directauth.model

import com.okta.directauth.http.DirectAuthTokenRequest
import com.okta.directauth.http.EXCEPTION
import com.okta.directauth.http.tokenResponseAsState
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

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
sealed class Continuation(internal val context: DirectAuthenticationContext, internal val mfaContext: MfaContext? = null) : DirectAuthenticationState {

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
    internal suspend fun poll(bindingContext: BindingContext, context: DirectAuthenticationContext, mfaContext: MfaContext?): DirectAuthenticationState {
        val state = runCatching {
            withTimeout(bindingContext.expiresIn * 1000L) {
                while (isActive) {
                    val request = mfaContext?.let {
                        DirectAuthTokenRequest.OobMfa(context, bindingContext.oobCode, it)
                    } ?: DirectAuthTokenRequest.Oob(context, bindingContext.oobCode)

                    val response = context.apiExecutor.execute(request).getOrThrow()
                    val currentState = response.tokenResponseAsState(context)

                    context.authenticationStateFlow.value = currentState

                    if (currentState !is DirectAuthenticationState.AuthorizationPending) return@withTimeout currentState

                    // if delay interval is not specified then default to 5 seconds
                    val interval = bindingContext.interval ?: 5
                    delay(interval * 1000L)
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
     * WebAuthn/passkey APIs.
     *
     * @param challengeData The challenge data from the server, to be passed to the platform's WebAuthn API.
     * @param context The [DirectAuthenticationContext] associated with this state.
     * @param mfaContext An optional context for MFA flows.
     */
    class WebAuthn internal constructor(val challengeData: String, context: DirectAuthenticationContext, mfaContext: MfaContext? = null) : Continuation(context, mfaContext) {
        /**
         * Proceeds with the authentication flow with the response from the WebAuthn/passkey ceremony.
         *
         * @param authenticationResponseJson The JSON response from the platform's WebAuthn API.
         * @return The next [DirectAuthenticationState] in the flow.
         */
        suspend fun proceed(authenticationResponseJson: String): DirectAuthenticationState {
            TODO("Not yet implemented")
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
    class Prompt internal constructor(internal val bindingContext: BindingContext, context: DirectAuthenticationContext, mfaContext: MfaContext? = null) : Continuation(context, mfaContext) {

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
            val request = if (mfaContext != null) {
                DirectAuthTokenRequest.OobMfa(context, bindingContext.oobCode, mfaContext, code)
            } else {
                DirectAuthTokenRequest.Oob(context, bindingContext.oobCode, code)
            }
            val result = context.apiExecutor.execute(request)
            val state = result.fold(
                onSuccess = { it.tokenResponseAsState(context) },
                onFailure = { DirectAuthenticationError.InternalError(EXCEPTION, it.message, it) }
            )
            context.authenticationStateFlow.value = state
            return state
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
    ) : Continuation(context, mfaContext) {

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
    ) : Continuation(context, mfaContext) {

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
