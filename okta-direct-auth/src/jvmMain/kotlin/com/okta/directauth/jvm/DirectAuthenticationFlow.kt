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

import com.okta.directauth.model.PrimaryFactor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import com.okta.directauth.api.DirectAuthenticationFlow as KotlinDirectAuthenticationFlow

/**
 * A Java-friendly wrapper around the Kotlin [KotlinDirectAuthenticationFlow].
 *
 * This class provides async methods returning [CompletableFuture] and
 * a listener-based API for observing authentication state changes.
 *
 * @param delegate The underlying Kotlin [KotlinDirectAuthenticationFlow] instance.
 */
class DirectAuthenticationFlow internal constructor(
    private val delegate: KotlinDirectAuthenticationFlow,
) : Closeable {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val listeners = CopyOnWriteArrayList<Consumer<DirectAuthenticationState>>()

    init {
        coroutineScope.launch {
            delegate.authenticationState.collect { state ->
                val jvmState = state.toJvm()
                listeners.forEach { it.accept(jvmState) }
            }
        }
    }

    /**
     * Starts the direct authentication flow asynchronously.
     *
     * @param loginHint A hint about the user's identity (e.g., username or email).
     * @param primaryFactor The initial authentication factor (e.g., [PrimaryFactor.Password]).
     * @return A [CompletableFuture] that completes with the resulting [DirectAuthenticationState].
     */
    fun startAsync(
        loginHint: String,
        primaryFactor: PrimaryFactor,
    ): CompletableFuture<DirectAuthenticationState> =
        coroutineScope.future {
            delegate.start(loginHint, primaryFactor).toJvm()
        }

    /**
     * Resets the authentication flow to [DirectAuthenticationState.Idle].
     *
     * @return The reset [DirectAuthenticationState].
     */
    fun reset(): DirectAuthenticationState = delegate.reset().toJvm()

    /**
     * Returns the current authentication state.
     *
     * @return The current [DirectAuthenticationState].
     */
    fun getAuthenticationState(): DirectAuthenticationState = delegate.authenticationState.value.toJvm()

    /**
     * Registers a listener that is notified whenever the authentication state changes.
     *
     * @param listener A [Consumer] that receives [DirectAuthenticationState] updates.
     * @return A [Closeable] that, when closed, removes the listener.
     */
    fun addStateListener(listener: Consumer<DirectAuthenticationState>): Closeable {
        listeners.add(listener)
        return Closeable { listeners.remove(listener) }
    }

    /**
     * Removes a previously registered state listener.
     *
     * @param listener The [Consumer] to remove.
     */
    fun removeStateListener(listener: Consumer<DirectAuthenticationState>) {
        listeners.remove(listener)
    }

    /**
     * Closes this flow, cancelling all in-flight operations.
     *
     * Pending [CompletableFuture] instances will complete exceptionally
     * with [java.util.concurrent.CancellationException].
     */
    override fun close() {
        coroutineScope.cancel()
    }
}
