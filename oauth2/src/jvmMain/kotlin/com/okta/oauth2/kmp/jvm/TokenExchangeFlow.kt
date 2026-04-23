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
package com.okta.oauth2.kmp.jvm

import com.okta.authfoundation.client.TokenInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import com.okta.oauth2.kmp.TokenExchangeFlow as KotlinTokenExchangeFlow

/**
 * A Java-friendly wrapper around the Kotlin [KotlinTokenExchangeFlow].
 *
 * This class exposes async methods returning [CompletableFuture] so Java consumers
 * can use the Token Exchange flow without dealing with Kotlin coroutines.
 *
 * Must be [closed][close] when no longer needed to release coroutine resources.
 *
 * @param delegate the underlying Kotlin [KotlinTokenExchangeFlow] instance.
 */
class TokenExchangeFlow(
    private val delegate: KotlinTokenExchangeFlow,
) : Closeable {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Initiates the token exchange flow asynchronously.
     *
     * @param idToken the ID token for the user.
     * @param deviceSecret the device secret from a previous authentication flow.
     * @param audience optional audience; pass null to omit from the request.
     * @param scope the scopes to request.
     * @return a [CompletableFuture] that completes with [TokenInfo] on success,
     *   or completes exceptionally on failure.
     */
    @JvmOverloads
    fun start(
        idToken: String,
        deviceSecret: String,
        audience: String? = null,
        scope: String = "openid profile email offline_access",
    ): CompletableFuture<TokenInfo> =
        coroutineScope.future {
            delegate.start(idToken, deviceSecret, audience, scope).getOrThrow()
        }

    override fun close() {
        coroutineScope.cancel()
    }
}
