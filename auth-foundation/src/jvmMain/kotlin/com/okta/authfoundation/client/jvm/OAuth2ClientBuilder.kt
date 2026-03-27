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
package com.okta.authfoundation.client.jvm

import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.client.Cache
import com.okta.authfoundation.client.CommonOAuth2Client
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.authfoundation.client.OidcClock
import kotlin.coroutines.CoroutineContext

/**
 * A Java-idiomatic builder for creating instances of [CommonOAuth2Client].
 *
 * This builder provides method-chaining setters for optional parameters and delegates
 * to the Kotlin [OAuth2ClientBuilder] for the actual construction.
 *
 * @param issuerUrl The base URL of the Authorization Server.
 * @param clientId The client ID of the application.
 * @param scope The OAuth 2.0 scopes the application is requesting.
 */
class OAuth2ClientBuilder(
    private val issuerUrl: String,
    private val clientId: String,
    private val scope: List<String>,
) {
    private var apiExecutor: ApiExecutor? = null
    private var clock: OidcClock? = null
    private var ioDispatcher: CoroutineContext? = null
    private var computeDispatcher: CoroutineContext? = null
    private var cache: Cache? = null
    private var authorizationServerId: String? = null
    private var clientSecret: String? = null
    private var acrValues: String? = null

    /**
     * Sets the HTTP executor used for all network requests.
     *
     * @param apiExecutor The [ApiExecutor] to use.
     * @return This builder for chaining.
     */
    fun setApiExecutor(apiExecutor: ApiExecutor): com.okta.authfoundation.client.jvm.OAuth2ClientBuilder =
        apply {
            this.apiExecutor = apiExecutor
        }

    /**
     * Sets the clock used for time-sensitive operations.
     *
     * @param clock The [OidcClock] to use.
     * @return This builder for chaining.
     */
    fun setClock(clock: OidcClock): com.okta.authfoundation.client.jvm.OAuth2ClientBuilder =
        apply {
            this.clock = clock
        }

    /**
     * Sets the dispatcher for IO-bound operations.
     *
     * @param dispatcher The [CoroutineContext] for IO.
     * @return This builder for chaining.
     */
    fun setIoDispatcher(dispatcher: CoroutineContext): com.okta.authfoundation.client.jvm.OAuth2ClientBuilder =
        apply {
            this.ioDispatcher = dispatcher
        }

    /**
     * Sets the dispatcher for compute-bound operations.
     *
     * @param dispatcher The [CoroutineContext] for computation.
     * @return This builder for chaining.
     */
    fun setComputeDispatcher(dispatcher: CoroutineContext): com.okta.authfoundation.client.jvm.OAuth2ClientBuilder =
        apply {
            this.computeDispatcher = dispatcher
        }

    /**
     * Sets the cache for optimizing network calls.
     *
     * @param cache The [Cache] to use.
     * @return This builder for chaining.
     */
    fun setCache(cache: Cache): com.okta.authfoundation.client.jvm.OAuth2ClientBuilder =
        apply {
            this.cache = cache
        }

    /**
     * Sets the authorization server ID.
     *
     * @param authorizationServerId The ID of the authorization server.
     * @return This builder for chaining.
     */
    fun setAuthorizationServerId(authorizationServerId: String): com.okta.authfoundation.client.jvm.OAuth2ClientBuilder =
        apply {
            this.authorizationServerId = authorizationServerId
        }

    /**
     * Sets the client secret for confidential clients.
     *
     * @param clientSecret The client secret.
     * @return This builder for chaining.
     */
    fun setClientSecret(clientSecret: String): com.okta.authfoundation.client.jvm.OAuth2ClientBuilder =
        apply {
            this.clientSecret = clientSecret
        }

    /**
     * Sets the ACR values.
     *
     * @param acrValues The ACR values string.
     * @return This builder for chaining.
     */
    fun setAcrValues(acrValues: String): com.okta.authfoundation.client.jvm.OAuth2ClientBuilder =
        apply {
            this.acrValues = acrValues
        }

    /**
     * Creates a [CommonOAuth2Client] instance with the configured parameters.
     *
     * @return A [AuthFoundationResult] containing the [CommonOAuth2Client] on success, or an exception on failure.
     */
    fun build(): AuthFoundationResult<CommonOAuth2Client> {
        val kotlinResult =
            OAuth2ClientBuilder.create(issuerUrl, clientId, scope) {
                this@OAuth2ClientBuilder.apiExecutor?.let { apiExecutor = it }
                this@OAuth2ClientBuilder.clock?.let { clock = it }
                this@OAuth2ClientBuilder.ioDispatcher?.let { ioDispatcher = it }
                this@OAuth2ClientBuilder.computeDispatcher?.let { computeDispatcher = it }
                this@OAuth2ClientBuilder.cache?.let { cache = it }
                this@OAuth2ClientBuilder.authorizationServerId?.let { authorizationServerId = it }
                this@OAuth2ClientBuilder.clientSecret?.let { clientSecret = it }
                this@OAuth2ClientBuilder.acrValues?.let { acrValues = it }
            }
        return AuthFoundationResult.fromKotlinResult(kotlinResult)
    }
}
