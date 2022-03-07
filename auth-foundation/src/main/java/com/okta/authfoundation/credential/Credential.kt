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
package com.okta.authfoundation.credential

import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.client.dto.OidcIntrospectInfo
import com.okta.authfoundation.client.dto.OidcUserInfo
import com.okta.authfoundation.credential.events.CredentialStoredAfterRemovedEvent
import com.okta.authfoundation.credential.events.NoAccessTokenAvailableEvent
import com.okta.authfoundation.events.EventCoordinator
import com.okta.authfoundation.jwt.Jwt
import com.okta.authfoundation.jwt.JwtParser
import com.okta.authfoundation.util.CoalescingOrchestrator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.Collections

/**
 * Convenience object that wraps a [Token], providing methods and properties for interacting with credential resources.
 *
 * This class can be used as a convenience mechanism for managing stored credentials, performing operations on or for a user using
 * their credentials, and interacting with resources scoped to the credential.
 */
class Credential internal constructor(
    oidcClient: OidcClient,
    private val storage: TokenStorage,
    private val credentialDataSource: CredentialDataSource,
    internal val storageIdentifier: String,
    @Volatile private var _token: Token? = null,
    @Volatile private var _metadata: Map<String, String> = emptyMap()
) {
    private val refreshCoalescingOrchestrator = CoalescingOrchestrator(
        factory = ::performRealRefresh,
        keepDataInMemory = { false },
    )

    @Volatile private var isDeleted: Boolean = false

    /**
     * The [OidcClient] associated with this [Credential].
     *
     * This is the [OidcClient] that should be used for all other operations related to this [Credential].
     */
    val oidcClient: OidcClient = oidcClient.withCredential(this)

    /**
     * The current [Token] that's stored and associated with this [Credential].
     */
    val token: Token?
        get() {
            return _token
        }

    /**
     * The metadata associated with this [Credential].
     *
     * This can be used when calling [Credential.storeToken] to associate this [Credential] with data relevant to your application.
     */
    val metadata: Map<String, String>
        get() {
            return Collections.unmodifiableMap(_metadata)
        }

    /**
     * Performs the OIDC User Info call, which returns claims associated with this [Credential].
     *
     * Internally, this uses [Credential.getValidAccessToken] to automatically refresh the access token if it's expired.
     */
    suspend fun getUserInfo(): OidcClientResult<OidcUserInfo> {
        val accessToken = getValidAccessToken() ?: return OidcClientResult.Error(IllegalStateException("No Access Token."))
        return oidcClient.getUserInfo(accessToken)
    }

    /**
     * Performs a call to the Authorization Server to validate if the specified [TokenType] is valid.
     *
     * @param tokenType the [TokenType] to check for validity.
     */
    suspend fun introspectToken(tokenType: TokenType): OidcClientResult<OidcIntrospectInfo> {
        val localToken = token ?: return OidcClientResult.Error(IllegalStateException("No token."))
        val token = when (tokenType) {
            TokenType.REFRESH_TOKEN -> {
                localToken.refreshToken ?: return OidcClientResult.Error(IllegalStateException("No refresh token."))
            }
            TokenType.ACCESS_TOKEN -> {
                localToken.accessToken
            }
            TokenType.ID_TOKEN -> {
                localToken.idToken ?: return OidcClientResult.Error(IllegalStateException("No id token."))
            }
            TokenType.DEVICE_SECRET -> {
                localToken.deviceSecret ?: return OidcClientResult.Error(IllegalStateException("No device secret."))
            }
        }

        return oidcClient.introspectToken(tokenType, token)
    }

    /**
     * Store a token, or update the existing token.
     * This can also be used to store custom metadata.
     *
     * @param token the token to update this [Credential] with, defaults to the current [Token].
     * @param metadata the map to associate with this [Credential], defaults to the current metadata.
     */
    suspend fun storeToken(token: Token? = _token, metadata: Map<String, String> = _metadata) {
        if (isDeleted) {
            oidcClient.configuration.eventCoordinator.sendEvent(CredentialStoredAfterRemovedEvent(this))
            return
        }
        val tokenToStore = token?.copy(
            // Device Secret isn't returned when refreshing.
            deviceSecret = token.deviceSecret ?: _token?.deviceSecret,
        )
        val metadataCopy = metadata.toMap() // Making a defensive copy, so it's not modified outside our control.
        storage.replace(
            updatedEntry = TokenStorage.Entry(storageIdentifier, tokenToStore, metadataCopy),
        )
        _token = tokenToStore
        _metadata = metadataCopy
    }

    /**
     * Removes this [Credential] from the associated [CredentialDataSource].
     * Also, sets the [Token] to `null`.
     * This [Credential] should not be used after it's been removed.
     *
     * > Note: OIDC Logout terminology is nuanced, see [Logout Documentation](https://github.com/okta/okta-mobile-kotlin#logout) for additional details.
     */
    suspend fun delete() {
        if (isDeleted) {
            return
        }
        credentialDataSource.remove(this)
        storage.remove(storageIdentifier)
        _token = null
        isDeleted = true
    }

    /**
     * Attempt to refresh the [Token] currently associated with this [Credential].
     */
    suspend fun refreshToken(): OidcClientResult<Token> {
        return refreshCoalescingOrchestrator.get()
    }

    private suspend fun performRealRefresh(): OidcClientResult<Token> {
        val localToken = token ?: return OidcClientResult.Error(IllegalStateException("No Token."))
        val refresh = localToken.refreshToken ?: return OidcClientResult.Error(IllegalStateException("No Refresh Token."))
        val scopes = localToken.scope.split(" ").toSet()
        return oidcClient.refreshToken(refresh, scopes)
    }

    /**
     * Attempt to revoke the specified [TokenType].
     *
     * > Note: OIDC Logout terminology is nuanced, see [Logout Documentation](https://github.com/okta/okta-mobile-kotlin#logout) for additional details.
     *
     * @param tokenType the [TokenType] to revoke, defaults to [RevokeTokenType.ACCESS_TOKEN].
     */
    suspend fun revokeToken(
        tokenType: RevokeTokenType = RevokeTokenType.ACCESS_TOKEN
    ): OidcClientResult<Unit> {
        val localToken = token ?: return OidcClientResult.Error(IllegalStateException("No token."))
        val token = when (tokenType) {
            RevokeTokenType.REFRESH_TOKEN -> {
                localToken.refreshToken ?: return OidcClientResult.Error(IllegalStateException("No refresh token."))
            }
            RevokeTokenType.ACCESS_TOKEN -> {
                localToken.accessToken
            }
            RevokeTokenType.DEVICE_SECRET -> {
                localToken.deviceSecret ?: return OidcClientResult.Error(IllegalStateException("No device secret."))
            }
        }
        return oidcClient.revokeToken(token)
    }

    /**
     * Returns the scopes associated with the current [Token] if present, otherwise the default scopes associated with the [OidcClient].
     */
    fun scopes(): Set<String> {
        return token?.scope?.split(" ")?.toSet() ?: oidcClient.configuration.defaultScopes
    }

    /**
     * Retrieve the [Jwt] associated with the [Token.idToken] field.
     *
     * This will return `null` if the associated [Token] or it's `idToken` field is `null`.
     */
    suspend fun idToken(): Jwt? {
        val idToken = _token?.idToken ?: return null
        try {
            val parser = JwtParser(oidcClient.configuration.json, oidcClient.configuration.computeDispatcher)
            return parser.parse(idToken)
        } catch (e: Exception) {
            // The ID token was malformed.
            return null
        }
    }

    /**
     * Checks to see if the current access token is valid, and if it is, returns it.
     * If there is no [Token] associated with the [Credential], null is returned.
     *
     * Access tokens are valid if they haven't expired.
     *
     * See [Credential.introspectToken] for checking if the token is valid with the Authorization Server.
     */
    suspend fun getAccessTokenIfValid(): String? {
        val accessToken = _token?.accessToken ?: return null
        try {
            val parser = JwtParser(oidcClient.configuration.json, oidcClient.configuration.computeDispatcher)
            val jwt = parser.parse(accessToken)
            val payload = jwt.payload(AccessTokenExpirationPayload.serializer())
            if (payload.exp > oidcClient.configuration.clock.currentTimeMillis()) {
                return accessToken
            }
        } catch (e: Exception) {
            // Failed to parse access token JWT.
        }
        return null
    }

    /**
     * Returns a valid access token if one is present.
     * If the access token is invalid, and there is a refresh token, a [Token] refresh is attempted via [Credential.refreshToken].
     * If the refresh results in a valid access token, it is returned.
     *
     * See [Credential.getAccessTokenIfValid] for what makes an access token valid.
     */
    suspend fun getValidAccessToken(): String? {
        getAccessTokenIfValid()?.let { return it }

        if (refreshToken() is OidcClientResult.Success) {
            return getAccessTokenIfValid()
        } else {
            return null
        }
    }

    /**
     * Returns an [Interceptor] that can be can be added to an [OkHttpClient] via [OkHttpClient.Builder.addInterceptor] to access a
     * Resource Server.
     *
     * The [Interceptor] attaches an authorization: Bearer header with the access token to all requests.
     * Internally, this uses [Credential.getValidAccessToken] to automatically refresh the access token once it expires.
     * If no valid access token is available, no authorization header will be added, and a [NoAccessTokenAvailableEvent] event will
     * be fired to the associated [EventCoordinator].
     */
    fun accessTokenInterceptor(): Interceptor {
        return AccessTokenInterceptor(::getValidAccessToken, oidcClient.configuration.eventCoordinator, this)
    }
}

@Serializable
private data class AccessTokenExpirationPayload(
    @SerialName("exp") val exp: Long,
)
