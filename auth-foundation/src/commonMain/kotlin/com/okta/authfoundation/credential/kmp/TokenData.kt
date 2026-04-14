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
import com.okta.authfoundation.client.OAuth2ClientConfiguration
import com.okta.authfoundation.client.TokenInfo

/**
 * Default cross-platform implementation of [TokenInfo].
 *
 * Used on JVM and other non-Android KMP targets where the Android-specific [Token] is not available.
 */
class TokenData(
    override val id: String,
    override val tokenType: String,
    override val expiresIn: Int,
    override val accessToken: String,
    override val scope: String?,
    override val refreshToken: String?,
    override val idToken: String?,
    override val deviceSecret: String?,
    override val issuedTokenType: String?,
    val configuration: OAuth2ClientConfiguration,
    val issuedAt: Long = configuration.clock.currentTimeEpochSecond() - expiresIn,
) : TokenInfo {
    override val clientId: String = configuration.clientId
    override val issuerUrl: String = configuration.issuerUrl

    /**
     * Creates a copy with optionally replaced fields.
     */
    fun copy(
        id: String = this.id,
        refreshToken: String? = this.refreshToken,
        deviceSecret: String? = this.deviceSecret,
    ): TokenData =
        TokenData(
            id = id,
            tokenType = tokenType,
            expiresIn = expiresIn,
            accessToken = accessToken,
            scope = scope,
            refreshToken = refreshToken,
            idToken = idToken,
            deviceSecret = deviceSecret,
            issuedTokenType = issuedTokenType,
            configuration = configuration,
            issuedAt = issuedAt
        )

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is TokenData) return false
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
        var result = tokenType.hashCode()
        result = 31 * result + expiresIn
        result = 31 * result + accessToken.hashCode()
        result = 31 * result + (scope?.hashCode() ?: 0)
        result = 31 * result + (refreshToken?.hashCode() ?: 0)
        result = 31 * result + (idToken?.hashCode() ?: 0)
        result = 31 * result + (deviceSecret?.hashCode() ?: 0)
        result = 31 * result + (issuedTokenType?.hashCode() ?: 0)
        return result
    }
}
