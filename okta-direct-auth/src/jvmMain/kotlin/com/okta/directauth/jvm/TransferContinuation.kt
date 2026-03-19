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

import com.okta.directauth.model.DirectAuthContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

/**
 * A Java-friendly wrapper around [DirectAuthContinuation.Transfer].
 *
 * Provides an async method for polling the status of an out-of-band authentication.
 *
 * @param delegate The underlying Kotlin [DirectAuthContinuation.Transfer] instance.
 */
class TransferContinuation(
    private val delegate: DirectAuthContinuation.Transfer,
) : DirectAuthenticationState(delegate) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The number of seconds until the challenge expires.
     */
    val expirationInSeconds: Int = delegate.expirationInSeconds

    /**
     * A code that can be displayed to the user to link devices or verify the transaction.
     */
    val bindingCode: String? = delegate.bindingCode

    /**
     * Proceeds by polling the server for the out-of-band authentication result.
     *
     * @return A [CompletableFuture] that completes with the next [DirectAuthenticationState].
     */
    fun proceedAsync(): CompletableFuture<DirectAuthenticationState> =
        coroutineScope.future {
            delegate.proceed().toJvm()
        }
}
