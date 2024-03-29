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
import com.okta.authfoundation.claims.DefaultClaimsProvider.Companion.createClaimsDeserializer
import com.okta.authfoundation.claims.subject
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
import com.okta.authfoundation.jwt.Jwks
import com.okta.authfoundation.jwt.SerializableJwks
import com.okta.authfoundation.util.CoalescingOrchestrator
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    jwks: CoalescingOrchestrator<OidcClientResult<Jwks>>? = null,
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
                        EndpointsFactory.get(configuration, discoveryUrl)
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

    private val jwks: CoalescingOrchestrator<OidcClientResult<Jwks>> = jwks ?: jwksCoalescingOrchestrator()

    internal fun withCredential(credential: Credential): OidcClient {
        return OidcClient(configuration, endpoints, jwks, credential)
    }

    /**
     * Performs the OIDC User Info call, which returns claims associated with the supplied `accessToken`.
     *
     * @param accessToken the access token used for authorization to the Authorization Server
     */
    suspend fun getUserInfo(accessToken: String): OidcClientResult<OidcUserInfo> {
        val endpoint = endpointsOrNull()?.userInfoEndpoint ?: return endpointNotAvailableError()

        val request = Request.Builder()
            .addHeader("authorization", "Bearer $accessToken")
            .url(endpoint)
            .build()

        return performRequest(JsonObject.serializer(), request) {
            val claimsProvider = configuration.createClaimsDeserializer(it)
            if (claimsProvider.subject.isNullOrBlank()) {
                throw IllegalArgumentException("The user info endpoint must return a valid sub claim.")
            }
            OidcUserInfo(claimsProvider)
        }
    }

    /**
     * Attempt to refresh the token.
     *
     * @param refreshToken the refresh token for which to mint a new [Token] for.
     */
    suspend fun refreshToken(
        refreshToken: String,
    ): OidcClientResult<Token> {
        val endpoints = endpointsOrNull() ?: return endpointNotAvailableError()

        val formBody = FormBody.Builder()
            .add("client_id", configuration.clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
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
        val endpoint = endpointsOrNull()?.revocationEndpoint ?: return endpointNotAvailableError()

        val formBody = FormBody.Builder()
            .add("client_id", configuration.clientId)
            .add("token", token)
            .build()

        val request = Request.Builder()
            .url(endpoint)
            .post(formBody)
            .build()

        return performRequestNonJson(request)
    }

    /**
     * Performs a call to the Authorization Server to validate the specified [TokenType].
     *
     * @param tokenType the [TokenType] to check for validity.
     * @param token the token associated with the [TokenType] to check for validity.
     */
    suspend fun introspectToken(
        tokenType: TokenType,
        token: String,
    ): OidcClientResult<OidcIntrospectInfo> {
        val introspectEndpoint = endpointsOrNull()?.introspectionEndpoint ?: return endpointNotAvailableError()

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
            .url(introspectEndpoint)
            .post(formBody)
            .build()

        return performRequest(JsonObject.serializer(), request) { response ->
            response.asOidcIntrospectInfo(configuration)
        }
    }

    /**
     * Performs a call to the Authorization Server to fetch [Jwks].
     */
    suspend fun jwks(): OidcClientResult<Jwks> {
        return jwks.get()
    }

    private fun jwksCoalescingOrchestrator(): CoalescingOrchestrator<OidcClientResult<Jwks>> {
        return CoalescingOrchestrator(
            factory = {
                actualJwks()
            },
            keepDataInMemory = { result ->
                result is OidcClientResult.Success
            },
        )
    }

    private suspend fun actualJwks(): OidcClientResult<Jwks> {
        val endpoint = endpointsOrNull()?.jwksUri ?: return endpointNotAvailableError()

        val url = endpoint.newBuilder()
            .addQueryParameter("client_id", configuration.clientId)
            .build()

        val request = Request.Builder()
            .url(url)
            .build()

        return performRequest(SerializableJwks.serializer(), request) { serializableJwks ->
            serializableJwks.toJwks()
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
        maxAge: Int? = null
    ): OidcClientResult<Token> {
        return coroutineScope {
            val tokenDeferred = async {
                performRequest(SerializableToken.serializer(), request) { serializableToken ->
                    serializableToken.asToken()
                }
            }
            val jwksDeferred = async {
                endpointsOrNull()?.jwksUri ?: return@async null
                jwks()
            }
            val tokenResult = tokenDeferred.await()
            if (tokenResult is OidcClientResult.Success) {
                val token = tokenResult.result

                try {
                    TokenValidator(this@OidcClient, token, nonce, maxAge, jwksDeferred.await()).validate()
                    configuration.eventCoordinator.sendEvent(TokenCreatedEvent(token, credential))
                    credential?.storeToken(token)
                } catch (e: Exception) {
                    return@coroutineScope OidcClientResult.Error(e)
                }
            }
            return@coroutineScope tokenResult
        }
    }
}
