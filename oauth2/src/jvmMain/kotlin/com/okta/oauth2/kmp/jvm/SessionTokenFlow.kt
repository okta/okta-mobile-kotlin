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
import com.okta.oauth2.kmp.SessionTokenFlow as KotlinSessionTokenFlow

/**
 * A Java-friendly wrapper around the Kotlin [KotlinSessionTokenFlow].
 *
 * This class exposes async methods returning [CompletableFuture] so Java consumers
 * can use the Session Token flow without dealing with Kotlin coroutines.
 *
 * Typical Java usage:
 * ```java
 * SessionTokenFlow flow = new SessionTokenFlow(kmpClient);
 * TokenInfo token = flow.start(sessionToken, redirectUrl).get();
 * flow.close();
 * ```
 *
 * Must be [closed][close] when no longer needed to release coroutine resources.
 *
 * @param delegate the underlying Kotlin [KotlinSessionTokenFlow] instance.
 */
class SessionTokenFlow(
    private val delegate: KotlinSessionTokenFlow,
) : Closeable {
    /**
     * Creates a [SessionTokenFlow] backed by the given [KmpOAuth2Client].
     *
     * @param client the KMP OAuth2 client to use for the Session Token flow.
     */
    constructor(client: KmpOAuth2Client) : this(KotlinSessionTokenFlow(client))

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Initiates the Session Token flow asynchronously.
     *
     * @param sessionToken the session token obtained from the Okta legacy Authn API.
     * @param redirectUrl the redirect URL registered with the authorization server.
     * @param extraRequestParameters additional key-value pairs appended to the authorization URL.
     * @param scope the space-delimited scopes to request. Defaults to the client's configured default scope when null.
     * @return a [CompletableFuture] that completes with [TokenInfo] on success,
     *   or completes exceptionally on failure.
     */
    @JvmOverloads
    fun start(
        sessionToken: String,
        redirectUrl: String,
        extraRequestParameters: Map<String, String> = emptyMap(),
        scope: String? = null,
    ): CompletableFuture<TokenInfo> =
        coroutineScope.future {
            delegate.start(sessionToken, redirectUrl, extraRequestParameters, scope).getOrThrow()
        }

    override fun close() {
        coroutineScope.cancel()
    }
}
