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

import androidx.annotation.VisibleForTesting
import androidx.biometric.BiometricPrompt
import com.okta.authfoundation.AuthFoundationDefaults
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.client.dto.OidcIntrospectInfo
import com.okta.authfoundation.client.dto.OidcUserInfo
import com.okta.authfoundation.credential.events.CredentialDeletedEvent
import com.okta.authfoundation.credential.events.CredentialStoredAfterRemovedEvent
import com.okta.authfoundation.credential.events.CredentialStoredEvent
import com.okta.authfoundation.credential.events.NoAccessTokenAvailableEvent
import com.okta.authfoundation.events.EventCoordinator
import com.okta.authfoundation.jwt.Jwt
import com.okta.authfoundation.jwt.JwtParser
import com.okta.authfoundation.util.CoalescingOrchestrator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.Collections
import java.util.Objects

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
    token: Token? = null,
    tags: Map<String, String> = emptyMap()
) {
    internal interface BiometricSecurity

    sealed interface Security {
        val keyAlias: String

        data class Default(
            override val keyAlias: String = AuthFoundationDefaults.Encryption.keyAlias
        ) : Security

        data class BiometricStrong(
            override val keyAlias: String = AuthFoundationDefaults.Encryption.keyAlias + ".biometricStrong"
        ) : Security, BiometricSecurity

        data class BiometricStrongOrDeviceCredential(
            override val keyAlias: String = AuthFoundationDefaults.Encryption.keyAlias + ".biometricStrongOrDeviceCredential"
        ) : Security, BiometricSecurity

        companion object {
            private var _standard: Security? = null

            var standard: Security
                get() = _standard ?: Default()
                set(value) { _standard = value }

            var promptInfo: BiometricPrompt.PromptInfo? = null
        }
    }

    private val refreshCoalescingOrchestrator = CoalescingOrchestrator(
        factory = ::performRealRefresh,
        keepDataInMemory = { false },
    )

    private val state = MutableStateFlow<CredentialState>(CredentialState.Data(token, tags))

    private val isDeleted: Boolean
        get() {
            return state.value == CredentialState.Deleted
        }

    /**
     * The [OidcClient] associated with this [Credential].
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val oidcClient: OidcClient = oidcClient.withCredential(this)

    /**
     * The current [Token] that's stored and associated with this [Credential].
     */
    val token: Token?
        get() {
            return state.value.token
        }

    /**
     * The tags associated with this [Credential].
     *
     * This can be used when calling [Credential.storeToken] to associate this [Credential] with data relevant to your application.
     */
    val tags: Map<String, String>
        get() {
            return Collections.unmodifiableMap(state.value.tags)
        }

    /**
     * Returns a [Flow] that emits the current [Token] that's stored and associated with this [Credential].
     */
    fun getTokenFlow(): Flow<Token?> {
        return state
            .transformWhile {
                emit(it)
                it !is CredentialState.Deleted
            }
            .dropWhile { it !is CredentialState.Data }
            .map { it.token }
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
     * Performs a call to the Authorization Server to validate the specified [TokenType].
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
     * This can also be used to store custom tags.
     *
     * @param token the token to update this [Credential] with, defaults to the current [Token].
     * @param tags the map to associate with this [Credential], defaults to the current tags.
     */
    suspend fun storeToken(token: Token? = this.token, tags: Map<String, String> = this.tags) {
        if (isDeleted) {
            oidcClient.configuration.eventCoordinator.sendEvent(CredentialStoredAfterRemovedEvent(this))
            return
        }
        val tokenToStore = token?.copy(
            // Refresh Token isn't ALWAYS returned when refreshing.
            refreshToken = token.refreshToken ?: this.token?.refreshToken,
            // Device Secret isn't returned when refreshing.
            deviceSecret = token.deviceSecret ?: this.token?.deviceSecret,
        )
        val tagsCopy = tags.toMap() // Making a defensive copy, so it's not modified outside our control.
        storage.replace(
            updatedEntry = TokenStorage.Entry(storageIdentifier, tokenToStore, tagsCopy),
        )
        state.value = CredentialState.Data(tokenToStore, tagsCopy)
        oidcClient.configuration.eventCoordinator.sendEvent(CredentialStoredEvent(this, this.token, this.tags))
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
        state.value = CredentialState.Deleted
        oidcClient.configuration.eventCoordinator.sendEvent(CredentialDeletedEvent(this))
    }

    /**
     * Attempt to refresh the [Token] associated with this [Credential].
     */
    suspend fun refreshToken(): OidcClientResult<Token> {
        return refreshCoalescingOrchestrator.get()
    }

    private suspend fun performRealRefresh(): OidcClientResult<Token> {
        val localToken = token ?: return OidcClientResult.Error(IllegalStateException("No Token."))
        val refresh = localToken.refreshToken ?: return OidcClientResult.Error(IllegalStateException("No Refresh Token."))
        return oidcClient.refreshToken(refresh)
    }

    /**
     * Attempt to revoke the specified [TokenType].
     *
     * > Note: OIDC Logout terminology is nuanced, see [Logout Documentation](https://github.com/okta/okta-mobile-kotlin#logout) for additional details.
     *
     * @param tokenType the [TokenType] to revoke.
     */
    suspend fun revokeToken(
        tokenType: RevokeTokenType
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
     * Attempt to revoke all available tokens.
     *
     * > Note: OIDC Logout terminology is nuanced, see [Logout Documentation](https://github.com/okta/okta-mobile-kotlin#logout) for additional details.
     */
    suspend fun revokeAllTokens(): OidcClientResult<Unit> {
        val localToken = token ?: return OidcClientResult.Error(IllegalStateException("No token."))
        val pairsToRevoke = mutableMapOf<RevokeTokenType, String>()

        pairsToRevoke[RevokeTokenType.ACCESS_TOKEN] = localToken.accessToken
        localToken.refreshToken?.let { pairsToRevoke[RevokeTokenType.REFRESH_TOKEN] = it }
        localToken.deviceSecret?.let { pairsToRevoke[RevokeTokenType.DEVICE_SECRET] = it }

        return coroutineScope {
            val exceptionPairs = pairsToRevoke
                .map { entry ->
                    async {
                        (oidcClient.revokeToken(entry.value) as? OidcClientResult.Error<Unit>)?.exception?.let {
                            entry.key to it
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .toMap()

            if (exceptionPairs.isEmpty()) {
                OidcClientResult.Success(Unit)
            } else {
                OidcClientResult.Error(RevokeAllException(exceptionPairs))
            }
        }
    }

    /**
     * Returns the scopes associated with the associated [Token] if present, otherwise the default scopes associated with the [OidcClient].
     */
    fun scope(): String {
        return token?.scope ?: oidcClient.configuration.defaultScope
    }

    /**
     * Retrieve the [Jwt] associated with the [Token.idToken] field.
     *
     * This will return `null` if the associated [Token] or it's `idToken` field is `null`.
     */
    suspend fun idToken(): Jwt? {
        val idToken = token?.idToken ?: return null
        try {
            val parser = JwtParser(oidcClient.configuration.json, oidcClient.configuration.computeDispatcher)
            return parser.parse(idToken)
        } catch (e: Exception) {
            // The token was malformed.
            return null
        }
    }

    /**
     * Checks to see if the current access token is valid, and if it is, returns it.
     * If there is no [Token] associated with the [Credential], null is returned.
     *
     * Access tokens are considered valid if they haven't expired.
     *
     * See [Credential.introspectToken] for checking if the token is valid with the Authorization Server.
     */
    suspend fun getAccessTokenIfValid(): String? {
        val localToken = token ?: return null
        val idToken = idToken() ?: return null
        val accessToken = localToken.accessToken
        val expiresIn = localToken.expiresIn
        try {
            val payload = idToken.deserializeClaims(TokenIssuedAtPayload.serializer())
            if (payload.issueAt + expiresIn > oidcClient.configuration.clock.currentTimeEpochSecond()) {
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
     * be emitted to the associated [EventCoordinator].
     */
    fun accessTokenInterceptor(): Interceptor {
        return AccessTokenInterceptor(::getValidAccessToken, oidcClient.configuration.eventCoordinator, this)
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is Credential) {
            return false
        }
        return storageIdentifier == other.storageIdentifier &&
            state.value == other.state.value
    }

    override fun hashCode(): Int {
        return Objects.hash(
            storageIdentifier,
            state.value,
        )
    }
}

private sealed class CredentialState {
    open val token: Token? get() = null
    open val tags get() = emptyMap<String, String>()

    data class Data(override val token: Token?, override val tags: Map<String, String>) : CredentialState()
    object Deleted : CredentialState()
}

@Serializable
private class TokenIssuedAtPayload(
    @SerialName("iat") val issueAt: Long,
)
