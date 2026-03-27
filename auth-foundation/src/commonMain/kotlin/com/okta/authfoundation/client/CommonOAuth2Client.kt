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
import com.okta.authfoundation.client.dto.OidcUserInfo
import com.okta.authfoundation.client.internal.OAuth2Endpoints
import com.okta.authfoundation.client.internal.OAuth2TokenResponse
import com.okta.authfoundation.client.internal.performFormPost
import com.okta.authfoundation.client.internal.performJsonFormPost
import com.okta.authfoundation.client.internal.performJsonGetRequest
import com.okta.authfoundation.jwt.Jwks
import com.okta.authfoundation.jwt.SerializableJwks
import com.okta.authfoundation.util.CoalescingOrchestrator
import kotlinx.serialization.json.JsonObject

/**
 * A cross-platform OAuth2 client for interacting with an Okta Authorization Server.
 *
 * Use [OAuth2ClientBuilder] to create an instance via the builder pattern.
 *
 * On Android, the existing [OAuth2Client] class provides backward-compatible access
 * with additional platform-specific features.
 */
@OptIn(InternalAuthFoundationApi::class)
class CommonOAuth2Client internal constructor(
    /** The configuration for this client. */
    val configuration: OAuth2ClientConfiguration,
    internal val endpointsOrchestrator: CoalescingOrchestrator<OAuth2ClientResult<OAuth2Endpoints>>,
) {
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
     * @param refreshToken the refresh token string.
     * @return the new token response as a [TokenInfo].
     */
    suspend fun refreshToken(refreshToken: String): OAuth2ClientResult<TokenInfo> {
        val endpoints = endpointsOrNull() ?: return endpointNotAvailableError()

        val formParams =
            mapOf(
                "client_id" to configuration.clientId,
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken
            )

        return performJsonFormPost(
            apiExecutor = configuration.apiExecutor,
            json = configuration.json,
            url = endpoints.tokenEndpoint,
            formParams = formParams,
            deserializer = OAuth2TokenResponse.serializer()
        ).mapSuccess { response ->
            response.toTokenInfo(configuration.clientId, configuration.issuerUrl)
        }
    }

    /**
     * Attempt to revoke the specified token.
     *
     * @param token the token string to revoke.
     */
    suspend fun revokeToken(token: String): OAuth2ClientResult<Unit> {
        val endpoint = endpointsOrNull()?.revocationEndpoint ?: return endpointNotAvailableError()

        val formParams =
            mapOf(
                "client_id" to configuration.clientId,
                "token" to token
            )

        return performFormPost(
            apiExecutor = configuration.apiExecutor,
            url = endpoint,
            formParams = formParams
        )
    }

    /**
     * Performs a call to the Authorization Server to validate the specified token.
     *
     * @param tokenTypeHint a hint about the type of token (e.g., "access_token", "refresh_token").
     * @param token the token string to introspect.
     *
     * TODO parse and return the IntrospectInfo object instead of raw JsonObject.
     * https://datatracker.ietf.org/doc/html/rfc7662
     */
    suspend fun introspectToken(
        tokenTypeHint: String,
        token: String,
    ): OAuth2ClientResult<JsonObject> {
        val endpoint = endpointsOrNull()?.introspectionEndpoint ?: return endpointNotAvailableError()

        val formParams =
            mapOf(
                "client_id" to configuration.clientId,
                "token" to token,
                "token_type_hint" to tokenTypeHint
            )

        return performJsonFormPost(
            apiExecutor = configuration.apiExecutor,
            json = configuration.json,
            url = endpoint,
            formParams = formParams,
            deserializer = JsonObject.serializer()
        )
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
}
