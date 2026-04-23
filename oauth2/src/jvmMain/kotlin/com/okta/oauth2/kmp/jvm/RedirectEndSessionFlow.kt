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

import com.okta.oauth2.kmp.BrowserRedirectHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import com.okta.oauth2.kmp.RedirectEndSessionFlow as KotlinRedirectEndSessionFlow

/**
 * A Java-friendly wrapper around the Kotlin [KotlinRedirectEndSessionFlow].
 *
 * This class combines the [start][KotlinRedirectEndSessionFlow.start] and
 * [resume][KotlinRedirectEndSessionFlow.resume] steps into a single
 * [start] method that uses a [BrowserRedirectHandler] to open the browser and capture
 * the redirect callback. Java consumers can use [CompletableFuture] without dealing
 * with Kotlin coroutines.
 *
 * Must be [closed][close] when no longer needed to release coroutine resources.
 *
 * @param delegate the underlying Kotlin [KotlinRedirectEndSessionFlow] instance.
 */
class RedirectEndSessionFlow(
    private val delegate: KotlinRedirectEndSessionFlow,
) : Closeable {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Performs the full redirect end-session flow asynchronously.
     *
     * Builds the logout URL, opens it via [browserRedirectHandler], and validates the redirect.
     *
     * @param idToken the ID token hint identifying the session to log out.
     * @param redirectUrl the post-logout redirect URI registered with the authorization server.
     * @param browserRedirectHandler handles opening the browser and capturing the redirect URI.
     * @param extraRequestParameters additional key-value pairs appended to the end-session URL.
     * @return a [CompletableFuture] that completes with [Unit] on success,
     *   or completes exceptionally on failure.
     */
    @JvmOverloads
    fun start(
        idToken: String,
        redirectUrl: String,
        browserRedirectHandler: BrowserRedirectHandler,
        extraRequestParameters: Map<String, String> = emptyMap(),
    ): CompletableFuture<Unit> =
        coroutineScope.future {
            val flowContext =
                delegate
                    .start(
                        idToken = idToken,
                        redirectUrl = redirectUrl,
                        extraRequestParameters = extraRequestParameters
                    ).getOrThrow()
            val redirectUri = browserRedirectHandler.handleRedirect(flowContext.url)
            delegate.resume(redirectUri, flowContext).getOrThrow()
        }

    override fun close() {
        coroutineScope.cancel()
    }
}
