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
import com.okta.authfoundation.api.http.KtorHttpExecutor
import com.okta.authfoundation.client.internal.EndpointDiscovery
import com.okta.authfoundation.client.kmp.AccessTokenValidator
import com.okta.authfoundation.client.kmp.DefaultAccessTokenValidator
import com.okta.authfoundation.client.kmp.DefaultDeviceSecretValidator
import com.okta.authfoundation.client.kmp.DefaultIdTokenValidator
import com.okta.authfoundation.client.kmp.DeviceSecretValidator
import com.okta.authfoundation.client.kmp.IdTokenValidator
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.authfoundation.util.CoalescingOrchestrator
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/**
 * Builder for creating an [OAuth2Client].
 *
 * Use the [create] factory method to construct an instance.
 *
 * Pass the base org URL as [issuerUrl] and optionally set [authorizationServerId] to target a
 * custom authorization server. The effective issuer URL used for OIDC discovery is derived as:
 * - No [authorizationServerId]: `issuerUrl` is used as-is (org authorization server).
 * - With [authorizationServerId]: `"$issuerUrl/oauth2/$authorizationServerId"` (custom authorization server).
 *
 * ```kotlin
 * // Org authorization server
 * val orgClient = OAuth2ClientBuilder.create(
 *     issuerUrl = "https://your-okta-domain.okta.com",
 *     clientId = "your-client-id",
 *     scope = listOf("openid", "profile", "offline_access"),
 * ).getOrThrow()
 *
 * // Custom authorization server
 * val customClient = OAuth2ClientBuilder.create(
 *     issuerUrl = "https://your-okta-domain.okta.com",
 *     clientId = "your-client-id",
 *     scope = listOf("openid", "profile", "offline_access"),
 * ) {
 *     authorizationServerId = "default"
 * }.getOrThrow()
 * ```
 */
