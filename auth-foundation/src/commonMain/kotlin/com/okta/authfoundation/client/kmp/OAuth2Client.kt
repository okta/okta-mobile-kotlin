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
package com.okta.authfoundation.client.kmp

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.client.OAuth2ClientConfiguration
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.dto.DeviceAuthorizationInfo
import com.okta.authfoundation.client.dto.IntrospectInfo
import com.okta.authfoundation.client.dto.OidcUserInfo
import com.okta.authfoundation.client.events.TokenRefreshedEvent
import com.okta.authfoundation.client.events.TokenRevokedEvent
import com.okta.authfoundation.client.internal.EndpointDiscovery
import com.okta.authfoundation.client.internal.OAuth2Endpoints
import com.okta.authfoundation.client.internal.OAuth2TokenResponse
import com.okta.authfoundation.client.internal.SerializableDeviceAuthorizationResponse
import com.okta.authfoundation.client.internal.performFormPost
import com.okta.authfoundation.client.internal.performJsonFormPost
import com.okta.authfoundation.client.internal.performJsonGetRequest
import com.okta.authfoundation.client.kmp.events.TokenCreatedEvent
import com.okta.authfoundation.client.kmp.events.ValidateAccessTokenEvent
import com.okta.authfoundation.client.kmp.events.ValidateIdTokenEvent
import com.okta.authfoundation.events.Event
import com.okta.authfoundation.jwt.Jwks
import com.okta.authfoundation.jwt.JwtParser
import com.okta.authfoundation.jwt.SerializableJwks
import com.okta.authfoundation.util.CoalescingOrchestrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonObject

/**
 * A cross-platform OAuth2 client for interacting with an Okta Authorization Server.
 *
 * This client is **storage-agnostic** — it handles HTTP calls, token validation, and event
 * emission but never persists tokens. To store tokens, use
 * [TokenCredentialManager][com.okta.authfoundation.credential.kmp.TokenCredentialManager]:
 *
 * ```kotlin
 * // 1. Create the client
 * val client = OAuth2ClientBuilder.create(issuerUrl, clientId, scope).getOrThrow()
 *
 * // 2. Perform a token request (e.g., from a flow class)
 * val tokenInfo = client.tokenRequest(formParams).getOrThrow()
 *
 * // 3. Persist via TokenCredentialManager
 * val tokenData = TokenData(tokenInfo, client.configuration)
 * val credential = manager.store(tokenData).getOrThrow()
 * ```
 *
 * Use [OAuth2ClientBuilder] to create an instance via the builder pattern.
 *
 * On Android, the existing OAuth2Client class provides backward-compatible access
 * with additional platform-specific features.
 */
