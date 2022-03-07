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

/**
 * Token information representing a user's access to a resource server, including access token, refresh token, and other related information.
 */
data class Token internal constructor(
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
    val scope: String,
    /**
     * The refresh token, if requested.
     */
    val refreshToken: String? = null,
    /**
     * The ID token, if requested.
     */
    val idToken: String? = null,
    /**
     * The device secret, if requested.
     */
    val deviceSecret: String? = null,
    /**
     * The issued token type, if returned.
     */
    val issuedTokenType: String? = null,
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
}

@Serializable
internal class SerializableToken internal constructor(
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("access_token") val accessToken: String,
    @SerialName("scope") val scope: String,
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
