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
package com.okta.authfoundation.client

/**
 * Read-only view of an OAuth2 token set (access token, optional refresh token, ID token, and
 * device secret) plus the identity of the client and issuer that minted it.
 *
 * This is the cross-platform token contract returned by [com.okta.authfoundation.client.kmp.OAuth2Client]
 * and stored via [com.okta.authfoundation.credential.kmp.TokenCredentialManager]. The default
 * implementation is [com.okta.authfoundation.credential.kmp.TokenData]; on Android the deprecated
 * [com.okta.authfoundation.credential.Token] also implements it.
 */
interface TokenInfo {
    /**
     * Unique identifier for this token.
     */
    val id: String

    /**
     * The client id of the application that minted this token.
     */
    val clientId: String

    /**
     * The issuer url of the token.
     */
    val issuerUrl: String

    /**
     * The string type of the token (e.g. `Bearer`).
     */
    val tokenType: String

    /**
     * The expiration duration in seconds for this token.
     */
    val expiresIn: Int

    /**
     * The access token.
     */
    val accessToken: String

    /**
     * The scopes granted when this token was minted.
     */
    val scope: String?

    /**
     * The refresh token, if requested.
     */
    val refreshToken: String?

    /**
     * The ID token, if requested.
     */
    val idToken: String?

    /**
     * The device secret, if requested.
     */
    val deviceSecret: String?

    /**
     * The issued token type, if returned.
     */
    val issuedTokenType: String?
}
