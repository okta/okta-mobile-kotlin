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
package com.okta.authfoundation.client

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.client.kmp.AccessTokenValidator
import com.okta.authfoundation.client.kmp.DefaultAccessTokenValidator
import com.okta.authfoundation.client.kmp.DefaultDeviceSecretValidator
import com.okta.authfoundation.client.kmp.DefaultIdTokenValidator
import com.okta.authfoundation.client.kmp.DeviceSecretValidator
import com.okta.authfoundation.client.kmp.IdTokenValidator
import com.okta.authfoundation.client.kmp.RateLimitRetryConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

/**
 * Immutable configuration for [OAuth2Client].
 *
 * Created via [OAuth2ClientBuilder]. Contains all settings needed to construct and operate an OAuth2 client
 * without requiring global singletons.
 */
class OAuth2ClientConfiguration internal constructor(
    /** The application's client ID. */
    val clientId: String,
    /** The default access scopes required by the client. */
    val defaultScope: List<String>,
    /**
     * The effective issuer URL used for OIDC discovery and token validation.
     *
     * Derived by [OAuth2ClientBuilder] from the base org URL and [authorizationServerId]:
     * - No [authorizationServerId]: the base org URL itself.
     * - With [authorizationServerId]: `"$baseUrl/oauth2/$authorizationServerId"`.
     */
    val issuerUrl: String,
    /** The HTTP executor used for all network requests. */
    val apiExecutor: ApiExecutor,
    /** The clock used for time-related operations (token expiry, JWT validation). */
    val clock: OidcClock,
    /** The JSON serializer for encoding/decoding responses. */
    val json: Json,
    /** The cache used to optimize network calls. */
    val cache: Cache,
    /**
     * The authorization server ID provided at build time, or null for the org authorization server.
     *
     * This is stored for reference; the effective issuer URL is already reflected in [issuerUrl].
     */
    val authorizationServerId: String?,
    /** Optional client secret for confidential clients. */
    val clientSecret: String = "",
    /**
     * Optional provider for private_key_jwt (or similar JWT-based) client authentication.
     * Mutually exclusive with [clientSecret]. See [ClientAssertionProvider].
     */
    val clientAssertionProvider: ClientAssertionProvider? = null,
    /** Optional ACR values. */
    val acrValues: String?,
    /** Validator for ID tokens. ID tokens are validated after token refresh. */
    val idTokenValidator: IdTokenValidator = DefaultIdTokenValidator(),
    /** Validator for access tokens. Validates `at_hash` claim against the access token hash. */
    val accessTokenValidator: AccessTokenValidator = DefaultAccessTokenValidator(),
    /** Validator for device secrets. Validates `ds_hash` claim against the device secret hash. */
    val deviceSecretValidator: DeviceSecretValidator = DefaultDeviceSecretValidator(),
    /** Optional per-endpoint URL overrides. Non-null fields win over discovery results. */
    val endpointOverrides: OAuth2EndpointOverrides? = null,
    /**
     * Enables Pushed Authorization Requests (PAR) for browser-based authorization flows.
     *
     * Disabled by default. When enabled, supported flows use PAR whenever the discovered
     * authorization server metadata advertises a `pushed_authorization_request_endpoint` — this
     * applies to the org authorization server as well as custom ones; it is not gated on which
     * kind of server is configured. Independently of this setting, a server that advertises
     * `require_pushed_authorization_requests` always uses PAR.
     */
    val enablePushedAuthorizationRequests: Boolean = false,
    /**
     * Allows browser-based authorization flows to fall back to the classic authorization request
     * URL when PAR is optional but unavailable or the push request fails.
     *
     * Disabled by default (fail-closed): a PAR failure surfaces as a thrown exception (e.g.
     * oauth2's `AuthorizationCodeFlow.PushedAuthorizationRequestException`) from the calling flow,
     * rather than silently downgrading to a request that omits PAR's replay/tampering protections.
     * Set to `true` to allow that downgrade instead — note that the PAR failure's cause is not
     * otherwise surfaced (no logging or event) when the fallback succeeds, so this is an explicit
     * trade of visibility for availability.
     */
    val allowPushedAuthorizationRequestFallback: Boolean = false,
    /**
     * Optional callback invoked when an HTTP 429 rate-limit response is received.
     *
     * The callback receives the current [retryCount] (0-based: 0 on first retry opportunity) and
     * returns a [RateLimitRetryConfig] to retry, or `null` to stop retrying and surface the
     * failure to the caller. When this callback is `null` (the default), 429 responses are never
     * retried and are surfaced as [com.okta.authfoundation.client.OAuth2ClientResult.Error]
     * immediately.
     *
     * If the callback throws, the exception propagates to the caller without retrying.
     *
     * ```kotlin
     * rateLimitRetryCallback = { retryCount ->
     *     if (retryCount < 3) RateLimitRetryConfig(MaxRetries(3), MinDelaySeconds(1L))
     *     else null
     * }
     * ```
     */
    val rateLimitRetryCallback: ((retryCount: Int) -> RateLimitRetryConfig?)? = null,
    /**
     * The dispatcher used for CPU-bound work, such as invoking [clientAssertionProvider].
     */
    val computeDispatcher: CoroutineContext = Dispatchers.Default,
) {
    /**
     * Builds the client-authentication form parameters for a request to [audience] (the exact
     * endpoint URL being called), invoking [clientAssertionProvider] fresh if configured so its
     * assertion can carry a unique `jti` and a correctly scoped `aud`/`exp`.
     *
     * [clientAssertionProvider] is invoked on [computeDispatcher] — never on whatever dispatcher
     * the caller happens to be on — since signing an assertion is CPU-bound work that must not
     * block a UI/main thread.
     *
     * Internal cross-module API: called by flow implementations (e.g. in the `oauth2` module),
     * not intended as public SDK surface.
     */
    @InternalAuthFoundationApi
    suspend fun clientAuthenticationFormParameters(audience: String): Map<String, String> =
        when {
            clientAssertionProvider != null -> {
                val assertion = withContext(computeDispatcher) { clientAssertionProvider.provide(audience) }
                mapOf(
                    "client_assertion_type" to assertion.type,
                    "client_assertion" to assertion.assertion
                )
            }

            clientSecret.isNotBlank() -> {
                mapOf("client_secret" to clientSecret)
            }

            else -> {
                emptyMap()
            }
        }
}
