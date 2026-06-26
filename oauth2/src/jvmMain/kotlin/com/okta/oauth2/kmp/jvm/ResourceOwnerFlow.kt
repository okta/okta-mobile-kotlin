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
import com.okta.authfoundation.client.kmp.OAuth2Client as KmpOAuth2Client
import com.okta.oauth2.kmp.ResourceOwnerFlow as KotlinResourceOwnerFlow

/**
 * A Java-friendly wrapper around the Kotlin [KotlinResourceOwnerFlow].
 *
 * This class exposes async methods returning [CompletableFuture] so Java consumers
 * can use the Resource Owner flow without dealing with Kotlin coroutines.
 *
 * Typical Java usage:
 * ```java
 * ResourceOwnerFlow flow = new ResourceOwnerFlow(kmpClient);
 * TokenInfo token = flow.start(username, password, scope).get();
 * flow.close();
 * ```
 *
 * Must be [closed][close] when no longer needed to release coroutine resources.
 *
 * @param delegate the underlying Kotlin [KotlinResourceOwnerFlow] instance.
 */
class ResourceOwnerFlow(
    private val delegate: KotlinResourceOwnerFlow,
) : Closeable {
    /**
     * Creates a [ResourceOwnerFlow] backed by the given [KmpOAuth2Client].
     *
     * @param client the KMP OAuth2 client to use for the Resource Owner flow.
     */
    constructor(client: KmpOAuth2Client) : this(KotlinResourceOwnerFlow(client))

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Initiates the Resource Owner flow asynchronously.
     *
     * @param username the user's username or email.
     * @param password the user's password.
     * @param scope the scopes to request. Defaults to the client's configured default scope when null.
     * @return a [CompletableFuture] that completes with [TokenInfo] on success,
     *   or completes exceptionally on failure.
     */
    @JvmOverloads
    fun start(
        username: String,
        password: String,
        scope: String? = null,
    ): CompletableFuture<TokenInfo> =
        coroutineScope.future {
            delegate.start(username, password, scope).getOrThrow()
        }

    override fun close() {
        coroutineScope.cancel()
    }
}
