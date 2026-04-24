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
import com.okta.authfoundation.client.dto.IntrospectInfo
import com.okta.authfoundation.client.dto.OidcUserInfo
import com.okta.authfoundation.client.events.TokenRefreshedEvent
import com.okta.authfoundation.client.events.TokenRevokedEvent
import com.okta.authfoundation.client.internal.EndpointDiscovery
import com.okta.authfoundation.client.internal.OAuth2Endpoints
import com.okta.authfoundation.client.internal.OAuth2TokenResponse
import com.okta.authfoundation.client.internal.performFormPost
import com.okta.authfoundation.client.internal.performJsonFormPost
import com.okta.authfoundation.client.internal.performJsonGetRequest
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

    private val jwksOrchestrator: CoalescingOrchestrator<OAuth2ClientResult<Jwks>> =
        CoalescingOrchestrator(
            factory = ::fetchJwks,
            keepDataInMemory = { it is OAuth2ClientResult.Success }
        )

    /**
     * Performs the OIDC User Info call.
     *
     * @param accessToken the access token used for authorization.
     */
    suspend fun getUserInfo(accessToken: String): OAuth2ClientResult<OidcUserInfo> {
        val endpoint = endpointsOrNull()?.userInfoEndpoint ?: return endpointNotAvailableError()

        return performJsonGetRequest(
            apiExecutor = configuration.apiExecutor,
            json = configuration.json,
            url = endpoint,
            deserializer = JsonObject.serializer(),
            headers =
                mapOf(
                    "Accept" to listOf("application/json"),
                    "Authorization" to listOf("Bearer $accessToken")
                )
        ).mapSuccess { claims ->
            val claimsProvider =
                com.okta.authfoundation.claims
                    .DefaultClaimsProvider(claims, configuration.json)
            OidcUserInfo(claimsProvider)
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
            val endpoints =
                endpointsOrNull()
                    ?: throw OAuth2ClientResult.Error.OidcEndpointsNotAvailableException()

            val formParams =
                mapOf(
                    "client_id" to configuration.clientId,
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken
                )

            val result =
                performJsonFormPost(
                    apiExecutor = configuration.apiExecutor,
                    json = configuration.json,
                    url = endpoints.tokenEndpoint,
                    formParams = formParams,
                    deserializer = OAuth2TokenResponse.serializer()
                )
            when (result) {
                is OAuth2ClientResult.Success -> {
                    val tokenInfo = result.result.toTokenInfo(configuration.clientId, configuration.issuerUrl)
                    _events.tryEmit(TokenRefreshedEvent(tokenInfo))
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
                    formParams = formParams
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
                    deserializer = JsonObject.serializer()
                )
            when (result) {
                is OAuth2ClientResult.Success -> IntrospectInfo.fromJsonObject(result.result, configuration.json)
                is OAuth2ClientResult.Error -> throw result.exception
            }
        }

    /**
     * Performs a call to the Authorization Server to fetch the JSON Web Key Set.
     */
    suspend fun jwks(): OAuth2ClientResult<Jwks> = jwksOrchestrator.get()

    @InternalAuthFoundationApi
    suspend fun endpointsOrNull(): OAuth2Endpoints? =
        when (val result = endpointsOrchestrator.get()) {
            is OAuth2ClientResult.Error -> null
            is OAuth2ClientResult.Success -> result.result
        }

    /**
     * Validates the ID token in [tokenInfo] using the configured [IdTokenValidator].
     *
     * Verifies claims first, then JWT signature against JWKS.
     */
    private suspend fun validateIdToken(tokenInfo: TokenInfo) {
        val idTokenStr = tokenInfo.idToken ?: return
        val validator = configuration.idTokenValidator

        val parser = JwtParser(configuration.json, Dispatchers.Default)
        val jwt = parser.parse(idTokenStr)

        val event = ValidateIdTokenEvent()
        _events.tryEmit(event)

        validator.validate(
            issuerUrl = configuration.issuerUrl,
            clientId = configuration.clientId,
            idToken = jwt,
            clock = configuration.clock,
            issuedAtGracePeriodInSeconds = event.issuedAtGracePeriodInSeconds
        )

        // Verify signature against JWKS
        val jwksResult = jwks()
        if (jwksResult is OAuth2ClientResult.Success) {
            if (!jwt.hasValidSignature(jwksResult.result)) {
                throw IdTokenValidator.Error(
                    "Invalid JWT signature.",
                    IdTokenValidator.Error.INVALID_JWT_SIGNATURE
                )
            }
        }
    }

    private fun <T> endpointNotAvailableError(): OAuth2ClientResult.Error<T> = OAuth2ClientResult.Error(OAuth2ClientResult.Error.OidcEndpointsNotAvailableException())

    private suspend fun fetchJwks(): OAuth2ClientResult<Jwks> {
        val jwksUri = endpointsOrNull()?.jwksUri ?: return endpointNotAvailableError()

        val url =
            if (jwksUri.contains("?")) {
                "$jwksUri&client_id=${configuration.clientId}"
            } else {
                "$jwksUri?client_id=${configuration.clientId}"
            }

        return performJsonGetRequest(
            apiExecutor = configuration.apiExecutor,
            json = configuration.json,
            url = url,
            deserializer = SerializableJwks.serializer()
        ).mapSuccess { it.toJwks() }
    }

    private fun <T, R> OAuth2ClientResult<T>.mapSuccess(transform: (T) -> R): OAuth2ClientResult<R> =
        when (this) {
            is OAuth2ClientResult.Success -> OAuth2ClientResult.Success(transform(result))
            is OAuth2ClientResult.Error -> OAuth2ClientResult.Error(exception)
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
