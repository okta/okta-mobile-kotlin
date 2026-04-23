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

import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.dto.OidcUserInfo
import com.okta.authfoundation.credential.CredentialIdentifier
import com.okta.authfoundation.credential.RevokeTokenType
import com.okta.authfoundation.credential.TokenType
import com.okta.authfoundation.jwt.Jwt
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * Cross-platform credential interface providing token management operations.
 *
 * Instances are **immutable snapshots**: [token], [tags], and [id] never change after construction.
 * Methods that modify credential state return a [Result] containing a new [Credential] snapshot.
 *
 * On Android, the existing [Credential][com.okta.authfoundation.credential.Credential] class
 * implements this interface while preserving mutable internal behavior as a transitional adapter.
 * On JVM and other KMP targets, [CredentialImpl] provides the default immutable implementation.
 */
interface Credential : CredentialIdentifier {
    /** Unique identifier for this credential. */
    override val id: String

    /** The token associated with this credential (point-in-time snapshot). */
    val token: TokenInfo

    /** The tags associated with this credential (point-in-time snapshot). */
    val tags: Map<String, String>

    /**
     * Deletes this credential from storage.
     *
     * On success the credential is removed from persistent storage and the default credential
     * is cleared if this credential was the default.
     *
     * @return [Result.success] with [Unit] if deleted, or [Result.failure] with:
     * - [IllegalStateException] if this credential has already been deleted.
     * - Other exceptions if the underlying storage operation fails.
     */
    suspend fun deleteAsync(): Result<Unit>

    /**
     * Returns a [Flow] that emits the current token and subsequent updates.
     *
     * The flow is shared across all snapshots of the same credential ID.
     */
    fun getTokenFlow(): Flow<TokenInfo>

    /**
     * Fetches user info from the authorization server.
     *
     * Automatically refreshes the access token if it has expired before making the request.
     *
     * @return [Result.success] with [OidcUserInfo], or [Result.failure] with:
     * - [IllegalStateException] if the access token is expired and cannot be refreshed.
     * - Other exceptions if the network request fails.
     */
    suspend fun getUserInfo(): Result<OidcUserInfo>

    /**
     * Revokes a specific token type at the authorization server.
     *
     * @param tokenType the [RevokeTokenType] to revoke.
     * @return [Result.success] with [Unit] if revoked, or [Result.failure] with:
     * - [IllegalStateException] if the requested token type is not present on this credential.
     * - Other exceptions if the network request fails.
     */
    suspend fun revokeToken(tokenType: RevokeTokenType): Result<Unit>

    /**
     * Revokes all available token types (access token, refresh token, device secret).
     *
     * Revocations are performed concurrently. If any individual revocation fails, the others
     * still proceed and all failures are collected.
     *
     * @return [Result.success] with [Unit] if all revocations succeed, or [Result.failure] with:
     * - [RevokeAllException] containing a map of [RevokeTokenType] to [Exception] for each
     *   token type that failed. Callers can inspect the map to handle partial failures.
     */
    suspend fun revokeAllTokens(): Result<Unit>

    /**
     * Ensures the credential has a non-expired access token, refreshing if necessary.
     *
     * If the current access token has not expired, returns this credential immediately
     * without any network call. If expired, performs a token refresh and returns a **new**
     * [Credential] snapshot containing the refreshed token. The original snapshot
     * is never mutated.
     *
     * Typical usage:
     * ```kotlin
     * val fresh = credential.refreshIfExpired().getOrThrow()
     * val accessToken = fresh.token.accessToken // guaranteed non-expired
     * ```
     *
     * @return [Result.success] with a [Credential] snapshot containing a valid token,
     *   or [Result.failure] with:
     * - [IllegalStateException] if the token is expired and no refresh token is available.
     * - Other exceptions if the refresh network request fails.
     * @see accessTokenIfNotExpired for a lightweight expiry check without network calls.
     */
    suspend fun refreshIfExpired(): Result<Credential>

    /**
     * Returns the access token string if it has not expired, or `null` if it has.
     *
     * This is a **pure read** with no side effects — it checks the token's
     * `issuedAt + expiresIn` against the current clock and never triggers a network call.
     * Use [refreshIfExpired] when you need a guaranteed-valid token.
     *
     * @return the access token string, or `null` if the token has expired.
     */
    fun accessTokenIfNotExpired(): String?

    /**
     * Updates the tags for this credential.
     *
     * @param tags the new tag map to associate with this credential.
     * @return [Result.success] with a new [Credential] snapshot containing the updated tags,
     *   or [Result.failure] with:
     * - [IllegalStateException] if this credential has been deleted.
     * - Other exceptions if the underlying storage operation fails.
     */
    suspend fun setTagsAsync(tags: Map<String, String>): Result<Credential>

    /**
     * Introspects a specific token type at the authorization server.
     *
     * @param tokenType the [TokenType] to introspect.
     * @return [Result.success] with [JsonObject] containing the introspection response,
     *   or [Result.failure] with:
     * - [IllegalStateException] if the requested [tokenType] is not present on this credential.
     * - Other exceptions if the network request fails.
     */
    suspend fun introspectToken(tokenType: TokenType): Result<JsonObject>

    /**
     * Refreshes the token and returns a new credential snapshot.
     *
     * Uses a shared orchestrator for deduplication — concurrent calls for the same credential ID
     * will coalesce into a single network request.
     *
     * @return [Result.success] with a new [Credential] snapshot containing the refreshed token,
     *   or [Result.failure] with:
     * - [IllegalStateException] if no refresh token is available on this credential.
     * - Other exceptions if the refresh network request fails.
     */
    suspend fun refreshToken(): Result<Credential>

    /**
     * Retrieves the parsed [Jwt] from the ID token.
     *
     * @return [Result.success] with the parsed [Jwt], or [Result.failure] with:
     * - [NoSuchElementException] if no ID token is present on this credential.
     * - Other exceptions if the ID token string is malformed and cannot be parsed as a JWT.
     */
    fun idToken(): Result<Jwt>

    /** Returns the scopes associated with the token, or the client's default scopes. Pure read. */
    fun scope(): String
}
