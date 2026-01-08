package com.okta.directauth.api

import com.okta.directauth.model.DirectAuthenticationState
import com.okta.directauth.model.PrimaryFactor
import kotlinx.coroutines.flow.StateFlow

/**
 * The primary interface for interacting with the Okta Direct Authentication API.
 *
 * This interface defines the contract for initiating an authentication flow. An instance
 * of this interface can be created using the `DirectAuthenticationFlow.create` builder.
 */
interface DirectAuthenticationFlow {

    /**
     * Indicates authentication flow state
     */
    val authenticationState: StateFlow<DirectAuthenticationState>

    /**
     * Starts the direct authentication flow with an initial factor.
     *
     * This is the entry point for authenticating a user. Depending on the server's policy
     * and the provided factor, the flow may complete in a single step or require
     * additional steps, such as providing a secondary factor.
     *
     * @param loginHint A hint to the authorization server about the user's identity,
     *  such as a username or email address.
     * @param primaryFactor The initial authentication factor to use (e.g., a [PrimaryFactor.Password]).
     * @return The [DirectAuthenticationState] of the flow
     */
    suspend fun start(loginHint: String, primaryFactor: PrimaryFactor): DirectAuthenticationState

    /**
     * Resets the direct authentication flow
     *
     * @return The [DirectAuthenticationState] of the flow
     */
    fun reset(): DirectAuthenticationState
}
