package com.okta.directauth.model

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
sealed class Continuation(private val context: DirectAuthenticationContext, private val mfaContext: MfaContext? = null) : DirectAuthenticationState {
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
        suspend fun proceed(authenticationResponseJson: String): Result<DirectAuthenticationState> {
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
    class Prompt internal constructor(private val bindingContext: BindingContext, context: DirectAuthenticationContext, mfaContext: MfaContext? = null) : Continuation(context, mfaContext) {
        /**
         * Proceeds with the authentication flow with the user-provided code.
         *
         * @param code The one-time code entered by the user.
         * @return The next [DirectAuthenticationState] in the flow.
         */
        suspend fun proceed(code: String): Result<DirectAuthenticationState> {
            TODO("Not yet implemented")
        }
    }

    /**
     * A continuation for an out-of-band (OOB) flow, where authentication happens on another device.
     *
     * This state is common for flows like "Okta Verify Push" where the user approves a prompt
     * on their phone.
     *
     * @param bindingContext Internal context required to continue the flow.
     * @param context The [DirectAuthenticationContext] associated with this state.
     * @param mfaContext An optional context for MFA flows.
     */
    class Transfer internal constructor(private val bindingContext: BindingContext, context: DirectAuthenticationContext, mfaContext: MfaContext? = null) : Continuation(context, mfaContext) {
        /**
         * A code that can be displayed to the user to link devices or verify the transaction.
         */
        val bindingCode = bindingContext.bindingCode

        /**
         * Proceeds with the authentication flow by polling the server to check the status of the
         * out-of-band authentication.
         *
         * @return The next [DirectAuthenticationState] in the flow, which will typically be
         * [DirectAuthenticationState.Authenticated] once the user has approved the OOB prompt.
         */
        suspend fun proceed(): Result<DirectAuthenticationState> {
            TODO("Not yet implemented")
        }
    }
}