class OAuth2ClientBuilder private constructor(
    private val issuerUrl: String,
    private val clientId: String,
    private val scope: String,
) {
    /** The HTTP executor used for all network requests. */
    var apiExecutor: ApiExecutor = KtorHttpExecutor()

    /** The clock used for time-related operations. */
    var clock: OidcClock = OidcClock { Clock.System.now().epochSeconds }

    /** The dispatcher for IO-bound operations. */
    var ioDispatcher: CoroutineContext = Dispatchers.IO

    /** The dispatcher for compute-bound operations. */
    var computeDispatcher: CoroutineContext = Dispatchers.Default

    /** The JSON serializer for encoding/decoding responses. */
    var json: Json = Json { ignoreUnknownKeys = true }

    /** The cache for optimizing network calls. */
    var cache: Cache = NoOpCache()

    /**
     * Optional authorization server ID.
     *
     * When set, the effective issuer URL for OIDC discovery is constructed as
     * `"$issuerUrl/oauth2/$authorizationServerId"`. When null or blank, [issuerUrl] is used
     * directly (org authorization server).
     *
     * Use `"default"` for the Okta-provisioned custom authorization server, or any other
     * custom authorization server ID from your Okta org.
     */
    var authorizationServerId: String? = null

    /** Optional client secret for confidential clients. */
    var clientSecret: String? = null

    /** Optional ACR values. */
    var acrValues: String? = null

    /** ID token validator. When set, ID tokens are validated after token refresh. */
    var idTokenValidator: IdTokenValidator = DefaultIdTokenValidator()

    /** Access token validator. When set, access tokens are validated via `at_hash` claim. */
    var accessTokenValidator: AccessTokenValidator = DefaultAccessTokenValidator()

    /** Device secret validator. When set, device secrets are validated via `ds_hash` claim. */
    var deviceSecretValidator: DeviceSecretValidator = DefaultDeviceSecretValidator()

    /**
     * Optional per-endpoint URL overrides.
     *
     * When set, each non-null field replaces the corresponding URL from the OpenID Connect
     * discovery document. When all 8 fields are non-null, the discovery HTTP call is skipped
     * entirely. All non-null override values must be valid HTTPS URLs.
     *
     * Example:
     * ```kotlin
     * endpointOverrides = OAuth2EndpointOverrides(
     *     tokenEndpoint = "https://proxy.example.com/token"
     * )
     * ```
     *
     * @see OAuth2EndpointOverrides
     */
    var endpointOverrides: OAuth2EndpointOverrides? = null

    companion object {
        /**
         * Creates an [OAuth2Client] with the given parameters and optional customization.
         *
         * @param issuerUrl the base URL of the Okta org (e.g. `"https://your-domain.okta.com"`).
         *   Must use HTTPS. When [authorizationServerId] is also set, the effective issuer for
         *   OIDC discovery becomes `"$issuerUrl/oauth2/$authorizationServerId"`.
         * @param clientId the application's client ID (must not be blank).
         * @param scope the default access scopes (must not be empty).
         * @param buildAction optional configuration block for customizing builder properties,
         *   including [authorizationServerId] for targeting a custom authorization server.
         * @return [Result] containing the built [OAuth2Client], or a failure with
         *   [IllegalArgumentException] if validation fails.
         */
        fun create(
            issuerUrl: String,
            clientId: String,
            scope: List<String>,
            buildAction: (OAuth2ClientBuilder.() -> Unit)? = null,
        ): Result<OAuth2Client> =
            runCatching {
                require(
                    runCatching {
                        val url = Url(issuerUrl)
                        url.protocol == URLProtocol.HTTPS && url.host.isNotBlank()
                    }.getOrDefault(false)
                ) { "issuerUrl must be a valid https URL." }

                require(clientId.isNotBlank()) { "clientId must be set and not empty." }
                require(scope.isNotEmpty()) { "scope must be set and not empty." }

                val builder = OAuth2ClientBuilder(issuerUrl, clientId, scope.joinToString(" "))
                buildAction?.invoke(builder)

                // Validate endpoint override URLs
                builder.endpointOverrides?.let { overrides ->
                    fun validateOverrideUrl(
                        value: String?,
                        fieldName: String,
                    ) {
                        if (value == null) return
                        require(
                            runCatching {
                                val url = Url(value)
                                url.protocol == URLProtocol.HTTPS && url.host.isNotBlank()
                            }.getOrDefault(false)
                        ) { "endpointOverrides.$fieldName must be a valid https URL." }
                    }
                    validateOverrideUrl(overrides.authorizationEndpoint, "authorizationEndpoint")
                    validateOverrideUrl(overrides.tokenEndpoint, "tokenEndpoint")
                    validateOverrideUrl(overrides.userInfoEndpoint, "userInfoEndpoint")
                    validateOverrideUrl(overrides.jwksUri, "jwksUri")
                    validateOverrideUrl(overrides.introspectionEndpoint, "introspectionEndpoint")
                    validateOverrideUrl(overrides.revocationEndpoint, "revocationEndpoint")
                    validateOverrideUrl(overrides.endSessionEndpoint, "endSessionEndpoint")
                    validateOverrideUrl(overrides.deviceAuthorizationEndpoint, "deviceAuthorizationEndpoint")
                }

                val config = builder.build()
                val discovery = EndpointDiscovery(config)
                @OptIn(InternalAuthFoundationApi::class)
                OAuth2Client(
                    config,
                    CoalescingOrchestrator(
                        factory = { discovery.discover() },
                        keepDataInMemory = { it is OAuth2ClientResult.Success }
                    )
                )
            }
    }

    private fun effectiveIssuerUrl(): String {
        val url = Url(issuerUrl)
        val portSuffix = if (url.port == url.protocol.defaultPort) "" else ":${url.port}"
        val base = "${url.protocol.name}://${url.host}$portSuffix"
        return if (authorizationServerId.isNullOrBlank()) base else "$base/oauth2/$authorizationServerId"
    }

    private fun build(): OAuth2ClientConfiguration =
        OAuth2ClientConfiguration(
            clientId = clientId,
            defaultScope = scope,
            issuerUrl = effectiveIssuerUrl(),
            apiExecutor = apiExecutor,
            clock = clock,
            json = json,
            cache = cache,
            authorizationServerId = authorizationServerId,
            clientSecret = clientSecret,
            acrValues = acrValues,
            idTokenValidator = idTokenValidator,
            accessTokenValidator = accessTokenValidator,
            deviceSecretValidator = deviceSecretValidator,
            endpointOverrides = endpointOverrides
        )
}
