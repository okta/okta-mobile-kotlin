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
import com.okta.authfoundation.client.OAuth2Client
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.dto.OidcIntrospectInfo
import com.okta.authfoundation.client.dto.OidcUserInfo
import com.okta.authfoundation.credential.TokenEncryptionHandler.Companion.withAsyncDecryptionContext
import com.okta.authfoundation.credential.TokenEncryptionHandler.Companion.withSyncDecryptionContext
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.runBlocking
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
    token: Token,
    internal val client: OAuth2Client = OAuth2Client.createFromConfiguration(token.oidcConfiguration),
    tags: Map<String, String> = emptyMap()
) {

    /**
     * Identifier for this credential.
     */
    val id = token.id

    internal interface BiometricSecurity {
        val userAuthenticationTimeout: Int

        companion object {
            internal const val TIMEOUT_RANGE_ERROR = "userAuthenticationTimeout must be >= 0"
        }
    }

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
            /**
             * User authentication timeout (in seconds). A timeout of 0 means that auth-per-use keys will be used for Biometrics.
             *
             * > Note: This timeout is ignored on Android 10 and below.
             */
            override val userAuthenticationTimeout: Int = 5,
            override val keyAlias: String = AuthFoundationDefaults.Encryption.keyAlias + ".biometricStrong.timeout.$userAuthenticationTimeout"
        ) : Security, BiometricSecurity {
            init {
                require(userAuthenticationTimeout >= 0) {
                    BiometricSecurity.TIMEOUT_RANGE_ERROR
                }
            }
        }

        /**
         * The stored [Token] is encrypted using a key generated with [KeyProperties.AUTH_BIOMETRIC_STRONG] or [KeyProperties.AUTH_DEVICE_CREDENTIAL]
         */
        data class BiometricStrongOrDeviceCredential(
            /**
             * User authentication timeout (in seconds). A timeout of 0 means that auth-per-use keys will be used for Biometrics.
             */
            override val userAuthenticationTimeout: Int = 5,
            override val keyAlias: String = AuthFoundationDefaults.Encryption.keyAlias + ".biometricStrongOrDeviceCredential.timeout.$userAuthenticationTimeout"
        ) : Security, BiometricSecurity {
            init {
                require(userAuthenticationTimeout >= 0) {
                    BiometricSecurity.TIMEOUT_RANGE_ERROR
                }
            }
        }

        companion object {
            private var _standard: Security? = null

            /**
             * Standard [Credential.Security] level to use when unspecified in method arguments
             */
            var standard: Security
                get() = _standard ?: Default()
                set(value) {
                    _standard = value
                }

            /**
             * [BiometricPrompt.PromptInfo] for specifying how to display biometric prompts. This is used whenever [promptInfo] is omitted in method arguments
             */
            var promptInfo: BiometricPrompt.PromptInfo? = null
        }
    }

    companion object {
        private val defaultCredentialIdDataStore: DefaultCredentialIdDataStore
            get() = DefaultCredentialIdDataStore.instance

        internal suspend fun credentialDataSource() = CredentialDataSource.getInstance()

        /**
         * The default [Credential]. This is null if no default [Credential] exists, and it can be
         * set to null to unset the default [Credential].
         *
         * > Note: This blocks on [setDefaultAsync] and [getDefaultAsync].
         */
        var default: Credential?
            get() = runBlocking {
                defaultCredentialIdDataStore.getDefaultCredentialId()?.let { id -> with(id) }
            }
            set(value) = runBlocking { setDefaultAsync(value) }

        /**
         * Returns the default [Credential], or null if no default [Credential] exists.
         */
        suspend fun getDefaultAsync(): Credential? {
            return defaultCredentialIdDataStore.getDefaultCredentialId()?.let { id -> withAsync(id) }
        }

        /**
         * Sets the default [Credential] to provided [credential]. Unsets the default [Credential] if
         * [credential] is null.
         */
        suspend fun setDefaultAsync(credential: Credential?) {
            credential?.let {
                defaultCredentialIdDataStore.setDefaultCredentialId(credential.id)
                AuthFoundationDefaults.eventCoordinator.sendEvent(
                    DefaultCredentialChangedEvent(
                        credential
                    )
                )
            } ?: defaultCredentialIdDataStore.clearDefaultCredentialId()
        }

        /**
         * Returns IDs of all available [Credential] objects in storage.
         *
         * > Note: This blocks on [allIdsAsync].
         */
        val allIds: List<String>
            get() = runBlocking { allIdsAsync() }

        /**
         * Returns IDs of all available [Credential] objects in storage.
         */
        suspend fun allIdsAsync() = credentialDataSource().allIds()

        /**
         * Returns [Token.Metadata] of [Credential] with specified [id].
         *
         * > Note: This blocks on [metadataAsync].
         *
         * @param id The identifier of the [Credential].
         */
        fun metadata(id: String) = runBlocking { metadataAsync(id) }

        /**
         * Returns [Token.Metadata] of [Credential] with specified [id].
         *
         * @param id The identifier of the [Credential].
         */
        suspend fun metadataAsync(id: String) = credentialDataSource().metadata(id)

        /**
         * Sets the [Token.Metadata] of [Token] with specified [Token.Metadata.id].
         *
         * > Note: This blocks on [setMetadataAsync].
         */
        fun setMetadata(metadata: Token.Metadata) = runBlocking { setMetadataAsync(metadata) }

        /**
         * Sets the [Token.Metadata] of [Token] with specified [Token.Metadata.id].
         */
        suspend fun setMetadataAsync(metadata: Token.Metadata) =
            credentialDataSource().setMetadata(metadata)

        /**
         * Return the [Credential] associated with the given [id].
         *
         * > Note: This blocks on [withAsync].
         *
         * @param id The id of the [Credential] to fetch.
         * @param promptInfo The [BiometricPrompt.PromptInfo] for displaying biometric prompt. A non-null value is required if the [Credential] with [id] is stored using a biometric [Credential.Security].
         */
        fun with(
            id: String,
            promptInfo: BiometricPrompt.PromptInfo? = Security.promptInfo
        ): Credential? {
            return runBlocking {
                withSyncDecryptionContext {
                    credentialDataSource().getCredential(id, promptInfo)
                }
            }
        }

        /**
         * Return the [Credential] associated with the given [id].
         *
         * @param id The id of the [Credential] to fetch.
         * @param promptInfo The [BiometricPrompt.PromptInfo] for displaying biometric prompt. A non-null value is required if the [Credential] with [id] is stored using a biometric [Credential.Security].
         */
        suspend fun withAsync(
            id: String,
            promptInfo: BiometricPrompt.PromptInfo? = Security.promptInfo
        ): Credential? {
            return withAsyncDecryptionContext {
                credentialDataSource().getCredential(id, promptInfo)
            }
        }

        /**
         * Return all [Credential] objects matching the given [where] expression. The [where] expression is supplied with [Token.Metadata] and should return true for cases where the user wants to fetch [Credential] with given [Token.Metadata].
         *
         * > Note: This blocks on [findAsync].
         *
         * @param promptInfo The [BiometricPrompt.PromptInfo] for displaying biometric prompt. A non-null value is required if a fetched [Credential] is stored using a biometric [Credential.Security].
         * @param where A function specifying whether a [Credential] with [Token.Metadata] should be fetched. This function should return true for [Credential] with [Token.Metadata] that should be retrieved from storage.
         */
        fun find(
            promptInfo: BiometricPrompt.PromptInfo? = Security.promptInfo,
            where: (Token.Metadata) -> Boolean
        ): List<Credential> {
            return runBlocking {
                withSyncDecryptionContext {
                    credentialDataSource().findCredential(promptInfo, where)
                }
            }
        }

        /**
         * Return all [Credential] objects matching the given [where] expression. The [where] expression is supplied with [Token.Metadata] and should return true for cases where the user wants to fetch [Credential] with given [Token.Metadata].
         *
         * @param promptInfo The [BiometricPrompt.PromptInfo] for displaying biometric prompt. A non-null value is required if a fetched [Credential] is stored using a biometric [Credential.Security].
         * @param where A function specifying whether a [Credential] with [Token.Metadata] should be fetched. This function should return true for [Credential] with [Token.Metadata] that should be retrieved from storage.
         */
        suspend fun findAsync(
            promptInfo: BiometricPrompt.PromptInfo? = Security.promptInfo,
            where: (Token.Metadata) -> Boolean
        ): List<Credential> {
            return withAsyncDecryptionContext {
                credentialDataSource().findCredential(promptInfo, where)
            }
        }

        /**
         * Store a [Token] with optional [tags] and [security] options.
         * Return the [Credential] object associated with the stored [Token]
         *
         * > Note: This blocks on [storeAsync].
         *
         * @param token The [Token] to store
         * @param tags User-defined value map to store along with [token]
         * @param security Security level for storing the [Token]
         */
        fun store(
            token: Token,
            tags: Map<String, String> = emptyMap(),
            security: Security = Security.standard
        ): Credential {
            return runBlocking { storeAsync(token, tags, security) }
        }

        /**
         * Store a [Token] with optional [tags] and [security] options.
         * Return the [Credential] object associated with the stored [Token]
         *
         * @param token The [Token] to store
         * @param tags User-defined value map to store along with [token]
         * @param security Security level for storing the [Token]
         */
        suspend fun storeAsync(
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

    private val tokenFlow = MutableStateFlow<Token?>(token)

    /**
     * The current [Token] that's stored and associated with this [Credential].
     */
    var token: Token = token
        internal set

    internal var _tags = tags

    /**
     * The tags associated with this [Credential].
     *
     * > Note: Setting this variable blocks on [setTagsAsync].
     */
    var tags: Map<String, String>
        get() = Collections.unmodifiableMap(_tags)
        set(value) {
            runBlocking {
                setTagsAsync(value)
            }
        }

    @VisibleForTesting
    internal var isDeleted: Boolean = false

    /**
     * Set tags for this [Credential].
     */
    suspend fun setTagsAsync(tags: Map<String, String>) {
        setMetadataAsync(Token.Metadata(id, tags, idToken()))
        _tags = tags
        client.configuration.eventCoordinator.sendEvent(
            CredentialStoredEvent(
                this,
                this.token,
                this.tags
            )
        )
    }

    /**
     * Returns a [Flow] that emits the current [Token] that's stored and associated with this [Credential].
     */
    fun getTokenFlow(): Flow<Token> {
        return tokenFlow
            .transformWhile {
                emit(it)
                it != null
            }
            .filterNotNull()
    }

    /**
     * Performs the OIDC User Info call, which returns claims associated with this [Credential].
     *
     * Internally, this uses [Credential.getValidAccessToken] to automatically refresh the access token if it's expired.
     */
    suspend fun getUserInfo(): OAuth2ClientResult<OidcUserInfo> {
        val accessToken = getValidAccessToken() ?: return OAuth2ClientResult.Error(
            IllegalStateException("No Access Token.")
        )
        return client.getUserInfo(accessToken)
    }

    /**
     * Performs a call to the Authorization Server to validate the specified [TokenType].
     *
     * @param tokenType the [TokenType] to check for validity.
     */
    suspend fun introspectToken(tokenType: TokenType): OAuth2ClientResult<OidcIntrospectInfo> {
        return client.introspectToken(tokenType, token)
    }

    internal suspend fun replaceToken(token: Token) {
        if (isDeleted) {
            client.configuration.eventCoordinator.sendEvent(
                CredentialStoredAfterRemovedEvent(
                    this
                )
            )
            return
        }
        val tokenToStore = token.copy(
            // Refresh Token isn't ALWAYS returned when refreshing.
            refreshToken = token.refreshToken ?: this.token.refreshToken,
            // Device Secret isn't returned when refreshing.
            deviceSecret = token.deviceSecret ?: this.token.deviceSecret,
        )
        this.token = tokenToStore
        tokenFlow.emit(token)
        client.configuration.eventCoordinator.sendEvent(
            CredentialStoredEvent(
                this,
                this.token,
                this.tags
            )
        )
    }

    /**
     * Removes this [Credential] from the associated [CredentialDataSource].
     * This [Credential] should not be used after it's been removed.
     *
     * > Notes:
     * > - OIDC Logout terminology is nuanced, see [Logout Documentation](https://github.com/okta/okta-mobile-kotlin#logout) for additional details.
     * > - This function blocks on [deleteAsync].
     */
    fun delete() = runBlocking { deleteAsync() }

    /**
     * Removes this [Credential] from the associated [CredentialDataSource].
     * This [Credential] should not be used after it's been removed.
     *
     * > Note: OIDC Logout terminology is nuanced, see [Logout Documentation](https://github.com/okta/okta-mobile-kotlin#logout) for additional details.
     */
    suspend fun deleteAsync() {
        if (isDeleted) {
            return
        }
        if (defaultCredentialIdDataStore.getDefaultCredentialId() == id) {
            defaultCredentialIdDataStore.clearDefaultCredentialId()
        }
        isDeleted = true
        tokenFlow.emit(null)
        credentialDataSource().remove(this)
        client.configuration.eventCoordinator.sendEvent(CredentialDeletedEvent(this))
    }

    /**
     * Attempt to refresh the [Token] associated with this [Credential].
     */
    suspend fun refreshToken(): OAuth2ClientResult<Token> {
        return refreshCoalescingOrchestrator.get()
    }

    private suspend fun performRealRefresh(): OAuth2ClientResult<Token> {
        return client.refreshToken(token)
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
    ): OAuth2ClientResult<Unit> {
        return client.revokeToken(tokenType, token)
    }

    /**
     * Attempt to revoke all available tokens.
     *
     * > Note: OIDC Logout terminology is nuanced, see [Logout Documentation](https://github.com/okta/okta-mobile-kotlin#logout) for additional details.
     */
    suspend fun revokeAllTokens(): OAuth2ClientResult<Unit> {
        val pairsToRevoke = mutableMapOf<RevokeTokenType, Token>()

        pairsToRevoke[RevokeTokenType.ACCESS_TOKEN] = token
        token.refreshToken?.let { pairsToRevoke[RevokeTokenType.REFRESH_TOKEN] = token }
        token.deviceSecret?.let { pairsToRevoke[RevokeTokenType.DEVICE_SECRET] = token }

        return coroutineScope {
            val exceptionPairs = pairsToRevoke
                .map { entry ->
                    async {
                        (client.revokeToken(entry.key, entry.value) as? OAuth2ClientResult.Error<Unit>)?.exception?.let {
                            entry.key to it
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .toMap()

            if (exceptionPairs.isEmpty()) {
                OAuth2ClientResult.Success(Unit)
            } else {
                OAuth2ClientResult.Error(RevokeAllException(exceptionPairs))
            }
        }
    }

    /**
     * Returns the scopes associated with the associated [Token] if present, otherwise the default scopes associated with the [OAuth2Client].
     */
    fun scope(): String {
        return token.scope ?: client.configuration.defaultScope
    }

    /**
     * Retrieve the [Jwt] associated with the [Token.idToken] field.
     *
     * This will return `null` if the associated [Token] or it's `idToken` field is `null`.
     */
    fun idToken(): Jwt? {
        val idToken = token.idToken ?: return null
        return try {
            val parser =
                JwtParser(client.configuration.json, client.configuration.computeDispatcher)
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
    fun getAccessTokenIfValid(): String? {
        val accessToken = token.accessToken
        val expiresIn = token.expiresIn
        if (token.issuedAt + expiresIn > client.configuration.clock.currentTimeEpochSecond()) {
            return accessToken
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

        return if (refreshToken() is OAuth2ClientResult.Success) {
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
            client.configuration.eventCoordinator,
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
            token == other.token &&
            tags == other.tags
    }

    override fun hashCode(): Int {
        return Objects.hash(
            id,
            token,
            tags
        )
    }
}
