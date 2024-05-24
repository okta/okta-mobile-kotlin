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
import com.okta.authfoundation.credential.RevokeTokenType
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
import okhttp3.Request
import java.util.UUID

@Deprecated(
    message = "Renamed to OAuth2Client",
    replaceWith = ReplaceWith("OAuth2Client")
)
typealias OidcClient = OAuth2Client

/**
 * The client used for interacting with an Okta Authorization Server.
 *
 * This is for low level access, and it's typically preferred to use a [Credential], which streamlines usage.
 *
 * [Okta Developer Docs](https://developer.okta.com/docs/reference/api/oidc)
 */
class OAuth2Client private constructor(
    @property:InternalAuthFoundationApi val configuration: OidcConfiguration,
    internal val endpoints: CoalescingOrchestrator<OAuth2ClientResult<OidcEndpoints>>,
    jwks: CoalescingOrchestrator<OAuth2ClientResult<Jwks>>? = null
) {
    companion object {
        /**
         * Create an [OAuth2Client], using the discovery url to create the [OidcEndpoints].
         *
         * @param configuration the [OidcConfiguration] detailing the settings to be used when communicating with the Authorization
         *  server, as well as with the rest of the SDK.
         */
        fun createFromConfiguration(
            configuration: OidcConfiguration
        ): OAuth2Client {
            return OAuth2Client(
                configuration = configuration,
                endpoints = CoalescingOrchestrator(
                    factory = {
                        EndpointsFactory.get(configuration)
                    },
                    keepDataInMemory = { result ->
                        result is OAuth2ClientResult.Success
                    }
                ),
            )
        }

        @InternalAuthFoundationApi
        fun create(configuration: OidcConfiguration, endpoints: OidcEndpoints): OAuth2Client {
            return OAuth2Client(
                configuration = configuration,
                endpoints = CoalescingOrchestrator(
                    factory = { OAuth2ClientResult.Success(endpoints) },
                    keepDataInMemory = { true },
                ),
            )
        }

        val default: OAuth2Client by lazy { createFromConfiguration(OidcConfiguration.default) }
    }

    private val jwks: CoalescingOrchestrator<OAuth2ClientResult<Jwks>> = jwks ?: jwksCoalescingOrchestrator()

    /**
     * Performs the OIDC User Info call, which returns claims associated with the supplied `accessToken`.
     *
     * @param accessToken the access token used for authorization to the Authorization Server
     */
    suspend fun getUserInfo(accessToken: String): OAuth2ClientResult<OidcUserInfo> {
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
     * @param token the [Token] to refresh.
     */
    suspend fun refreshToken(
        token: Token,
    ): OAuth2ClientResult<Token> {
        val endpoints = endpointsOrNull() ?: return endpointNotAvailableError()

        val refreshToken = token.getTokenOfType(TokenType.REFRESH_TOKEN).getOrElse { throwable ->
            return OAuth2ClientResult.Error(throwable as Exception)
        }

        val formBody = FormBody.Builder()
            .add("client_id", configuration.clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .build()

        val request = Request.Builder()
            .url(endpoints.tokenEndpoint)
            .post(formBody)
            .build()

        return tokenRequest(request, requestToken = token)
    }

    /**
     * Attempt to revoke the specified token.
     *
     * > Note: OIDC Logout terminology is nuanced, see [Logout Documentation](https://github.com/okta/okta-mobile-kotlin#logout) for additional details.
     *
     * @param revokeTokenType the [RevokeTokenType] to revoke.
     * @param token the token to attempt to revoke.
     */
    suspend fun revokeToken(revokeTokenType: RevokeTokenType, token: Token): OAuth2ClientResult<Unit> {
        val endpoint = endpointsOrNull()?.revocationEndpoint ?: return endpointNotAvailableError()

        val tokenString = token.getTokenOfType(revokeTokenType.toTokenType()).getOrElse { throwable ->
            return OAuth2ClientResult.Error(throwable as Exception)
        }

        val formBody = FormBody.Builder()
            .add("client_id", configuration.clientId)
            .add("token", tokenString)
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
        token: Token,
    ): OAuth2ClientResult<OidcIntrospectInfo> {
        val introspectEndpoint = endpointsOrNull()?.introspectionEndpoint ?: return endpointNotAvailableError()

        val tokenString = token.getTokenOfType(tokenType).getOrElse { throwable ->
            return OAuth2ClientResult.Error(throwable as Exception)
        }

        val formBody = FormBody.Builder()
            .add("client_id", configuration.clientId)
            .add("token", tokenString)
            .add("token_type_hint", tokenType.toTokenTypeHint())
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
    suspend fun jwks(): OAuth2ClientResult<Jwks> {
        return jwks.get()
    }

    private fun jwksCoalescingOrchestrator(): CoalescingOrchestrator<OAuth2ClientResult<Jwks>> {
        return CoalescingOrchestrator(
            factory = {
                actualJwks()
            },
            keepDataInMemory = { result ->
                result is OAuth2ClientResult.Success
            },
        )
    }

    private suspend fun actualJwks(): OAuth2ClientResult<Jwks> {
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
            is OAuth2ClientResult.Error -> {
                null
            }
            is OAuth2ClientResult.Success -> {
                result.result
            }
        }
    }

    @InternalAuthFoundationApi
    fun <T> endpointNotAvailableError(): OAuth2ClientResult.Error<T> {
        return OAuth2ClientResult.Error(OAuth2ClientResult.Error.OidcEndpointsNotAvailableException())
    }

    @InternalAuthFoundationApi
    suspend fun tokenRequest(
        request: Request,
        nonce: String? = null,
        maxAge: Int? = null,
        requestToken: Token? = null
    ): OAuth2ClientResult<Token> {
        return coroutineScope {
            val isRefreshRequest = requestToken != null
            val tokenId = requestToken?.id ?: UUID.randomUUID().toString()
            val tokenDeferred = async {
                performRequest(SerializableToken.serializer(), request) { serializableToken ->
                    serializableToken.asToken(id = tokenId, oidcConfiguration = configuration)
                }
            }
            val jwksDeferred = async {
                endpointsOrNull()?.jwksUri ?: return@async null
                jwks()
            }
            val tokenResult = tokenDeferred.await()
            if (tokenResult is OAuth2ClientResult.Success) {
                val token = tokenResult.result

                try {
                    TokenValidator(this@OAuth2Client, token, nonce, maxAge, jwksDeferred.await()).validate()
                    configuration.eventCoordinator.sendEvent(TokenCreatedEvent(token))
                    if (isRefreshRequest) {
                        Credential.credentialDataSource().replaceToken(token)
                    }
                } catch (e: Exception) {
                    return@coroutineScope OAuth2ClientResult.Error(e)
                }
            }
            return@coroutineScope tokenResult
        }
    }

    private fun Token.getTokenOfType(tokenType: TokenType): Result<String> {
        return when (tokenType) {
            TokenType.ACCESS_TOKEN -> {
                Result.success(accessToken)
            }
            TokenType.REFRESH_TOKEN -> {
                refreshToken?.let {
                    Result.success(it)
                } ?: Result.failure(IllegalStateException("No refresh token."))
            }
            TokenType.ID_TOKEN -> {
                idToken?.let {
                    Result.success(it)
                } ?: Result.failure(IllegalStateException("No id token."))
            }
            TokenType.DEVICE_SECRET -> {
                deviceSecret?.let {
                    Result.success(it)
                } ?: Result.failure(IllegalStateException("No device secret."))
            }
        }
    }
}
