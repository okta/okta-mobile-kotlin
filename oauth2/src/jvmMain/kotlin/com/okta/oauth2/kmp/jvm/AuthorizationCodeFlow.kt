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
import com.okta.oauth2.kmp.BrowserRedirectHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import com.okta.oauth2.kmp.AuthorizationCodeFlow as KotlinAuthorizationCodeFlow

/**
 * A Java-friendly wrapper around the Kotlin [KotlinAuthorizationCodeFlow].
 *
 * This class combines the [start][KotlinAuthorizationCodeFlow.start] and
 * [resume][KotlinAuthorizationCodeFlow.resume] steps into a single
 * [start] method that uses a [BrowserRedirectHandler] to open the browser and capture
 * the redirect callback. Java consumers can use [CompletableFuture] without dealing
 * with Kotlin coroutines.
 *
 * Must be [closed][close] when no longer needed to release coroutine resources.
 *
 * @param delegate the underlying Kotlin [KotlinAuthorizationCodeFlow] instance.
 */
class AuthorizationCodeFlow(
    private val delegate: KotlinAuthorizationCodeFlow,
) : Closeable {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Performs the full Authorization Code flow asynchronously.
     *
     * Opens [redirectUrl] in a browser via [browserRedirectHandler], waits for the
     * redirect callback, and exchanges the authorization code for tokens.
     *
     * @param redirectUrl the registered redirect URI for this client.
     * @param browserRedirectHandler handles opening the browser and capturing the redirect.
     * @param extraRequestParameters additional authorization endpoint parameters.
     * @param scope the scopes to request.
     * @return a [CompletableFuture] that completes with [TokenInfo] on success,
     *   or completes exceptionally on failure.
     */
    @JvmOverloads
    fun start(
        redirectUrl: String,
        browserRedirectHandler: BrowserRedirectHandler,
        extraRequestParameters: Map<String, String> = emptyMap(),
        scope: String = "openid profile email offline_access",
    ): CompletableFuture<TokenInfo> =
        coroutineScope.future {
            val flowContext =
                delegate
                    .start(
                        redirectUrl = redirectUrl,
                        extraRequestParameters = extraRequestParameters,
                        scope = scope
                    ).getOrThrow()
            val redirectUri = browserRedirectHandler.handleRedirect(flowContext.url)
            delegate.resume(redirectUri, flowContext).getOrThrow()
        }

    override fun close() {
        coroutineScope.cancel()
    }
}
