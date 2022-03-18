/*
 * Copyright 2021-Present Okta, Inc.
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
import com.okta.authfoundation.client.dto.OidcIntrospectInfo
import com.okta.authfoundation.client.dto.OidcIntrospectInfo.Companion.asOidcIntrospectInfo
import com.okta.authfoundation.client.dto.OidcUserInfo
import com.okta.authfoundation.client.events.TokenCreatedEvent
import com.okta.authfoundation.client.internal.performRequest
import com.okta.authfoundation.client.internal.performRequestNonJson
import com.okta.authfoundation.credential.Credential
import com.okta.authfoundation.credential.SerializableToken
import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.credential.TokenType
import com.okta.authfoundation.util.CoalescingOrchestrator
import com.okta.authfoundation.claims.DefaultClaimsProvider.Companion.createClaimsDeserializer
import com.okta.authfoundation.jwt.Jwt
import com.okta.authfoundation.jwt.JwtParser
import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request

/**
 * The client used for interacting with an Okta Authorization Server.
 *
 * This is for low level access, and it's typically preferred to use a [Credential], which streamlines usage.
 *
 * [Okta Developer Docs](https://developer.okta.com/docs/reference/api/oidc)
 */
class OidcClient private constructor(
    @property:InternalAuthFoundationApi val configuration: OidcConfiguration,
    internal val endpoints: CoalescingOrchestrator<OidcClientResult<OidcEndpoints>>,
    internal val credential: Credential? = null,
) {
    companion object {
        /**
         * Create an [OidcClient], using the discovery url to create the [OidcEndpoints].
         *
         * @param configuration the [OidcConfiguration] detailing the settings to be used when communicating with the Authorization
         *  server, as well as with the rest of the SDK.
         * @param discoveryUrl the `.well-known/openid-configuration` endpoint associated with the Authorization Server. This is
         *  used to fetch the [OidcEndpoints].
         */
        fun createFromDiscoveryUrl(
            configuration: OidcConfiguration,
            discoveryUrl: HttpUrl
        ): OidcClient {
            return OidcClient(
                configuration = configuration,
                endpoints = CoalescingOrchestrator(
                    factory = {
                        val request = Request.Builder()
                            .url(discoveryUrl)
                            .build()
                        configuration.performRequest(SerializableOidcEndpoints.serializer(), request) { serializableOidcEndpoints ->
                            serializableOidcEndpoints.asOidcEndpoints()
                        }
                    },
                    keepDataInMemory = { result ->
                        result is OidcClientResult.Success
                    }
                ),
            )
        }

        fun create(configuration: OidcConfiguration, endpoints: OidcEndpoints): OidcClient {
            return OidcClient(
                configuration = configuration,
                endpoints = CoalescingOrchestrator(
                    factory = { OidcClientResult.Success(endpoints) },
                    keepDataInMemory = { true },
                ),
            )
        }
    }

    internal fun withCredential(credential: Credential): OidcClient {
        return OidcClient(configuration, endpoints, credential)
    }

    /**
     * Performs the OIDC User Info call, which returns claims associated with supplied `accessToken`.
     *
     * @param accessToken the access token used for authorization to the Authorization Server
     */
    suspend fun getUserInfo(accessToken: String): OidcClientResult<OidcUserInfo> {
        val endpoints = endpointsOrNull() ?: return endpointNotAvailableError()

        val request = Request.Builder()
            .addHeader("authorization", "Bearer $accessToken")
            .url(endpoints.userInfoEndpoint)
            .build()

        return configuration.performRequest(JsonObject.serializer(), request) {
            OidcUserInfo(configuration.createClaimsDeserializer(it))
        }
    }

    /**
     * Attempt to refresh the token.
     *
     * @param refreshToken the refresh token for which to mint a new [Token] for.
     * @param scopes the scopes to request from the Authorization Server, defaults to the configured [OidcConfiguration.defaultScopes].
     */
    suspend fun refreshToken(
        refreshToken: String,
        scopes: Set<String> = configuration.defaultScopes,
    ): OidcClientResult<Token> {
        val endpoints = endpointsOrNull() ?: return endpointNotAvailableError()

        val formBody = FormBody.Builder()
            .add("client_id", configuration.clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("scope", scopes.joinToString(separator = " "))
            .build()

        val request = Request.Builder()
            .url(endpoints.tokenEndpoint)
            .post(formBody)
            .build()

        return tokenRequest(request)
    }

    /**
     * Attempt to revoke the specified token.
     *
     * > Note: OIDC Logout terminology is nuanced, see [Logout Documentation](https://github.com/okta/okta-mobile-kotlin#logout) for additional details.
     *
     * @param token the token to attempt to revoke.
     */
    suspend fun revokeToken(token: String): OidcClientResult<Unit> {
        val endpoints = endpointsOrNull() ?: return endpointNotAvailableError()

        val formBody = FormBody.Builder()
            .add("client_id", configuration.clientId)
            .add("token", token)
            .build()

        val request = Request.Builder()
            .url(endpoints.revocationEndpoint)
            .post(formBody)
            .build()

        return configuration.performRequestNonJson(request)
    }

    /**
     * Performs a call to the Authorization Server to validate if the specified [TokenType] is valid.
     *
     * @param tokenType the [TokenType] to check for validity.
     * @param token the token associated with the [TokenType] to check for validity.
     */
    suspend fun introspectToken(
        tokenType: TokenType,
        token: String,
    ): OidcClientResult<OidcIntrospectInfo> {
        val endpoints = endpointsOrNull() ?: return endpointNotAvailableError()

        val tokenTypeHint: String = when (tokenType) {
            TokenType.ACCESS_TOKEN -> {
                "access_token"
            }
            TokenType.REFRESH_TOKEN -> {
                "refresh_token"
            }
            TokenType.ID_TOKEN -> {
                "id_token"
            }
            TokenType.DEVICE_SECRET -> {
                "device_secret"
            }
        }

        val formBody = FormBody.Builder()
            .add("client_id", configuration.clientId)
            .add("token", token)
            .add("token_type_hint", tokenTypeHint)
            .build()

        val request = Request.Builder()
            .url(endpoints.introspectionEndpoint)
            .post(formBody)
            .build()

        return configuration.performRequest(JsonObject.serializer(), request) { response ->
            response.asOidcIntrospectInfo(configuration)
        }
    }

    /**
     * Parses a given string into a [Jwt], if possible.
     *
     * Returns `null` if an error occurs attempted to parse the [Jwt].
     *
     * @param jwt the string representation of a [Jwt].
     */
    suspend fun parseJwt(jwt: String): Jwt? {
        try {
            val parser = JwtParser(configuration.json, configuration.computeDispatcher)
            return parser.parse(jwt)
        } catch (e: Exception) {
            // The token was malformed.
            return null
        }
    }

    @InternalAuthFoundationApi
    suspend fun endpointsOrNull(): OidcEndpoints? {
        return when (val result = endpoints.get()) {
            is OidcClientResult.Error -> {
                null
            }
            is OidcClientResult.Success -> {
                result.result
            }
        }
    }

    @InternalAuthFoundationApi
    fun <T> endpointNotAvailableError(): OidcClientResult.Error<T> {
        return OidcClientResult.Error(OidcClientResult.Error.OidcEndpointsNotAvailableException())
    }

    @InternalAuthFoundationApi
    suspend fun tokenRequest(
        request: Request,
        nonce: String? = null,
    ): OidcClientResult<Token> {
        val result = configuration.performRequest(SerializableToken.serializer(), request) { serializableToken ->
            serializableToken.asToken()
        }
        if (result is OidcClientResult.Success) {
            val token = result.result

            try {
                TokenValidator(this, token, nonce).validate()
            } catch (e: Exception) {
                return OidcClientResult.Error(e)
            }

            configuration.eventCoordinator.sendEvent(TokenCreatedEvent(token, credential))

            credential?.storeToken(token)
        }
        return result
    }
}
