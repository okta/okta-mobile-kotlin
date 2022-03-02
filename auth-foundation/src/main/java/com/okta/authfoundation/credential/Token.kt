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
@Serializable
data class Token internal constructor(
    /**
     * The string type of the token (e.g. `Bearer`).
     */
    @SerialName("token_type") val tokenType: String,
    /**
     * The expiration duration in seconds for this token.
     */
    @SerialName("expires_in") val expiresIn: Int,
    /**
     * The access token.
     */
    @SerialName("access_token") val accessToken: String,
    /**
     * The scopes granted when this token was minted.
     */
    @SerialName("scope") val scope: String,
    /**
     * The refresh token, if requested.
     */
    @SerialName("refresh_token") val refreshToken: String? = null,
    /**
     * The ID token, if requested.
     */
    @SerialName("id_token") val idToken: String? = null,
    /**
     * The device secret, if requested.
     */
    @SerialName("device_secret") val deviceSecret: String? = null,
    /**
     * The issued token type, if returned.
     */
    @SerialName("issued_token_type") val issuedTokenType: String? = null,
)