@OptIn(InternalAuthFoundationApi::class)
class OAuth2Client internal constructor(
    /** The configuration for this client. */
    val configuration: OAuth2ClientConfiguration,
    internal val endpointsOrchestrator: CoalescingOrchestrator<OAuth2ClientResult<OAuth2Endpoints>>,
) {
    private val _events =
        MutableSharedFlow<Event>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    /**
     * A read-only flow of OAuth2 lifecycle events emitted by this client.
     *
     * Events are multicast — all active collectors receive every event independently.
     * There is no replay; events emitted before a collector subscribes are not delivered.
     * When no collectors are active, events are silently dropped (fire-and-forget semantics).
     * The emitter is never suspended; if the buffer is full, the oldest event is dropped.
     */
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val jwksOrchestrator: CoalescingOrchestrator<Result<Jwks>> =
        CoalescingOrchestrator(
            factory = ::fetchJwks,
            keepDataInMemory = { it.isSuccess }
        )

    /**
     * Performs the OIDC User Info call.
     *
     * @param accessToken the access token used for authorization.
     * @return [Result.success] with [OidcUserInfo], or [Result.failure] with:
     * - [OAuth2ClientResult.Error.OidcEndpointsNotAvailableException] if endpoints cannot be resolved.
     * - [OAuth2ClientResult.Error.HttpResponseException] if the server returns an error.
     * - Other exceptions for network or parsing failures.
     */
    suspend fun getUserInfo(accessToken: String): Result<OidcUserInfo> =
        runCatching {
            val endpoint =
                endpointsOrNull()?.userInfoEndpoint
                    ?: throw OAuth2ClientResult.Error.OidcEndpointsNotAvailableException()

            val result =
                performJsonGetRequest(
                    apiExecutor = configuration.apiExecutor,
                    json = configuration.json,
                    url = endpoint,
                    deserializer = JsonObject.serializer(),
                    headers =
                        mapOf(
                            "Accept" to listOf("application/json"),
                            "Authorization" to listOf("Bearer $accessToken")
                        ),
                    onRateLimitExceeded = { event -> _events.tryEmit(event) }
                )
            when (result) {
                is OAuth2ClientResult.Success -> {
                    val claimsProvider =
                        com.okta.authfoundation.claims
                            .DefaultClaimsProvider(result.result, configuration.json)
                    OidcUserInfo(claimsProvider)
                }

                is OAuth2ClientResult.Error -> {
                    throw result.exception
                }
            }
        }

    /**
     * Attempt to refresh a token using the provided refresh token string.
     *
     * On success, a [TokenRefreshedEvent] is emitted on [events].
     * If an [IdTokenValidator] is configured, the ID token is validated before the event is emitted.
     *
     * @param refreshToken the refresh token string.
     * @return [Result.success] with the new [TokenInfo], or [Result.failure] with:
     * - [OAuth2ClientResult.Error.OidcEndpointsNotAvailableException] if endpoints cannot be resolved.
     * - [OAuth2ClientResult.Error.HttpResponseException] if the server returns an error.
     * - [IdTokenValidator.Error] if ID token validation fails.
     * - Other exceptions for network or parsing failures.
     */
    suspend fun refreshToken(refreshToken: String): Result<TokenInfo> =
        runCatching {
            val formParams =
                mapOf(
                    "client_id" to configuration.clientId,
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken
                )
            val tokenInfo = tokenRequest(formParams).getOrThrow()
            _events.tryEmit(TokenRefreshedEvent(tokenInfo))
            tokenInfo
        }

    /**
     * Performs a token endpoint request with the given form parameters.
     *
     * Posts [formParams] to the token endpoint, parses the response into [TokenInfo],
     * and runs full validation via [processTokenResponse] (ID token, access token,
     * device secret validation + event emission).
     *
     * This is the KMP equivalent of the Android `@InternalAuthFoundationApi tokenRequest` method.
     * Flow classes should call this instead of making their own HTTP calls to the token endpoint.
     *
     * @param formParams the form parameters for the token request (e.g., `grant_type`, `client_id`, etc.).
     * @param nonce the nonce sent with the authorization request, if applicable.
     * @param maxAge the max_age sent with the authorization request, if applicable.
     * @return [Result.success] with the validated [TokenInfo], or [Result.failure] with:
     * - [OAuth2ClientResult.Error.OidcEndpointsNotAvailableException] if endpoints cannot be resolved.
     * - [OAuth2ClientResult.Error.HttpResponseException] if the server returns an error.
     * - [IdTokenValidator.Error] if ID token validation fails.
     * - Other exceptions for network or parsing failures.
     */
    @InternalAuthFoundationApi
    suspend fun tokenRequest(
        formParams: Map<String, String>,
        nonce: String? = null,
        maxAge: Int? = null,
    ): Result<TokenInfo> =
        runCatching {
            val endpoints =
                endpointsOrNull()
                    ?: throw OAuth2ClientResult.Error.OidcEndpointsNotAvailableException()

            val result =
                performJsonFormPost(
                    apiExecutor = configuration.apiExecutor,
                    json = configuration.json,
                    url = endpoints.tokenEndpoint,
                    formParams = formParams,
                    deserializer = OAuth2TokenResponse.serializer(),
                    onRateLimitExceeded = { event -> _events.tryEmit(event) }
                )
            when (result) {
                is OAuth2ClientResult.Success -> {
                    val tokenInfo = result.result.toTokenInfo(configuration.clientId, configuration.issuerUrl)
                    processTokenResponse(tokenInfo, nonce, maxAge)
                    tokenInfo
                }

                is OAuth2ClientResult.Error -> {
                    throw result.exception
                }
            }
        }

    /**
     * Attempt to revoke the specified token.
     *
     * On success, a [TokenRevokedEvent] is emitted on [events].
     *
     * @param token the token string to revoke.
     * @return [Result.success] with [Unit] if revoked, or [Result.failure] with:
     * - [OAuth2ClientResult.Error.OidcEndpointsNotAvailableException] if endpoints cannot be resolved.
     * - [OAuth2ClientResult.Error.HttpResponseException] if the server returns an error.
     * - Other exceptions for network failures.
     */
    suspend fun revokeToken(token: String): Result<Unit> =
        runCatching {
            val endpoint =
                endpointsOrNull()?.revocationEndpoint
                    ?: throw OAuth2ClientResult.Error.OidcEndpointsNotAvailableException()

            val formParams =
                mapOf(
                    "client_id" to configuration.clientId,
                    "token" to token
                )

            val result =
                performFormPost(
                    apiExecutor = configuration.apiExecutor,
                    url = endpoint,
                    formParams = formParams,
                    onRateLimitExceeded = { event -> _events.tryEmit(event) }
                )
            when (result) {
                is OAuth2ClientResult.Success -> _events.tryEmit(TokenRevokedEvent(token))
                is OAuth2ClientResult.Error -> throw result.exception
            }
        }

    /**
     * Performs a call to the Authorization Server to validate the specified token
     * per [RFC 7662](https://datatracker.ietf.org/doc/html/rfc7662).
     *
     * @param tokenTypeHint a hint about the type of token (e.g., "access_token", "refresh_token").
     * @param token the token string to introspect.
     * @return [Result.success] with [IntrospectInfo], or [Result.failure] with:
     * - [OAuth2ClientResult.Error.OidcEndpointsNotAvailableException] if endpoints cannot be resolved.
     * - [OAuth2ClientResult.Error.HttpResponseException] if the server returns an error.
     * - Other exceptions for network or parsing failures.
     */
    suspend fun introspectToken(
        tokenTypeHint: String,
        token: String,
    ): Result<IntrospectInfo> =
        runCatching {
            val endpoint =
                endpointsOrNull()?.introspectionEndpoint
                    ?: throw OAuth2ClientResult.Error.OidcEndpointsNotAvailableException()

            val formParams =
                mapOf(
                    "client_id" to configuration.clientId,
                    "token" to token,
                    "token_type_hint" to tokenTypeHint
                )

            val result =
                performJsonFormPost(
                    apiExecutor = configuration.apiExecutor,
                    json = configuration.json,
                    url = endpoint,
                    formParams = formParams,
                    deserializer = JsonObject.serializer(),
                    onRateLimitExceeded = { event -> _events.tryEmit(event) }
                )
            when (result) {
                is OAuth2ClientResult.Success -> IntrospectInfo.fromJsonObject(result.result, configuration.json)
                is OAuth2ClientResult.Error -> throw result.exception
            }
        }

    /**
     * Performs a device authorization request per
     * [RFC 8628](https://datatracker.ietf.org/doc/html/rfc8628).
     *
     * Posts [formParams] to the device authorization endpoint and returns a [DeviceAuthorizationInfo]
     * containing the device code, user code, and verification URIs needed to complete the flow.
     *
     * @param formParams the form parameters (at minimum `client_id` and `scope`).
     * @return [Result.success] with [DeviceAuthorizationInfo], or [Result.failure] with:
     * - [OAuth2ClientResult.Error.OidcEndpointsNotAvailableException] if the device authorization endpoint is not available.
     * - [OAuth2ClientResult.Error.HttpResponseException] if the server returns an error.
     * - Other exceptions for network or parsing failures.
     */
    suspend fun deviceAuthorizationRequest(formParams: Map<String, String>): Result<DeviceAuthorizationInfo> =
        runCatching {
            val endpoint =
                endpointsOrNull()?.deviceAuthorizationEndpoint
                    ?: throw OAuth2ClientResult.Error.OidcEndpointsNotAvailableException()

            val result =
                performJsonFormPost(
                    apiExecutor = configuration.apiExecutor,
                    json = configuration.json,
                    url = endpoint,
                    formParams = formParams,
                    deserializer = SerializableDeviceAuthorizationResponse.serializer(),
                    onRateLimitExceeded = { event -> _events.tryEmit(event) }
                )
            when (result) {
                is OAuth2ClientResult.Success -> result.result.toDeviceAuthorizationInfo()
                is OAuth2ClientResult.Error -> throw result.exception
            }
        }

    /**
     * Performs a call to the Authorization Server to fetch the JSON Web Key Set.
     *
     * @return [Result.success] with the [Jwks], or [Result.failure] with:
     * - [OAuth2ClientResult.Error.OidcEndpointsNotAvailableException] if endpoints cannot be resolved.
     * - [OAuth2ClientResult.Error.HttpResponseException] if the server returns an error.
     * - Other exceptions for network or parsing failures.
     */
    suspend fun jwks(): Result<Jwks> = jwksOrchestrator.get()

    @InternalAuthFoundationApi
    suspend fun endpointsOrNull(): OAuth2Endpoints? =
        when (val result = endpointsOrchestrator.get()) {
            is OAuth2ClientResult.Error -> null
            is OAuth2ClientResult.Success -> result.result
        }

    /**
     * Validates a token response and emits a [TokenCreatedEvent].
     *
     * Called by [tokenRequest] after parsing the HTTP response. Not intended to be
     * called directly — use [tokenRequest] instead.
     *
     * Validation order (when an ID token is present):
     * 1. Parse ID token to [Jwt]
     * 2. Validate ID token claims via [IdTokenValidator]
     * 3. Verify JWT signature against JWKS (retry once on signature failure)
     * 4. Validate access token hash via [AccessTokenValidator]
     * 5. Validate device secret hash via [DeviceSecretValidator] (if present)
     * 6. Emit [TokenCreatedEvent]
     *
     * If no ID token is present, steps 1–5 are skipped and only [TokenCreatedEvent] is emitted.
     *
     * @param tokenInfo the token response to validate.
     * @param nonce the nonce sent with the authorization request, if applicable.
     * @param maxAge the max_age sent with the authorization request, if applicable.
     */
    private suspend fun processTokenResponse(
        tokenInfo: TokenInfo,
        nonce: String? = null,
        maxAge: Int? = null,
    ) {
        val idTokenStr = tokenInfo.idToken
        if (idTokenStr != null) {
            val parser = JwtParser(configuration.json, Dispatchers.Default)
            val jwt = parser.parse(idTokenStr)

            // 1. Validate ID token claims
            val idTokenEvent = ValidateIdTokenEvent()
            _events.tryEmit(idTokenEvent)

            configuration.idTokenValidator.validate(
                issuerUrl = configuration.issuerUrl,
                clientId = configuration.clientId,
                idToken = jwt,
                clock = configuration.clock,
                issuedAtGracePeriodInSeconds = idTokenEvent.issuedAtGracePeriodInSeconds,
                parameters = IdTokenValidator.Parameters(nonce = nonce, maxAge = maxAge)
            )

            // 2. Verify JWT signature against JWKS, retry once on failure
            val jwks = jwks().getOrNull()
            if (jwks != null) {
                if (!jwt.hasValidSignature(jwks)) {
                    // Retry with refreshed JWKS
                    val refreshedJwks = fetchJwks().getOrNull()
                    if (refreshedJwks == null || !jwt.hasValidSignature(refreshedJwks)) {
                        throw IdTokenValidator.Error(
                            "Invalid JWT signature.",
                            IdTokenValidator.Error.INVALID_JWT_SIGNATURE
                        )
                    }
                }
            }

            // 3. Validate access token hash
            _events.tryEmit(ValidateAccessTokenEvent())
            configuration.accessTokenValidator.validate(tokenInfo.accessToken, jwt)

            // 4. Validate device secret hash (if present)
            val deviceSecret = tokenInfo.deviceSecret
            if (deviceSecret != null) {
                configuration.deviceSecretValidator.validate(deviceSecret, jwt)
            }
        }

        // 5. Emit token created event
        _events.tryEmit(TokenCreatedEvent(tokenInfo))
    }

    private suspend fun fetchJwks(): Result<Jwks> =
        runCatching {
            val jwksUri =
                endpointsOrNull()?.jwksUri
                    ?: throw OAuth2ClientResult.Error.OidcEndpointsNotAvailableException()

            val url =
                if (jwksUri.contains("?")) {
                    "$jwksUri&client_id=${configuration.clientId}"
                } else {
                    "$jwksUri?client_id=${configuration.clientId}"
                }

            val result =
                performJsonGetRequest(
                    apiExecutor = configuration.apiExecutor,
                    json = configuration.json,
                    url = url,
                    deserializer = SerializableJwks.serializer(),
                    onRateLimitExceeded = { event -> _events.tryEmit(event) }
                )
            when (result) {
                is OAuth2ClientResult.Success -> result.result.toJwks()
                is OAuth2ClientResult.Error -> throw result.exception
            }
        }

    companion object {
        /**
         * Creates a [OAuth2Client] from an existing [OAuth2ClientConfiguration].
         *
         * This is useful when recreating a client from a stored token's configuration,
         * mirroring the Android `OAuth2Client.createFromConfiguration` pattern.
         */
        fun createFromConfiguration(configuration: OAuth2ClientConfiguration): OAuth2Client {
            val discovery = EndpointDiscovery(configuration)
            return OAuth2Client(
                configuration,
                CoalescingOrchestrator(
                    factory = { discovery.discover() },
                    keepDataInMemory = { it is OAuth2ClientResult.Success }
                )
            )
        }
    }
}
