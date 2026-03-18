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
package com.okta.directauth.jvm

import com.okta.authfoundation.ChallengeGrantType
import com.okta.directauth.model.SecondaryFactor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture
import com.okta.directauth.model.DirectAuthenticationState as KotlinDirectAuthenticationState

/**
 * A Java-friendly wrapper around [KotlinDirectAuthenticationState.MfaRequired].
 *
 * Provides async methods for MFA challenge and resume operations.
 *
 * @param delegate The underlying Kotlin [KotlinDirectAuthenticationState.MfaRequired] instance.
 */
class MfaRequired(
    private val delegate: KotlinDirectAuthenticationState.MfaRequired,
) : com.okta.directauth.jvm.DirectAuthenticationState(delegate) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Initiates an MFA challenge with the specified secondary factor.
     *
     * @param secondaryFactor The [SecondaryFactor] to use for the challenge.
     * @return A [CompletableFuture] that completes with the next [DirectAuthenticationState].
     */
    fun challengeAsync(secondaryFactor: SecondaryFactor): CompletableFuture<DirectAuthenticationState> =
        coroutineScope.future {
            delegate.challenge(secondaryFactor).toJvm()
        }

    /**
     * Initiates an MFA challenge with the specified secondary factor and supported challenge types.
     *
     * @param secondaryFactor The [SecondaryFactor] to use for the challenge.
     * @param challengeTypesSupported The list of [ChallengeGrantType]s the client supports.
     * @return A [CompletableFuture] that completes with the next [DirectAuthenticationState].
     */
    fun challengeAsync(
        secondaryFactor: SecondaryFactor,
        challengeTypesSupported: List<ChallengeGrantType>,
    ): CompletableFuture<DirectAuthenticationState> =
        coroutineScope.future {
            delegate.challenge(secondaryFactor, challengeTypesSupported).toJvm()
        }

    /**
     * Continues the MFA flow using the specified secondary factor.
     *
     * @param secondaryFactor The [SecondaryFactor] to use for authentication.
     * @return A [CompletableFuture] that completes with the next [DirectAuthenticationState].
     */
    fun resumeAsync(secondaryFactor: SecondaryFactor): CompletableFuture<DirectAuthenticationState> =
        coroutineScope.future {
            delegate.resume(secondaryFactor).toJvm()
        }

    /**
     * Continues the MFA flow using the specified secondary factor and supported challenge types.
     *
     * @param secondaryFactor The [SecondaryFactor] to use for authentication.
     * @param challengeTypesSupported The list of [ChallengeGrantType]s the client supports.
     * @return A [CompletableFuture] that completes with the next [DirectAuthenticationState].
     */
    fun resumeAsync(
        secondaryFactor: SecondaryFactor,
        challengeTypesSupported: List<ChallengeGrantType>,
    ): CompletableFuture<DirectAuthenticationState> =
        coroutineScope.future {
            delegate.resume(secondaryFactor, challengeTypesSupported).toJvm()
        }
}
