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
package com.okta.authfoundation.jwt

import com.okta.authfoundation.client.OAuth2Client
import com.okta.authfoundation.client.OidcConfiguration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okio.ByteString.Companion.toByteString
import java.security.PrivateKey
import java.security.Signature

class JwtBuilder private constructor(
    private val jwtParser: JwtParser,
    private val privateKey: PrivateKey,
) {
    companion object {
        const val KEY_ID = "FJA0HGNtsuuda_Pl45J42kvQqcsu_0C4Fg7pbJLXTHY"

        fun OAuth2Client.createJwtBuilder(): JwtBuilder {
            return JwtBuilder(JwtParser(configuration.json, configuration.computeDispatcher), TestKeyFactory.privateKey())
        }
    }

    val json = Json(from = OidcConfiguration.defaultJson()) {
        encodeDefaults = true
    }

    suspend inline fun <reified T> createJwt(
        algorithm: String = "RS256",
        keyId: String = KEY_ID,
        claims: T,
    ): Jwt {
        val serializer = json.serializersModule.serializer<T>()
        return createJwt(algorithm, keyId, claims, serializer)
    }

    suspend fun <T> createJwt(
        algorithm: String,
        keyId: String,
        claims: T,
        serializer: SerializationStrategy<T>,
    ): Jwt {
        val headerString = json.encodeToString(Header(algorithm = algorithm, keyId = keyId)).toBase64()
        val claimsString = json.encodeToString(serializer, claims).toBase64()
        val signatureString = "$headerString.$claimsString".rs256()
        val rawJwt = "$headerString.$claimsString.$signatureString"
        return jwtParser.parse(rawJwt)
    }

    private fun String.rs256(): String {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(toByteArray(Charsets.US_ASCII))
        val signedBytes = signature.sign()
        return signedBytes.toByteString().base64Url().trimEnd('=')
    }

    private fun String.toBase64(): String {
        return toByteArray(Charsets.US_ASCII).toByteString().base64Url().trimEnd('=')
    }
}

@Serializable
private class Header(
    @SerialName("alg") val algorithm: String,
    @SerialName("kid") val keyId: String,
    @SerialName("typ") val type: String = "JWT",
)

@Serializable
class IdTokenClaims(
    @SerialName("sub") val subject: String? = "00ub41z7mgzNqryMv696",
    @SerialName("name") val name: String? = "Jay Newstrom",
    @SerialName("email") val email: String? = "jaynewstrom@example.com",
    @SerialName("ver") val version: Int? = 1,
    @SerialName("iss") val issuer: String? = "https://example-test.okta.com/oauth2/default",
    @SerialName("aud") val audience: String? = "unit_test_client_id",
    @SerialName("iat") val issuedAt: Long? = 1644347069,
    @SerialName("exp") val expiresAt: Long? = 1644350669,
    @SerialName("jti") val jwtId: String? = "ID.55cxBtdYl8l6arKISPBwd0yOT-9UCTaXaQTXt2laRLs",
    @SerialName("amr") val authenticationMethods: List<String>? = listOf("pwd"),
    @SerialName("idp") val identityProvider: String? = "00o8fou7sRaGGwdn4696",
    @SerialName("sid") val sessionId: String? = "idxWxklp_4kSxuC_nU1pXD-nA",
    @SerialName("preferred_username") val preferredUsername: String? = "jaynewstrom@example.com",
    @SerialName("auth_time") val authTime: Long? = 1644347068,
    @SerialName("at_hash") val accessTokenHash: String? = "gMcGTbhGT1G_ldsHoJsPzQ",
    @SerialName("ds_hash") val deviceSecretHash: String? = "DAeLOFRqifysbgsrbOgbog",
    @SerialName("nonce") val nonce: String? = null,
)
