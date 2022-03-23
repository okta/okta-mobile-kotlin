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

import com.okta.authfoundation.client.OidcClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

class JwtBuilder private constructor(
    private val oidcClient: OidcClient,
    private val privateKey: PrivateKey,
) {
    companion object {
        private val privateKeyString = """
        -----BEGIN PRIVATE KEY-----
        MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDbFwJnJx6l4Lq1
        tsiV0np3aow39puw2S/ziH00D9TM6WtsGKXdmWFn/XjvV85JDe0mNP7o5w4gfVan
        cm866vOm6gaspHIcl+Q8PqM2gZv7kKbacIzmTgEmMrilL9fp+Q2Fhk7rZXWNHytU
        1C24T44AaMNfpCp+EEVmyKZJx8EXWgDjCZ1Mgd1+bLDB5PS5CgaabQrKadzwDhGf
        caunqtuYyb1yuUXfEL4CCVviQdvktGeDI++8/Udwjm82mHsOjT23/oub5Zzn1ksq
        GB4ktcSEJk55TEyjTjxH+8uQ22rlJQr2CVDe+U5keXiV2G8JKEq4G3IWKaSqeQvo
        BGNgOwz5AgMBAAECggEAZHXZiTk76W3xz08ADQsVUtqNbz/qRh5gyXfFiXDU8Bz8
        P/XRYJprOsbUhFMr6P20x3c3h84jASzX5jIn5MlFbj0TUGibVpcjdah3KJAn2SOM
        Ds/bG+OazUwmtMAKbmPgGmDqoS/Fxi8LrHsad9Aq2e8v3xQk0+dcG3RYI66v0KeA
        Rdq1GQ4DsFyICwWqhbjz0gBx45QGn5U9PPmXrpQpXR//HdUQWmYqth/549Udpt+A
        2S5QSpeK+7kZKzGK1RyOO+Guw4gku1V8NrqKEhCexgGneEXyYc35v1Sie0Zhtn6H
        4yg5zmBMi/KZZFSqEHv46dXgckPJDo0Gb8lUV/JgXQKBgQDz5iQE8zfEkUH3f+Oi
        vuiNzNlBucZEfAvgke+NDu9PNV6wJdUlU21CteDcRBeJIcGrVs8CxgZRIx6JDon3
        sBacykf5JvjZDEbvNs3h6/nuAtMx7PtRFRsyP3swC5KqRDP8Uq6PfE4aE5FsPHah
        97u+FnpI826OyNf/gnwMNXb/pwKBgQDl9cIbIdkJkFFr3iza/bAFj3bOIi4aD/KC
        itJwK/K+FbtQg/sijU3KIOKVjAdVDZ1WADG2lMcftdoav9brPgdgxPWuyeJYIfmX
        1gei9luEQkO0k36CpBkpqT3HWdSoXAoBce+JlyCARssW+zSl1Dc8J8lD2X4FBQqQ
        IjxTX7IiXwKBgQC7iE5TvAs6ShI10pDeNvoq5cJ7BfPL/rFHOA7AICajeb7XpA9S
        huYw8BX4ZybNmzYFn1bGpCqBQoadDZ/J4gxQ/DwA+BVJFmaIUlRVjRL8DhIDhlrq
        ylbB+QuoMo3P+2cZcR2lWAfZhwg+9/KjsQ8bJr9ZzktI4GcsoFDvNkDMawKBgG9X
        0yg391J+Ii5MYQOXmcbXc/rS6eeMmStD9CiD3wDSnOObQ9my+VtJGOy35ET2Vpvx
        dCCnYNKlxnj1Mias3f2o4BxFe+aYbLVr2D67cgxT2Vxxneu7cMOPQm5nvGPYTK/u
        bsD7/6ycmnECKLeyTRw/V2AWysG7cyXerb7gsuuZAoGBAKt6NNVpxqmHB6/Wdqsi
        sct6YBeZSLPuK/PtK5anVwTYlN7Omg2BjS+/yCygNuDp+px9ykHwYicSgDsJxyOt
        1Spw+uMVoNG6yvIE3q7mjhDoHDu5lUA8s6uzzcTsvDnSLR6uNV2iqDzR/Osu3+bp
        QjRCFXd0Tr5PlKpAHb6dk+aI
        -----END PRIVATE KEY-----
        """.trimIndent()

        fun OidcClient.createJwtBuilder(): JwtBuilder {
            val privateKeyBytes = privateKeyString.replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("\n", "")
                .decodeBase64()?.toByteArray() ?: throw IllegalStateException("Invalid private key.")

            val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKey = keyFactory.generatePrivate(keySpec)

            return JwtBuilder(this, privateKey)
        }
    }

    val json = Json(from = oidcClient.configuration.json) {
        encodeDefaults = true
    }

    suspend inline fun <reified T> createJwt(
        algorithm: String = "RS256",
        keyId: String = "FJA0HGNtsuuda_Pl45J42kvQqcsu_0C4Fg7pbJLXTHY",
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
        return oidcClient.parseJwt(rawJwt)!!
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
