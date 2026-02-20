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
    suspend fun start(
        loginHint: String,
        primaryFactor: PrimaryFactor,
    ): DirectAuthenticationState

    /**
     * Resets the direct authentication flow
     *
     * @return The [DirectAuthenticationState] of the flow
     */
    fun reset(): DirectAuthenticationState
}
