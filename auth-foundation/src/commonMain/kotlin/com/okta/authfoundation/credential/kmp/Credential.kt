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
     * @return [Result.success] with [Unit] if deleted, [Result.failure] if storage fails or already deleted.
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
     * @return [Result.success] with [OidcUserInfo] or [Result.failure] on error.
     */
    suspend fun getUserInfo(): Result<OidcUserInfo>

    /**
     * Revokes a specific token type at the authorization server.
     *
     * @return [Result.success] with [Unit] or [Result.failure] on error.
     */
    suspend fun revokeToken(tokenType: RevokeTokenType): Result<Unit>

    /**
     * Revokes all available token types. Reports partial failures via [RevokeAllException].
     *
     * @return [Result.success] with [Unit] if all revoked, [Result.failure] with [RevokeAllException] if any fail.
     */
    suspend fun revokeAllTokens(): Result<Unit>

    /**
     * Returns a new credential snapshot with a valid access token, refreshing if expired.
     *
     * @return [Result.success] with a [Credential] snapshot containing a valid token,
     *         or [Result.failure] if no access token is available and refresh fails.
     */
    suspend fun getValidAccessToken(): Result<Credential>

    /** Returns the access token if it hasn't expired, null otherwise. Pure read. */
    fun getAccessTokenIfValid(): String?

    /**
     * Updates the tags for this credential.
     *
     * @return [Result.success] with a new [Credential] snapshot containing the updated tags,
     *         or [Result.failure] if storage fails.
     */
    suspend fun setTagsAsync(tags: Map<String, String>): Result<Credential>

    /**
     * Introspects a specific token type at the authorization server.
     *
     * @param tokenType the [TokenType] to introspect.
     * @return [Result.success] with [JsonObject] or [Result.failure] on error.
     */
    suspend fun introspectToken(tokenType: TokenType): Result<JsonObject>

    /**
     * Refreshes the token and returns a new credential snapshot.
     *
     * @return [Result.success] with a new [Credential] snapshot containing the refreshed token,
     *         or [Result.failure] if no refresh token is available or refresh fails.
     */
    suspend fun refreshToken(): Result<Credential>

    /**
     * Retrieves the parsed [Jwt] from the ID token, if present.
     *
     * @return the parsed [Jwt], or null if no ID token is present or the token is malformed.
     */
    fun idToken(): Jwt?

    /** Returns the scopes associated with the token, or the client's default scopes. Pure read. */
    fun scope(): String
}
