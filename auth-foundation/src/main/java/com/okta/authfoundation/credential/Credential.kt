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

import android.security.keystore.KeyProperties
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
import com.okta.authfoundation.credential.events.DefaultCredentialChangedEvent
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
import kotlinx.coroutines.flow.filterIsInstance
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
    val id: String,
    token: Token,
    oidcClient: OidcClient = OidcClient.createFromConfiguration(token.oidcConfiguration),
    tags: Map<String, String> = emptyMap()
) {
    internal interface BiometricSecurity

    /**
     * Convenience object for specifying security level for storing [Token] objects
     */
    sealed interface Security {
        val keyAlias: String

        /**
         * Default security level. The stored [Token] is encrypted with a non-biometric key in keychain
         */
        data class Default(
            override val keyAlias: String = AuthFoundationDefaults.Encryption.keyAlias
        ) : Security

        /**
         * The stored [Token] is encrypted using a key generated with [KeyProperties.AUTH_BIOMETRIC_STRONG]
         */
        data class BiometricStrong(
            override val keyAlias: String = AuthFoundationDefaults.Encryption.keyAlias + ".biometricStrong"
        ) : Security, BiometricSecurity

        /**
         * The stored [Token] is encrypted using a key generated with [KeyProperties.AUTH_BIOMETRIC_STRONG] or [KeyProperties.AUTH_DEVICE_CREDENTIAL]
         */
        data class BiometricStrongOrDeviceCredential(
            override val keyAlias: String = AuthFoundationDefaults.Encryption.keyAlias + ".biometricStrongOrDeviceCredential"
        ) : Security, BiometricSecurity

        companion object {
            private var _standard: Security? = null

            /**
             * Standard [Credential.Security] level to use when unspecified in method arguments
             */
            var standard: Security
                get() = _standard ?: Default()
                set(value) { _standard = value }

            /**
             * [BiometricPrompt.PromptInfo] for specifying how to display biometric prompts. This is used whenever [promptInfo] is omitted in method arguments
             */
            var promptInfo: BiometricPrompt.PromptInfo? = null
        }
    }

    companion object {
        private val defaultCredentialIdDataStore: DefaultCredentialIdDataStore
            get() = DefaultCredentialIdDataStore.instance

        @VisibleForTesting
        internal suspend fun credentialDataSource() = CredentialDataSource.getInstance()

        /**
         * Returns the default [Credential], or null if no default [Credential] exists.
         */
        suspend fun getDefaultCredential(): Credential? {
            return defaultCredentialIdDataStore.getDefaultCredentialId()?.let { id -> with(id) }
        }

        /**
         * Sets the default [Credential] to provided [credential].
         */
        suspend fun setDefaultCredential(credential: Credential) {
            defaultCredentialIdDataStore.setDefaultCredentialId(credential.id)
            AuthFoundationDefaults.eventCoordinator.sendEvent(DefaultCredentialChangedEvent(credential))
        }

        /**
         * Returns IDs of all available [Credential] objects in storage.
         */
        suspend fun allIds(): List<String> = credentialDataSource().allIds()

        /**
         * Returns [Token.Metadata] of [Credential] with specified [id].
         *
         * @param id The identifier of the [Credential].
         */
        suspend fun metadata(id: String) = credentialDataSource().metadata(id)

        /**
         * Sets the [Token.Metadata] of [Token] with specified [Token.Metadata.id].
         */
        suspend fun setMetadata(metadata: Token.Metadata) = credentialDataSource().setMetadata(metadata)

        /**
         * Return the [Credential] associated with the given [id].
         *
         * @param id The id of the [Credential] to fetch.
         * @param promptInfo The [BiometricPrompt.PromptInfo] for displaying biometric prompt. A non-null value is required if the [Credential] with [id] is stored using a biometric [Credential.Security].
         */
        suspend fun with(id: String, promptInfo: BiometricPrompt.PromptInfo? = Security.promptInfo): Credential? {
            return credentialDataSource().getCredential(id, promptInfo)
        }

        /**
         * Return all [Credential] objects matching the given [where] expression. The [where] expression is supplied with [Token.Metadata] and should return true for cases where the user wants to fetch [Credential] with given [Token.Metadata].
         *
         * @param promptInfo The [BiometricPrompt.PromptInfo] for displaying biometric prompt. A non-null value is required if a fetched [Credential] is stored using a biometric [Credential.Security].
         * @param where A function specifying whether a [Credential] with [Token.Metadata] should be fetched. This function should return true for [Credential] with [Token.Metadata] that should be retrieved from storage.
         */
        suspend fun find(
            promptInfo: BiometricPrompt.PromptInfo? = Security.promptInfo,
            where: (Token.Metadata) -> Boolean
        ): List<Credential> {
            return credentialDataSource().findCredential(promptInfo, where)
        }

        /**
         * Store a [Token] with optional [tags] and [security] options.
         * Return the [Credential] object associated with the stored [Token]
         *
         * @param token The [Token] to store
         * @param tags User-defined value map to store along with [token]
         * @param security Security level for storing the [Token]
         */
        suspend fun store(
            token: Token,
            tags: Map<String, String> = emptyMap(),
            security: Security = Security.standard
        ): Credential {
            return credentialDataSource().createCredential(token, tags, security)
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
            return when (val value = state.value) {
                is CredentialState.Deleted -> null
                is CredentialState.Data -> value.token
            }
        }

    /**
     * The tags associated with this [Credential].
     */
    val tags: Map<String, String>
        get() {
            return Collections.unmodifiableMap(state.value.tags)
        }

    /**
     * Set tags for this [Credential].
     */
    suspend fun setTags(tags: Map<String, String>) {
        token?.let {
            setMetadata(Token.Metadata(id, tags, idToken()))
            state.value = CredentialState.Data(it, tags)
            oidcClient.configuration.eventCoordinator.sendEvent(
                CredentialStoredEvent(
                    this,
                    this.token,
                    this.tags
                )
            )
        } ?: throw IllegalStateException("Attempted setting tags for deleted token with id: $id")
    }

    /**
     * Returns a [Flow] that emits the current [Token] that's stored and associated with this [Credential].
     */
    fun getTokenFlow(): Flow<Token> {
        return state
            .transformWhile {
                emit(it)
                it !is CredentialState.Deleted
            }
            .filterIsInstance(CredentialState.Data::class)
            .map { it.token }
    }

    /**
     * Performs the OIDC User Info call, which returns claims associated with this [Credential].
     *
     * Internally, this uses [Credential.getValidAccessToken] to automatically refresh the access token if it's expired.
     */
    suspend fun getUserInfo(): OidcClientResult<OidcUserInfo> {
        val accessToken = getValidAccessToken() ?: return OidcClientResult.Error(
            IllegalStateException("No Access Token.")
        )
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
                localToken.refreshToken
                    ?: return OidcClientResult.Error(IllegalStateException("No refresh token."))
            }

            TokenType.ACCESS_TOKEN -> {
                localToken.accessToken
            }

            TokenType.ID_TOKEN -> {
                localToken.idToken
                    ?: return OidcClientResult.Error(IllegalStateException("No id token."))
            }

            TokenType.DEVICE_SECRET -> {
                localToken.deviceSecret
                    ?: return OidcClientResult.Error(IllegalStateException("No device secret."))
            }
        }

        return oidcClient.introspectToken(tokenType, token)
    }

    /**
     * Replace the [Token] stored in this [Credential].
     *
     * @param token The [Token] to store.
     */
    suspend fun replaceToken(token: Token) {
        if (isDeleted) {
            oidcClient.configuration.eventCoordinator.sendEvent(
                CredentialStoredAfterRemovedEvent(
                    this
                )
            )
            return
        }
        val tokenToStore = token.copy(
            // Refresh Token isn't ALWAYS returned when refreshing.
            refreshToken = token.refreshToken ?: this.token?.refreshToken,
            // Device Secret isn't returned when refreshing.
            deviceSecret = token.deviceSecret ?: this.token?.deviceSecret,
        )
        credentialDataSource().replaceToken(id, tokenToStore)
        state.value = CredentialState.Data(tokenToStore, tags)
        oidcClient.configuration.eventCoordinator.sendEvent(
            CredentialStoredEvent(
                this,
                this.token,
                this.tags
            )
        )
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
        if (defaultCredentialIdDataStore.getDefaultCredentialId() == id) {
            defaultCredentialIdDataStore.clearDefaultCredentialId()
        }
        credentialDataSource().remove(this)
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
        val refresh = localToken.refreshToken
            ?: return OidcClientResult.Error(IllegalStateException("No Refresh Token."))
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
                localToken.refreshToken
                    ?: return OidcClientResult.Error(IllegalStateException("No refresh token."))
            }

            RevokeTokenType.ACCESS_TOKEN -> {
                localToken.accessToken
            }

            RevokeTokenType.DEVICE_SECRET -> {
                localToken.deviceSecret
                    ?: return OidcClientResult.Error(IllegalStateException("No device secret."))
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
        return try {
            val parser =
                JwtParser(oidcClient.configuration.json, oidcClient.configuration.computeDispatcher)
            parser.parse(idToken)
        } catch (e: Exception) {
            // The token was malformed.
            null
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

        return if (refreshToken() is OidcClientResult.Success) {
            getAccessTokenIfValid()
        } else {
            null
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
        return AccessTokenInterceptor(
            ::getValidAccessToken,
            oidcClient.configuration.eventCoordinator,
            this
        )
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is Credential) {
            return false
        }
        return id == other.id &&
            state.value == other.state.value
    }

    override fun hashCode(): Int {
        return Objects.hash(
            id,
            state.value,
        )
    }
}

private sealed class CredentialState {
    open val tags get() = emptyMap<String, String>()

    data class Data(
        val token: Token,
        override val tags: Map<String, String>
    ) : CredentialState()

    data object Deleted : CredentialState()
}

@Serializable
private class TokenIssuedAtPayload(
    @SerialName("iat") val issueAt: Long,
)
