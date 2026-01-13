package com.okta.directauth.model

import com.okta.authfoundation.credential.Token

/**
 * Represents the current state of a Direct Authentication flow.
 *
 * This sealed interface defines the possible outcomes after an authentication step. The flow can
 * either be complete, resulting in a [Authenticated] state, or require further user interaction,
 * such as in an [MfaRequired] state or [Continuation] state.
 *
 * In the case of a failure, the state will be an instance of [DirectAuthenticationError].
 * This can be an [DirectAuthenticationError.HttpError] for API-related issues (e.g., invalid
 * credentials) or an [DirectAuthenticationError.InternalError] for unexpected client-side
 * problems (e.g., network failures, response parsing errors).
 */
sealed interface DirectAuthenticationState {

    /**
     * The initial state of the authentication flow, before any action has been taken.
     */
    data object Idle : DirectAuthenticationState

    /**
     * The authentication flow has been canceled
     */
    data object Canceled : DirectAuthenticationState

    /**
     * This state indicates that the authentication process is still pending and awaiting user action.
     * Such as waiting for the user to complete an out-of-band (OOB) authentication step.
     *
     * @param timestamp The time in milliseconds when the authorization became pending.
     */
    class AuthorizationPending internal constructor(val timestamp: Long) : DirectAuthenticationState

    /**
     * This state indicates that the user has been authenticated and tokens have been issued.
     *
     * @param token The [Token] containing the access, refresh, and ID tokens.
     */
    class Authenticated internal constructor(val token: Token) : DirectAuthenticationState

    /**
     * The authentication flow requires an additional factor to complete.
     *
     * This state indicates that the user must perform a Multi-Factor Authentication (MFA)
     * step to proceed.
     *
     * @param context The [DirectAuthenticationContext] associated with this state.
     * @param mfaContext The context required to continue the MFA flow.
     */
    class MfaRequired internal constructor(private val context: DirectAuthenticationContext, internal val mfaContext: MfaContext) : DirectAuthenticationState {
        /**
         * Continues the authentication flow with the selected MFA factor.
         *
         * @param secondaryFactor The secondary [SecondaryFactor] to use for MFA (e.g., [PrimaryFactor.Otp], [PrimaryFactor.WebAuthn], etc.).
         * @return The next [DirectAuthenticationState] in the flow.
         */
        suspend fun resume(secondaryFactor: SecondaryFactor): DirectAuthenticationState {
            TODO("Not yet implemented")
        }
    }
}
