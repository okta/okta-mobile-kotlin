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
package com.okta.authfoundation.client.kmp

import com.okta.authfoundation.jwt.Jwt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Default [AccessTokenValidator] that validates the `at_hash` claim in an ID token
 * against the SHA-256 left-half hash of the access token, per
 * [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html#ImplicitTokenValidation).
 *
 * Validation is skipped if the `at_hash` claim is absent from the ID token.
 * Only the RS256 algorithm is supported.
 */
class DefaultAccessTokenValidator : AccessTokenValidator {
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun validate(
        accessToken: String,
        idToken: Jwt,
    ) {
        if (idToken.algorithm != "RS256") {
            throw AccessTokenValidator.Error("Unsupported algorithm")
        }
        val expectedAccessTokenHash =
            idToken.deserializeClaims(IdTokenAtHash.serializer()).atHash ?: return
        val sha256 = MessageDigest.getInstance("SHA-256").digest(accessToken.toByteArray(Charsets.US_ASCII))
        val leftMost = sha256.copyOfRange(0, sha256.size / 2)
        val actualAccessTokenHash = Base64.UrlSafe.encode(leftMost).trimEnd('=')
        if (actualAccessTokenHash != expectedAccessTokenHash) {
            throw AccessTokenValidator.Error("ID Token at_hash didn't match the access token.")
        }
    }
}

@Serializable
private class IdTokenAtHash(
    @SerialName("at_hash") val atHash: String? = null,
)
