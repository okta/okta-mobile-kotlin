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
import com.okta.authfoundation.client.kmp.OAuth2Client as KmpOAuth2Client
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
 * Typical Java usage:
 * ```java
 * AuthorizationCodeFlow flow = new AuthorizationCodeFlow(kmpClient);
 * TokenInfo token = flow.start(redirectUrl, browserHandler, scope).get();
 * flow.close();
 * ```
 *
 * Must be [closed][close] when no longer needed to release coroutine resources.
 *
 * **Pushed Authorization Requests (PAR) note**: because this wrapper performs the full
 * start+resume round trip internally, the intermediate
 * [com.okta.oauth2.kmp.AuthorizationCodeFlowContext] —
 * including `usedPushedAuthorizationRequest`/`pushedAuthorizationRequestUri` — is never
 * constructed as a value Java callers can observe; it's an intermediate `flowContext` local in
 * [start] below. PAR itself is fully supported here and is configured on the client via
 * `OAuth2ClientBuilder.setEnablePushedAuthorizationRequests`/`setAllowPushedAuthorizationRequestFallback` —
 * only the diagnostic "did this call use PAR" signal is unavailable to Java callers. The two PAR
 * failure exceptions ([KotlinAuthorizationCodeFlow.PushedAuthorizationRequiredException] and
 * [KotlinAuthorizationCodeFlow.PushedAuthorizationRequestException]) propagate normally through the
 * returned [CompletableFuture] (retrievable via `ExecutionException.getCause()`), so no error
 * information is lost — only the successful-PAR-usage signal.
 *
 * @param delegate the underlying Kotlin [KotlinAuthorizationCodeFlow] instance.
 */
class AuthorizationCodeFlow(
    private val delegate: KotlinAuthorizationCodeFlow,
) : Closeable {
    /**
     * Creates an [AuthorizationCodeFlow] backed by the given [KmpOAuth2Client].
     *
     * @param client the KMP OAuth2 client to use for the Authorization Code flow.
     */
    constructor(client: KmpOAuth2Client) : this(KotlinAuthorizationCodeFlow(client))

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Performs the full Authorization Code flow asynchronously.
     *
     * Opens [redirectUrl] in a browser via [browserRedirectHandler], waits for the
     * redirect callback, and exchanges the authorization code for tokens.
     *
     * @param redirectUrl the registered redirect URI for this client.
     * @param browserRedirectHandler handles opening the browser and capturing the redirect.
     * @param scope the scopes to request.
     * @param extraRequestParameters additional authorization endpoint parameters.
     * @return a [CompletableFuture] that completes with [TokenInfo] on success, or completes
     *   exceptionally on failure — including with
     *   [KotlinAuthorizationCodeFlow.PushedAuthorizationRequiredException] when the authorization
     *   server requires PAR, but it cannot be used, or
     *   [KotlinAuthorizationCodeFlow.PushedAuthorizationRequestException] when a PAR request fails
     *   and fallback is disabled (retrievable via `ExecutionException.getCause()`).
     */
    @JvmOverloads
    fun start(
        redirectUrl: String,
        browserRedirectHandler: BrowserRedirectHandler,
        scope: List<String>,
        extraRequestParameters: Map<String, String> = emptyMap(),
    ): CompletableFuture<TokenInfo> =
        coroutineScope.future {
            val flowContext =
                delegate
                    .start(
                        redirectUrl = redirectUrl,
                        scope = scope,
                        extraRequestParameters = extraRequestParameters
                    ).getOrThrow()
            val redirectUri = browserRedirectHandler.handleRedirect(flowContext.url)
            delegate.resume(redirectUri, flowContext).getOrThrow()
        }

    override fun close() {
        coroutineScope.cancel()
    }
}
