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
package com.okta.authfoundation.credential.kmp

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.dto.OidcUserInfo
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.authfoundation.credential.RevokeAllException
import com.okta.authfoundation.credential.RevokeTokenType
import com.okta.authfoundation.credential.TokenMetadata
import com.okta.authfoundation.credential.TokenType
import com.okta.authfoundation.credential.events.CredentialDeletedEvent
import com.okta.authfoundation.credential.events.CredentialStoredAfterRemovedEvent
import com.okta.authfoundation.credential.events.CredentialStoredEvent
import com.okta.authfoundation.events.Event
import com.okta.authfoundation.jwt.Jwt
import com.okta.authfoundation.jwt.JwtParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.JsonObject

/**
 * Default cross-platform **immutable** implementation of [Credential].
 *
 * Each instance is a point-in-time snapshot: [token] and [tags] are `val` and never change.
 * Methods that modify credential state return a [Result] wrapping a **new** [CredentialImpl]
 * snapshot with the updated values.
 *
 * Shared mutable state (deletion tracking, token flows, refresh deduplication) is managed by
 * [CredentialDataSource], keyed by credential [id].
 *
 * Used on JVM and other non-Android KMP targets. On Android, the existing
 * [Credential][com.okta.authfoundation.credential.Credential] class provides a mutable adapter.
 */
