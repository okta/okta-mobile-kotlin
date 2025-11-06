package com.okta.directauth.model

import com.okta.authfoundation.ChallengeGrantType
import com.okta.authfoundation.ChallengeGrantType.OobMfa
import com.okta.authfoundation.ChallengeGrantType.OtpMfa
import com.okta.authfoundation.ChallengeGrantType.WebAuthnMfa
import com.okta.authfoundation.credential.Token
import com.okta.directauth.http.DirectAuthChallengeRequest
import com.okta.directauth.http.DirectAuthTokenRequest
import com.okta.directauth.http.EXCEPTION
import com.okta.directauth.http.challengeResponseAsState
import com.okta.directauth.http.tokenResponseAsState
import kotlin.coroutines.cancellation.CancellationException

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
         * Initiates an interactive multi-factor authentication (MFA) challenge.
         *
         * This function is used for secondary factors that require an interactive step from the user,
         * such as approving a push notification ([PrimaryFactor.Oob]) or using a security key
         * ([PrimaryFactor.WebAuthn]). It sends a request to the server to start the challenge for the
         * specified factor.
         *
         * For factors that don't require a separate challenge step (like [PrimaryFactor.Otp]),
         * the [resume] function can be used instead to directly exchange the passcode for tokens.
         *
         * @param secondaryFactor The [SecondaryFactor] to use for the challenge, such as [PrimaryFactor.Oob].
         * @param challengeTypesSupported A list of [ChallengeGrantType]s the client supports for MFA.
         * @return The next [DirectAuthenticationState] in the flow. This is typically [AuthorizationPending]
         * while waiting for the user to complete the challenge, or an error state if the challenge
         * could not be initiated.
         */
        suspend fun challenge(secondaryFactor: SecondaryFactor, challengeTypesSupported: List<ChallengeGrantType> = listOf(WebAuthnMfa, OobMfa, OtpMfa)): DirectAuthenticationState {
            val result = runCatching {
                val channel = if (secondaryFactor is PrimaryFactor.Oob) secondaryFactor.channel else null
                val request = DirectAuthChallengeRequest(context, mfaContext, challengeTypesSupported, channel)
                val response = context.apiExecutor.execute(request).getOrThrow()
                response.challengeResponseAsState(context, mfaContext)
            }.getOrElse {
                when (it) {
                    is CancellationException -> Canceled
                    else -> DirectAuthenticationError.InternalError(EXCEPTION, it.message, it)
                }
            }
            context.authenticationStateFlow.value = result
            return result
        }

        /**
         * Continues the multi-factor authentication (MFA) flow using the specified secondary factor.
         *
         * This function handles the next step in the MFA process based on the provided [SecondaryFactor].
         * - For factors like [PrimaryFactor.Otp], where the user provides a code directly, this function
         *   attempts to exchange the passcode for tokens without an additional challenge step.
         * - For factors that require an interactive challenge, such as [PrimaryFactor.Oob] or
         *   [PrimaryFactor.WebAuthn], this function will first initiate that challenge.
         *
         * @param secondaryFactor The [SecondaryFactor] to use for authentication, which may include
         *   user input like a one-time passcode.
         * @param challengeTypesSupported A list of [ChallengeGrantType]s the client supports for MFA,
         *   such as [OobMfa], [OtpMfa], and [WebAuthnMfa]. This is used when a new challenge is required.
         * @return The next [DirectAuthenticationState] in the flow, which could be [Authenticated],
         *   [AuthorizationPending], or an error state.
         */
        suspend fun resume(secondaryFactor: SecondaryFactor, challengeTypesSupported: List<ChallengeGrantType> = listOf(WebAuthnMfa, OobMfa, OtpMfa)): DirectAuthenticationState {
            val result = runCatching {
                when (secondaryFactor) {
                    is PrimaryFactor.Otp -> {
                        val request = DirectAuthTokenRequest.MfaOtp(context.copy(grantTypes = challengeTypesSupported), secondaryFactor.passCode, mfaContext)
                        val response = context.apiExecutor.execute(request).getOrThrow()
                        response.tokenResponseAsState(context)
                    }

                    is PrimaryFactor.Oob, PrimaryFactor.WebAuthn -> challenge(secondaryFactor, challengeTypesSupported)
                }
            }.getOrElse {
                when (it) {
                    is CancellationException -> Canceled
                    else -> DirectAuthenticationError.InternalError(EXCEPTION, it.message, it)
                }
            }
            context.authenticationStateFlow.value = result
            return result
        }
    }
}
