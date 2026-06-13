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

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.jwt.JwtBuilder.Companion.createJwtBuilder
import com.okta.testhelpers.OktaRule
import kotlinx.coroutines.runBlocking
import okio.ByteString.Companion.toByteString
import org.junit.Rule
import org.junit.Test
import java.security.KeyPair
import java.security.Signature
import java.security.interfaces.ECPublicKey

class JwtTest {
    @get:Rule val oktaRule = OktaRule()

    @Test fun testHasValidSignature(): Unit =
        runBlocking {
            val jwks = createJwks()
            val jwt = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
            assertThat(jwt.hasValidSignature(jwks)).isTrue()
        }

    @Test fun testHasValidSignatureWithMissingKey(): Unit =
        runBlocking {
            val jwks = Jwks(keys = listOf())
            val jwt = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
            assertThat(jwt.hasValidSignature(jwks)).isFalse()
        }

    @Test fun testHasValidSignatureWithDifferentKeyId(): Unit =
        runBlocking {
            val jwks = createJwks(keyId = "Different")
            val jwt = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
            assertThat(jwt.hasValidSignature(jwks)).isFalse()
        }

    @Test fun testHasValidSignatureWithInvalidAlgorithm(): Unit =
        runBlocking {
            val jwks = createJwks(algorithm = "RS512")
            val jwt = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
            assertThat(jwt.hasValidSignature(jwks)).isFalse()
        }

    @Test fun testHasValidSignatureWithNullAlgorithm(): Unit =
        runBlocking {
            val jwks = createJwks(algorithm = null)
            val jwt = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
            assertThat(jwt.hasValidSignature(jwks)).isFalse()
        }

    @Test fun testHasValidSignatureWithInvalidUse(): Unit =
        runBlocking {
            val jwks = createJwks(use = "enc")
            val jwt = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
            assertThat(jwt.hasValidSignature(jwks)).isFalse()
        }

    @Test fun testHasValidSignatureWithInvalidKeyType(): Unit =
        runBlocking {
            val jwks = createJwks(keyType = "HMAC")
            val jwt = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
            assertThat(jwt.hasValidSignature(jwks)).isFalse()
        }

    @Test fun testHasValidSignatureWithInvalidSignature(): Unit =
        runBlocking {
            val jwks = createJwks()
            val client = oktaRule.createOAuth2Client()
            val jwt = client.createJwtBuilder().createJwt(claims = IdTokenClaims())
            val jwtParser = JwtParser(oktaRule.configuration.json, oktaRule.configuration.computeDispatcher)
            val invalidJwt = jwtParser.parse("${jwt.rawValue.substringBeforeLast(".")}.invalid")
            assertThat(invalidJwt.hasValidSignature(jwks)).isFalse()
        }

    @Test fun testEqualsReturnsTrueSameInstance(): Unit =
        runBlocking {
            val jwt = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
            assertThat(jwt).isEqualTo(jwt)
        }

    @Test fun testEqualsReturnsTrueSameValues(): Unit =
        runBlocking {
            val jwt1 = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
            val jwt2 = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
            assertThat(jwt1).isEqualTo(jwt2)
        }