@OptIn(InternalAuthFoundationApi::class)
class CredentialImpl internal constructor(
    token: TokenData,
    internal val client: OAuth2Client = OAuth2Client.createFromConfiguration(token.configuration),
    override val tags: Map<String, String> = emptyMap(),
    private val dataSource: CredentialDataSource,
    private val events: MutableSharedFlow<Event>,
    private val defaultIdStore: DefaultCredentialIdStore,
) : Credential {
    override val id: String = token.id
    override val token: TokenData = token

    override fun getTokenFlow(): Flow<TokenData> = dataSource.getTokenFlow(id, token)

    @OptIn(InternalAuthFoundationApi::class)
    override suspend fun deleteAsync(): Result<Unit> =
        runCatching {
            if (dataSource.isDeleted(id)) {
                throw IllegalStateException("Credential $id has already been deleted.")
            }
            if (defaultIdStore.getDefaultCredentialId() == id) {
                defaultIdStore.clearDefaultCredentialId()
            }
            dataSource.markDeleted(id)
            dataSource.remove(id)
            events.tryEmit(CredentialDeletedEvent(this))
        }

    @Suppress("UNCHECKED_CAST")
    override suspend fun getUserInfo(): Result<OidcUserInfo> =
        runCatching {
            val fresh = getValidAccessToken().getOrThrow()
            val accessToken = fresh.token.accessToken
            when (val result = client.getUserInfo(accessToken)) {
                is OAuth2ClientResult.Success<OidcUserInfo> -> result.result
                is OAuth2ClientResult.Error<OidcUserInfo> -> throw result.exception
            }
        }

    /**
     * Introspects a specific token type at the authorization server.
     *
     * @return [Result.success] with [JsonObject] or [Result.failure] on error.
     */
    override suspend fun introspectToken(tokenType: TokenType): Result<JsonObject> =
        runCatching {
            val tokenStr =
                when (tokenType) {
                    TokenType.ACCESS_TOKEN -> token.accessToken
                    TokenType.REFRESH_TOKEN -> token.refreshToken
                    TokenType.ID_TOKEN -> token.idToken
                    TokenType.DEVICE_SECRET -> token.deviceSecret
                } ?: throw IllegalStateException("Token type ${tokenType.name} not available.")
            when (val result = client.introspectToken(tokenType.toTokenTypeHint(), tokenStr)) {
                is OAuth2ClientResult.Success -> result.result
                is OAuth2ClientResult.Error -> throw result.exception
            }
        }

    /**
     * Refreshes the token and returns a new credential snapshot.
     *
     * Uses a shared [CoalescingOrchestrator][com.okta.authfoundation.util.CoalescingOrchestrator]
     * for deduplication across all snapshots of the same credential ID.
     *
     * @return [Result.success] with a new [CredentialImpl] snapshot, or [Result.failure].
     */
    override suspend fun refreshToken(): Result<Credential> =
        runCatching {
            val orchestrator = dataSource.getOrCreateRefreshOrchestrator(id, ::performRealRefresh)
            val refreshResult = orchestrator.get()
            when (refreshResult) {
                is OAuth2ClientResult.Success -> {
                    val newToken = refreshResult.result
                    replaceToken(newToken)
                }

                is OAuth2ClientResult.Error -> {
                    throw refreshResult.exception
                }
            }
        }

    private suspend fun performRealRefresh(): OAuth2ClientResult<TokenInfo> {
        val refreshTokenStr =
            token.refreshToken ?: return OAuth2ClientResult.Error(
                IllegalStateException("No refresh token available.")
            )
        return client.refreshToken(refreshTokenStr)
    }

    override suspend fun revokeToken(tokenType: RevokeTokenType): Result<Unit> =
        runCatching {
            val tokenStr =
                tokenStringForType(tokenType) ?: throw IllegalStateException("Token type ${tokenType.name} not available.")
            when (val result = client.revokeToken(tokenStr)) {
                is OAuth2ClientResult.Success -> Unit
                is OAuth2ClientResult.Error -> throw result.exception
            }
        }

    override suspend fun revokeAllTokens(): Result<Unit> {
        val pairsToRevoke = mutableMapOf<RevokeTokenType, String>()
        pairsToRevoke[RevokeTokenType.ACCESS_TOKEN] = token.accessToken
        token.refreshToken?.let { pairsToRevoke[RevokeTokenType.REFRESH_TOKEN] = it }
        token.deviceSecret?.let { pairsToRevoke[RevokeTokenType.DEVICE_SECRET] = it }

        return runCatching {
            coroutineScope {
                val exceptionPairs =
                    pairsToRevoke
                        .map { entry ->
                            async {
                                (client.revokeToken(entry.value) as? OAuth2ClientResult.Error<Unit>)?.exception?.let {
                                    entry.key to it
                                }
                            }
                        }.awaitAll()
                        .filterNotNull()
                        .toMap()

                if (exceptionPairs.isNotEmpty()) {
                    throw RevokeAllException(exceptionPairs)
                }
            }
        }
    }

    override suspend fun getValidAccessToken(): Result<Credential> {
        getAccessTokenIfValid()?.let { return Result.success(this) }
        return refreshToken()
    }

    override fun getAccessTokenIfValid(): String? {
        val accessToken = token.accessToken
        val expiresIn = token.expiresIn
        if (token.issuedAt + expiresIn > token.configuration.clock.currentTimeEpochSecond()) {
            return accessToken
        }
        return null
    }

    override fun idToken(): Jwt? {
        val idTokenStr = token.idToken ?: return null
        return try {
            val parser = JwtParser(client.configuration.json, Dispatchers.Default)
            parser.parse(idTokenStr)
        } catch (_: Exception) {
            null
        }
    }

    @OptIn(InternalAuthFoundationApi::class)
    override suspend fun setTagsAsync(tags: Map<String, String>): Result<Credential> =
        runCatching {
            if (dataSource.isDeleted(id)) {
                throw IllegalStateException("Credential $id has already been deleted.")
            }
            dataSource.setMetadata(TokenMetadata(id = id, tags = tags, payloadData = null))
            val snapshot =
                CredentialImpl(
                    token = token,
                    client = client,
                    tags = tags,
                    dataSource = dataSource,
                    events = events,
                    defaultIdStore = defaultIdStore
                )
            events.tryEmit(CredentialStoredEvent(snapshot, token, tags))
            snapshot
        }

    override fun scope(): String = token.scope ?: client.configuration.defaultScope

    /**
     * Replaces the current token in storage and returns a new immutable snapshot.
     *
     * Emits the updated token on the shared flow and fires [CredentialStoredEvent].
     */
    @OptIn(InternalAuthFoundationApi::class)
    internal suspend fun replaceToken(newToken: TokenInfo): Credential {
        val deleted = dataSource.isDeleted(id)
        if (deleted) {
            events.tryEmit(CredentialStoredAfterRemovedEvent(this))
            return this
        }
        // Re-key the incoming token to this credential's ID so storage lookups succeed.
        val rekeyed =
            TokenData(
                id = id,
                tokenType = newToken.tokenType,
                expiresIn = newToken.expiresIn,
                accessToken = newToken.accessToken,
                scope = newToken.scope,
                refreshToken = newToken.refreshToken ?: token.refreshToken,
                idToken = newToken.idToken,
                deviceSecret = newToken.deviceSecret ?: token.deviceSecret,
                issuedTokenType = newToken.issuedTokenType,
                configuration = token.configuration
            )
        val updated = dataSource.replaceToken(rekeyed)
        val snapshotToken = updated ?: token
        dataSource.emitToken(id, snapshotToken)
        val snapshot =
            CredentialImpl(
                token = snapshotToken,
                client = client,
                tags = tags,
                dataSource = dataSource,
                events = events,
                defaultIdStore = defaultIdStore
            )
        events.tryEmit(CredentialStoredEvent(snapshot, snapshotToken, tags))
        return snapshot
    }

    private fun tokenStringForType(tokenType: RevokeTokenType): String? =
        when (tokenType) {
            RevokeTokenType.ACCESS_TOKEN -> token.accessToken
            RevokeTokenType.REFRESH_TOKEN -> token.refreshToken
            RevokeTokenType.DEVICE_SECRET -> token.deviceSecret
        }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is CredentialImpl) return false
        return id == other.id && token == other.token && tags == other.tags
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + token.hashCode()
        result = 31 * result + tags.hashCode()
        return result
    }
}
