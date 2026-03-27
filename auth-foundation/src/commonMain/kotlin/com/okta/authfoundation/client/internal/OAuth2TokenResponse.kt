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
package com.okta.authfoundation.client.internal

import com.okta.authfoundation.client.TokenInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable token response from the authorization server.
 */
@Serializable
internal class OAuth2TokenResponse(
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("access_token") val accessToken: String,
    @SerialName("scope") val scope: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("id_token") val idToken: String? = null,
    @SerialName("device_secret") val deviceSecret: String? = null,
    @SerialName("issued_token_type") val issuedTokenType: String? = null,
) {
    fun toTokenInfo(
        clientId: String,
        issuerUrl: String,
    ): TokenInfo =
        object : TokenInfo {
            override val clientId: String = clientId
            override val issuerUrl: String = issuerUrl
            override val tokenType: String = this@OAuth2TokenResponse.tokenType
            override val expiresIn: Int = this@OAuth2TokenResponse.expiresIn
            override val accessToken: String = this@OAuth2TokenResponse.accessToken
            override val scope: String? = this@OAuth2TokenResponse.scope
            override val refreshToken: String? = this@OAuth2TokenResponse.refreshToken
            override val idToken: String? = this@OAuth2TokenResponse.idToken
            override val deviceSecret: String? = this@OAuth2TokenResponse.deviceSecret
            override val issuedTokenType: String? = this@OAuth2TokenResponse.issuedTokenType
        }
}