    @Test fun testEqualsReturnsFalseDifferentValues(): Unit =
        runBlocking {
            val jwt1 = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims(subject = "Different"))
            val jwt2 = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
            assertThat(jwt1).isNotEqualTo(jwt2)
        }

    @Test fun testEqualsReturnsFalseNonJwt(): Unit =
        runBlocking {
            val jwt = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
            assertThat(jwt).isNotEqualTo("Different!")
        }

    // ── EC dispatch tests ──────────────────────────────────────────────────────

    @Test fun hasValidSignature_EcKey_Es256_ReturnsTrue(): Unit =
        runBlocking {
            val keyPair = TestKeyFactory.createEcKeyPair("P-256")
            val jwks = createEcJwks(keyPair, crv = "P-256", keyId = "ec-test-key")
            val jwt =
                createEcJwt(
                    keyPair = keyPair,
                    keyId = "ec-test-key",
                    algorithm = "ES256",
                    jcaAlg = "SHA256withECDSA",
                    parser = JwtParser(oktaRule.configuration.json, oktaRule.configuration.computeDispatcher)
                )
            assertThat(jwt.hasValidSignature(jwks)).isTrue()
        }

    @Test fun hasValidSignature_EcKey_WrongKeyId_ReturnsFalse(): Unit =
        runBlocking {
            val keyPair = TestKeyFactory.createEcKeyPair("P-256")
            // JWKS has "other-key-id" but JWT header says "ec-test-key"
            val jwks = createEcJwks(keyPair, crv = "P-256", keyId = "other-key-id")
            val jwt =
                createEcJwt(
                    keyPair = keyPair,
                    keyId = "ec-test-key",
                    algorithm = "ES256",
                    jcaAlg = "SHA256withECDSA",
                    parser = JwtParser(oktaRule.configuration.json, oktaRule.configuration.computeDispatcher)
                )
            assertThat(jwt.hasValidSignature(jwks)).isFalse()
        }

    @Test fun hasValidSignature_MixedJwks_RsaKeyStillWorks(): Unit =
        runBlocking {
            // A JWKS containing both an RSA key and an EC key — RSA JWT should still verify correctly.
            val rsaJwks = createJwks()
            val ecKeyPair = TestKeyFactory.createEcKeyPair("P-256")
            val ecJwks = createEcJwks(ecKeyPair, crv = "P-256", keyId = "ec-key")
            val mixedJwks = Jwks(keys = rsaJwks.keys + ecJwks.keys)

            val rsaJwt = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
            assertThat(rsaJwt.hasValidSignature(mixedJwks)).isTrue()
        }
}

// ── EC JWT test helper ─────────────────────────────────────────────────────────

private fun createEcJwt(
    keyPair: KeyPair,
    keyId: String,
    algorithm: String,
    jcaAlg: String,
    parser: JwtParser,
): Jwt {
    val ecPublicKey = keyPair.public as ECPublicKey
    val coordByteLen = (ecPublicKey.params.order.bitLength() + 7) / 8

    fun String.toBase64(): String = toByteArray(Charsets.US_ASCII).toByteString().base64Url().trimEnd('=')

    val header = """{"alg":"$algorithm","kid":"$keyId","typ":"JWT"}""".toBase64()
    val claims = """{"sub":"test","iss":"https://example.okta.com","aud":"client","iat":1700000000,"exp":9999999999}""".toBase64()
    val signingInput = "$header.$claims"

    val derSig =
        Signature.getInstance(jcaAlg).run {
            initSign(keyPair.private)
            update(signingInput.toByteArray(Charsets.US_ASCII))
            sign()
        }

    // Convert DER → P1363 for JWT (IETF JWS spec requires P1363/raw format)
    val p1363Sig = derToP1363(derSig, coordByteLen)
    val sigEncoded = p1363Sig.toByteString().base64Url().trimEnd('=')

    return parser.parse("$signingInput.$sigEncoded")
}

/** Converts a DER ECDSA signature to P1363 (raw r‖s) format for JWT encoding. */
private fun derToP1363(
    der: ByteArray,
    coordByteLen: Int,
): ByteArray {
    var pos = 1 // skip 0x30
    val lenByte = der[pos++].toInt() and 0xFF
    if (lenByte and 0x80 != 0) pos += lenByte and 0x7F

    pos++ // skip 0x02
    val rLen = der[pos++].toInt() and 0xFF
    val rBytes = der.copyOfRange(pos, pos + rLen)
    pos += rLen

    pos++ // skip 0x02
    val sLen = der[pos++].toInt() and 0xFF
    val sBytes = der.copyOfRange(pos, pos + sLen)

    val p1363 = ByteArray(coordByteLen * 2)
    rBytes.toCoordBytes(coordByteLen).copyInto(p1363, 0)
    sBytes.toCoordBytes(coordByteLen).copyInto(p1363, coordByteLen)
    return p1363
}

private fun ByteArray.toCoordBytes(coordByteLen: Int): ByteArray =
    when {
        size == coordByteLen -> this
        size > coordByteLen -> copyOfRange(size - coordByteLen, size)
        else -> ByteArray(coordByteLen - size) + this
    }
