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
import com.okta.authfoundation.client.kmp.AccessTokenValidator
import com.okta.authfoundation.client.kmp.DeviceSecretValidator
import com.okta.authfoundation.client.kmp.IdTokenValidator
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.authfoundation.client.kmp.RateLimitRetryConfig
import kotlinx.serialization.json.Json
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
    private var json: Json? = null
    private var cache: Cache? = null
    private var authorizationServerId: String? = null
    private var clientSecret: String = ""
    private var clientAssertionType: String = ""
    private var clientAssertion: String = ""
    private var acrValues: String? = null
    private var idTokenValidator: IdTokenValidator? = null
    private var accessTokenValidator: AccessTokenValidator? = null
    private var deviceSecretValidator: DeviceSecretValidator? = null
    private var endpointOverrides: com.okta.authfoundation.client.OAuth2EndpointOverrides? = null
    private var enablePushedAuthorizationRequests: Boolean? = null
    private var allowPushedAuthorizationRequestFallback: Boolean? = null
    private var rateLimitRetryCallback: ((retryCount: Int) -> RateLimitRetryConfig?)? = null

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
     * Sets the JSON serializer used for encoding/decoding responses.
     *
     * @param json The [Json] instance to use.
     * @return This builder for chaining.
     */
    fun setJson(json: Json): com.okta.authfoundation.client.jvm.OAuth2ClientBuilder =
        apply {
            this.json = json
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
     * Sets the client assertion JWT type for private_key_jwt clients.
     *
     * @param clientAssertionType The client assertion type.
     * @return This builder for chaining.
     */
    fun setClientAssertionType(clientAssertionType: String): com.okta.authfoundation.client.jvm.OAuth2ClientBuilder =
        apply {
            this.clientAssertionType = clientAssertionType
        }

    /**
     * Sets the client assertion JWT for private_key_jwt clients.
     *
     * @param clientAssertion The client assertion JWT.
     * @return This builder for chaining.
     */
    fun setClientAssertion(clientAssertion: String): com.okta.authfoundation.client.jvm.OAuth2ClientBuilder =
        apply {
            this.clientAssertion = clientAssertion
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
     * Sets the ID token validator. When set, ID tokens are validated after token refresh.
     *
     * @param idTokenValidator The [IdTokenValidator] to use.
     * @return This builder for chaining.
     */
    fun setIdTokenValidator(idTokenValidator: IdTokenValidator): com.okta.authfoundation.client.jvm.OAuth2ClientBuilder =
        apply {
            this.idTokenValidator = idTokenValidator
        }

    /**
     * Sets the access token validator. When set, access tokens are validated via `at_hash` claim.
     *
     * @param accessTokenValidator The [AccessTokenValidator] to use.
     * @return This builder for chaining.
     */
    fun setAccessTokenValidator(accessTokenValidator: AccessTokenValidator): com.okta.authfoundation.client.jvm.OAuth2ClientBuilder =
        apply {
            this.accessTokenValidator = accessTokenValidator
        }

    /**
     * Sets the device secret validator. When set, device secrets are validated via `ds_hash` claim.
     *
     * @param deviceSecretValidator The [DeviceSecretValidator] to use.
     * @return This builder for chaining.
     */
    fun setDeviceSecretValidator(deviceSecretValidator: DeviceSecretValidator): com.okta.authfoundation.client.jvm.OAuth2ClientBuilder =
        apply {
            this.deviceSecretValidator = deviceSecretValidator
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
     * Enables or disables Pushed Authorization Requests (PAR) for browser-based authorization flows.
     *
     * PAR is automatically considered only for custom authorization servers that advertise
     * `pushed_authorization_request_endpoint`.
     *
     * @param enablePushedAuthorizationRequests Whether PAR should be attempted.
     * @return This builder for chaining.
     */
    fun setEnablePushedAuthorizationRequests(enablePushedAuthorizationRequests: Boolean): com.okta.authfoundation.client.jvm.OAuth2ClientBuilder =
        apply {
            this.enablePushedAuthorizationRequests = enablePushedAuthorizationRequests
        }

    /**
     * Sets whether browser-based authorization flows may fall back to the classic authorization URL
     * when PAR is optional and unavailable/fails.
     *
     * @param allowPushedAuthorizationRequestFallback Whether the fallback is allowed.
     * @return This builder for chaining.
     */
    fun setAllowPushedAuthorizationRequestFallback(allowPushedAuthorizationRequestFallback: Boolean): com.okta.authfoundation.client.jvm.OAuth2ClientBuilder =
        apply {
            this.allowPushedAuthorizationRequestFallback = allowPushedAuthorizationRequestFallback
        }

    /**
     * Sets the callback invoked when an HTTP 429 rate-limit response is received.
     *
     * Return a [RateLimitRetryConfig] from [rateLimitRetryCallback] to retry the request, or `null`
     * to surface the failure immediately. The `retryCount` parameter is 0-based: 0 on the first
     * retry opportunity.
     *
     * @param rateLimitRetryCallback The callback to invoke on a 429 response.
     * @return This builder for chaining.
     */
    fun setRateLimitRetryCallback(rateLimitRetryCallback: (retryCount: Int) -> RateLimitRetryConfig?): com.okta.authfoundation.client.jvm.OAuth2ClientBuilder =
        apply {
            this.rateLimitRetryCallback = rateLimitRetryCallback
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
                this@OAuth2ClientBuilder.json?.let { json = it }
                this@OAuth2ClientBuilder.cache?.let { cache = it }
                this@OAuth2ClientBuilder.authorizationServerId?.let { authorizationServerId = it }
                clientSecret = this@OAuth2ClientBuilder.clientSecret
                clientAssertionType = this@OAuth2ClientBuilder.clientAssertionType
                clientAssertion = this@OAuth2ClientBuilder.clientAssertion
                this@OAuth2ClientBuilder.acrValues?.let { acrValues = it }
                this@OAuth2ClientBuilder.idTokenValidator?.let { idTokenValidator = it }
                this@OAuth2ClientBuilder.accessTokenValidator?.let { accessTokenValidator = it }
                this@OAuth2ClientBuilder.deviceSecretValidator?.let { deviceSecretValidator = it }
                this@OAuth2ClientBuilder.endpointOverrides?.let { endpointOverrides = it }
                this@OAuth2ClientBuilder.enablePushedAuthorizationRequests?.let { enablePushedAuthorizationRequests = it }
                this@OAuth2ClientBuilder.allowPushedAuthorizationRequestFallback?.let { allowPushedAuthorizationRequestFallback = it }
                this@OAuth2ClientBuilder.rateLimitRetryCallback?.let { rateLimitRetryCallback = it }
            }
        return AuthFoundationResult.fromKotlinResult(kotlinResult)
    }
}
