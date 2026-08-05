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
 * This interface defines the contract for initiating an authentication flow. An instance is
 * created via [com.okta.directauth.DirectAuthenticationFlowBuilder.create]. Kotlin callers
 * should prefer this coroutine-based API; Java callers should use
 * [com.okta.directauth.jvm.DirectAuthenticationFlow], which exposes CompletableFuture-based
 * methods.
 */
interface DirectAuthenticationFlow {
    /**
     * A [StateFlow] emitting the current [DirectAuthenticationState]; collect it to observe
     * every transition driven by `start()`, `reset()`, and the various
     * `proceed()`/`challenge()`/`resume()` calls. Starts at [DirectAuthenticationState.Idle].
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
     * @return the resulting [DirectAuthenticationState] — [DirectAuthenticationState.Authenticated] on immediate
     * success, [DirectAuthenticationState.MfaRequired] or a [com.okta.directauth.model.DirectAuthContinuation] when
     * more steps are needed, or a [com.okta.directauth.model.DirectAuthenticationError] on failure. Errors are
     * returned as states, never thrown (cancellation excepted). Suspends until the server responds.
     */
    suspend fun start(
        loginHint: String,
        primaryFactor: PrimaryFactor,
    ): DirectAuthenticationState

    /**
     * Resets the direct authentication flow.
     *
     * @return [DirectAuthenticationState.Idle]. This is not a suspending call; it both returns and
     * emits (via [authenticationState]) the idle state synchronously.
     */
    fun reset(): DirectAuthenticationState
}
