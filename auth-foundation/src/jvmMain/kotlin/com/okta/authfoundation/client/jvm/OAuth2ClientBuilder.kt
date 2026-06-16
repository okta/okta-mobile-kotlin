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
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.authfoundation.client.OidcClock
import com.okta.authfoundation.client.kmp.OAuth2Client
import kotlin.coroutines.CoroutineContext

/**
 * A Java-idiomatic builder for creating instances of [OAuth2Client].
 *
 * This builder provides method-chaining setters for optional parameters and delegates
 * to the Kotlin [OAuth2ClientBuilder] for the actual construction.
 *
 * Pass the base org URL as [issuerUrl] and optionally call [setAuthorizationServerId] to target a
 * custom authorization server. The effective issuer URL used for OIDC discovery is derived as:
 * - No authorization server ID: [issuerUrl] is used as-is (org authorization server).
 * - With authorization server ID: `"$issuerUrl/oauth2/$authorizationServerId"` (custom authorization server).
 *
 * @param issuerUrl The base URL of the Okta org (e.g. `"https://your-domain.okta.com"`). Must use HTTPS.
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
    private var endpointOverrides: com.okta.authfoundation.client.OAuth2EndpointOverrides? = null

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
     * Sets the authorization server ID used to target a custom authorization server.
     *
     * When set, the effective issuer URL for OIDC discovery is constructed as
     * `"$issuerUrl/oauth2/$authorizationServerId"`. Use `"default"` for the Okta-provisioned
     * custom authorization server, or any other custom authorization server ID from your Okta org.
     *
     * @param authorizationServerId The ID of the custom authorization server.
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
     * Sets optional per-endpoint URL overrides.
     *
     * When provided, each non-null field in [overrides] replaces the corresponding URL from
     * the OpenID Connect discovery document. When all 8 fields are non-null, the discovery
     * HTTP call is skipped entirely. All non-null values must be valid HTTPS URLs.
     *
     * @param overrides The [OAuth2EndpointOverrides] to apply.
     * @return This builder for chaining.
     */
    fun setEndpointOverrides(overrides: com.okta.authfoundation.client.OAuth2EndpointOverrides): com.okta.authfoundation.client.jvm.OAuth2ClientBuilder =
        apply {
            this.endpointOverrides = overrides
        }

    /**
     * Creates an [OAuth2Client] instance with the configured parameters.
     *
     * @return A [AuthFoundationResult] containing the [OAuth2Client] on success, or an exception on failure.
     */
    fun build(): AuthFoundationResult<OAuth2Client> {
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
                this@OAuth2ClientBuilder.endpointOverrides?.let { endpointOverrides = it }
            }
        return AuthFoundationResult.fromKotlinResult(kotlinResult)
    }
}
