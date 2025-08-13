package com.okta.directauth.model

import com.okta.authfoundation.credential.Token

/**
 * Represents the current state of a Direct Authentication flow.
 *
 * This sealed interface defines the possible outcomes after an authentication step. The flow can
 * either be complete, resulting in a [Authenticated] state, or require further user interaction,
 * such as in an [MfaRequired] state or [Continuation] state
 */
sealed interface DirectAuthenticationState {

    /**
     * The initial state of the authentication flow, before any action has been taken.
     */
    data object Idle : DirectAuthenticationState

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
    class MfaRequired internal constructor(private val context: DirectAuthenticationContext, private val mfaContext: MfaContext) : DirectAuthenticationState {
        /**
         * Continues the authentication flow with the selected MFA factor.
         *
         * @param secondaryFactor The secondary [SecondaryFactor] to use for MFA (e.g., [PrimaryFactor.Otp], [PrimaryFactor.WebAuthn], etc.).
         * @return The next [DirectAuthenticationState] in the flow.
         */
        suspend fun resume(secondaryFactor: SecondaryFactor): Result<DirectAuthenticationState> {
            TODO("Not yet implemented")
        }
    }
}
