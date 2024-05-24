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

import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.jwt.Jwt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.ByteString.Companion.toByteString

/**
 * Used for validating Access Tokens minted by an Authorization Server.
 */
fun interface AccessTokenValidator {
    /**
     * An error used for describing errors when validating the [Jwt].
     */
    class Error(message: String) : IllegalStateException(message)

    /**
     * Called when the [OAuth2Client] receives a [Token] response.
     *
     * This should throw an [Exception] if the token is invalid.
     *
     * @param client the [OAuth2Client] that made the [Token] request.
     * @param accessToken the access token from the [Token] response.
     * @param idToken the [Jwt] representing the id token from the [Token] response.
     */
    suspend fun validate(client: OAuth2Client, accessToken: String, idToken: Jwt)
}

// https://openid.net/specs/openid-connect-core-1_0.html#ImplicitTokenValidation
internal class DefaultAccessTokenValidator : AccessTokenValidator {
    override suspend fun validate(client: OAuth2Client, accessToken: String, idToken: Jwt) {
        if (idToken.algorithm != "RS256") {
            throw AccessTokenValidator.Error("Unsupported algorithm")
        }
        val expectedAccessTokenHash = idToken.deserializeClaims(IdTokenAtHash.serializer()).atHash ?: return
        val sha256 = accessToken.toByteArray(Charsets.US_ASCII).toByteString().sha256()
        val leftMost = sha256.substring(0, sha256.size / 2)
        val actualAccessTokenHash = leftMost.base64Url().trimEnd('=')
        if (actualAccessTokenHash != expectedAccessTokenHash) {
            throw AccessTokenValidator.Error("ID Token at_hash didn't match the access token.")
        }
    }
}

@Serializable
private class IdTokenAtHash(
    @SerialName("at_hash") val atHash: String? = null,
)
