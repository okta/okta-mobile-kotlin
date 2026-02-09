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
package com.okta.authfoundation.client.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the response from an OAuth 2.0 Token Introspection endpoint
 * as defined in RFC 7662.
 * * @see <a href="https://datatracker.ietf.org/doc/html/rfc7662#section-2.2">RFC 7662 Section 2.2</a>
 */
@Serializable
data class TokenIntrospectionResponse(
    /**
     * REQUIRED. Boolean indicator of whether the presented token
     * is currently active.
     */
    val active: Boolean,
    /**
     * OPTIONAL. A JSON string containing a space-separated list of
     * OAuth Scopes associated with this token.
     */
    val scope: String? = null,
    /**
     * OPTIONAL. Client identifier for the OAuth Client that requested this token.
     */
    @SerialName("client_id")
    val clientId: String? = null,
    /**
     * OPTIONAL. Human-readable identifier for the resource owner who
     * authorized this token.
     */
    val username: String? = null,
    /**
     * OPTIONAL. Type of the token as defined in OAuth 2.0 section 5.1.
     * Common values: "Bearer".
     */
    @SerialName("token_type")
    val tokenType: String? = null,
    /**
     * OPTIONAL. Integer timestamp, measured in the number of seconds
     * since January 1, 1970, UTC, indicating when this token will expire.
     */
    val exp: Long? = null,
    /**
     * OPTIONAL. Integer timestamp, measured in the number of seconds
     * since January 1, 1970, UTC, indicating when this token was originally issued.
     */
    val iat: Long? = null,
    /**
     * OPTIONAL. Integer timestamp, measured in the number of seconds
     * since January 1, 1970, UTC, indicating when this token is not to be
     * used before.
     */
    val nbf: Long? = null,
    /**
     * OPTIONAL. Subject of the token (user ID).
     */
    val sub: String? = null,
    /**
     * OPTIONAL. Service-specific string identifier or list of string
     * identifiers representing the intended audience for this token.
     */
    @Serializable(with = AudienceSerializer::class)
    val aud: List<String>? = null,
    /**
     * OPTIONAL. String representing the issuer of this token.
     */
    val iss: String? = null,
    /**
     * OPTIONAL. String identifier for the token.
     */
    val jti: String? = null,
)
