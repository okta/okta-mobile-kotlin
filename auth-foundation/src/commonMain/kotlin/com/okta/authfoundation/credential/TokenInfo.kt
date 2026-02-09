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

import kotlinx.serialization.Serializable

/**
 * Represents token information for a user's access to a resource server.
 */
@Serializable
data class TokenInfo(
    /**
     * The string type of the token (e.g. `Bearer`).
     */
    val tokenType: String,
    /**
     * The expiration duration in seconds for this token.
     */
    val expiresIn: Int,
    /**
     * The time the token was issued.
     */
    val issuedAt: Long,
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
)
