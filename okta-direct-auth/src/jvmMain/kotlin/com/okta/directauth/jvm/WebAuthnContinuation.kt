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

import com.okta.directauth.api.WebAuthnCeremonyHandler
import com.okta.directauth.model.DirectAuthContinuation
import com.okta.directauth.model.WebAuthnAssertionResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

/**
 * A Java-friendly wrapper around [DirectAuthContinuation.WebAuthn].
 *
 * Provides async methods for completing a WebAuthn ceremony.
 *
 * @param delegate The underlying Kotlin [DirectAuthContinuation.WebAuthn] instance.
 */
class WebAuthnContinuation(
    private val delegate: DirectAuthContinuation.WebAuthn,
) : DirectAuthenticationState(delegate) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Retrieves the raw JSON string representing the WebAuthn challenge data.
     *
     * @return A [Result] containing the challenge data JSON string on success.
     */
    fun challengeData(): Result<String> = delegate.challengeData()

    /**
     * The list of authenticator enrollments returned from the server.
     */
    val authenticatorEnrollment = delegate.authenticatorEnrollment

    /**
     * Proceeds with the WebAuthn flow using a platform ceremony handler.
     *
     * @param handler A [WebAuthnCeremonyHandler] that performs the platform WebAuthn ceremony.
     * @return A [CompletableFuture] that completes with the next [DirectAuthenticationState].
     */
    fun proceedAsync(handler: WebAuthnCeremonyHandler): CompletableFuture<DirectAuthenticationState> =
        coroutineScope.future {
            delegate.proceed(handler).toJvm()
        }

    /**
     * Proceeds with the WebAuthn flow using a pre-obtained assertion response.
     *
     * @param assertionResponse The [WebAuthnAssertionResponse] from the platform's WebAuthn API.
     * @return A [CompletableFuture] that completes with the next [DirectAuthenticationState].
     */
    fun proceedAsync(assertionResponse: WebAuthnAssertionResponse): CompletableFuture<DirectAuthenticationState> =
        coroutineScope.future {
            delegate.proceed(assertionResponse).toJvm()
        }
}
