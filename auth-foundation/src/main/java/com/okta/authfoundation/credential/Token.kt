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
package com.okta.authfoundation.credential

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.Objects

/**
 * Token information representing a user's access to a resource server, including access token, refresh token, and other related information.
 */
@Serializable
class Token(
    /**
     * The string type of the token (e.g. `Bearer`).
     */
    val tokenType: String,
    /**
     * The expiration duration in seconds for this token.
     */
    val expiresIn: Int,
    /**
     * The access token.
     */
    val accessToken: String,
    /**
     * The scopes granted when this token was minted.
     */
    val scope: String?,
    /**
     * The refresh token, if requested.
     */
    val refreshToken: String?,
    /**
     * The ID token, if requested.
     */
    val idToken: String?,
    /**
     * The device secret, if requested.
     */
    val deviceSecret: String?,
    /**
     * The issued token type, if returned.
     */
    val issuedTokenType: String?,
) {
    internal fun asSerializableToken(): SerializableToken {
        return SerializableToken(
            tokenType = tokenType,
            expiresIn = expiresIn,
            accessToken = accessToken,
            scope = scope,
            refreshToken = refreshToken,
            idToken = idToken,
            deviceSecret = deviceSecret,
            issuedTokenType = issuedTokenType,
        )
    }

    internal fun copy(
        refreshToken: String?,
        deviceSecret: String?,
    ): Token {
        return Token(
            tokenType = tokenType,
            expiresIn = expiresIn,
            accessToken = accessToken,
            scope = scope,
            refreshToken = refreshToken,
            idToken = idToken,
            deviceSecret = deviceSecret,
            issuedTokenType = issuedTokenType,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is Token) {
            return false
        }
        return other.tokenType == tokenType &&
            other.expiresIn == expiresIn &&
            other.accessToken == accessToken &&
            other.scope == scope &&
            other.refreshToken == refreshToken &&
            other.idToken == idToken &&
            other.deviceSecret == deviceSecret &&
            other.issuedTokenType == issuedTokenType
    }

    override fun hashCode(): Int {
        return Objects.hash(
            tokenType,
            expiresIn,
            accessToken,
            scope,
            refreshToken,
            idToken,
            deviceSecret,
            issuedTokenType,
        )
    }

    data class Metadata(
        val id: String,
        val tags: Map<String, String>,
        val payloadData: JsonObject
    )
}

@Serializable
internal class SerializableToken internal constructor(
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("access_token") val accessToken: String,
    @SerialName("scope") val scope: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("id_token") val idToken: String? = null,
    @SerialName("device_secret") val deviceSecret: String? = null,
    @SerialName("issued_token_type") val issuedTokenType: String? = null,
) {
    fun asToken(): Token {
        return Token(
            tokenType = tokenType,
            expiresIn = expiresIn,
            accessToken = accessToken,
            scope = scope,
            refreshToken = refreshToken,
            idToken = idToken,
            deviceSecret = deviceSecret,
            issuedTokenType = issuedTokenType,
        )
    }
}
