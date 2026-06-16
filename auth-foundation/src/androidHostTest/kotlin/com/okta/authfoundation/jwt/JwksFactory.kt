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

import okio.ByteString.Companion.toByteString
import java.security.KeyPair
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey

fun createJwks(
    algorithm: String? = "RS256",
    keyId: String = JwtBuilder.KEY_ID,
    keyType: String = "RSA",
    use: String = "sig",
    publicKey: RSAPublicKey = TestKeyFactory.publicKey() as RSAPublicKey,
): Jwks =
    Jwks(
        keys =
            listOf(
                Jwks.Key(
                    algorithm = algorithm,
                    exponent =
                        publicKey.publicExponent
                            .toByteArray()
                            .toByteString()
                            .base64Url(),
                    modulus =
                        publicKey.modulus
                            .toByteArray()
                            .toByteString()
                            .base64Url(),
                    keyId = keyId,
                    keyType = keyType,
                    use = use
                )
            )
    )

/**
 * Creates a [Jwks] containing a single EC key backed by [keyPair].
 *
 * The x and y coordinates are extracted from the public key and base64url-encoded per RFC 7517.
 *
 * @param keyPair the EC key pair (public key must be an [ECPublicKey]).
 * @param crv the JWK curve name: `"P-256"`, `"P-384"`, or `"P-521"`.
 * @param keyId the key ID to embed in the JWKS entry.
 */
fun createEcJwks(
    keyPair: KeyPair,
    crv: String,
    keyId: String = "ec-test-key",
): Jwks {
    val ecPublicKey = keyPair.public as ECPublicKey
    val coordByteLen = (ecPublicKey.params.order.bitLength() + 7) / 8
    val x =
        ecPublicKey.w.affineX
            .toByteArray()
            .toCoordBytes(coordByteLen)
    val y =
        ecPublicKey.w.affineY
            .toByteArray()
            .toCoordBytes(coordByteLen)
    return Jwks(
        keys =
            listOf(
                Jwks.Key(
                    keyId = keyId,
                    use = "sig",
                    keyType = "EC",
                    algorithm = null,
                    exponent = null,
                    modulus = null,
                    x = x.toByteString().base64Url(),
                    y = y.toByteString().base64Url(),
                    crv = crv
                )
            )
    )
}

/**
 * Converts a [BigInteger.toByteArray] result to a fixed-length unsigned coordinate byte array.
 * BigInteger may prepend a 0x00 sign byte; this strips or pads to exactly [coordByteLen] bytes.
 */
private fun ByteArray.toCoordBytes(coordByteLen: Int): ByteArray =
    when {
        size == coordByteLen -> this
        size > coordByteLen -> copyOfRange(size - coordByteLen, size)
        else -> ByteArray(coordByteLen - size) + this
    }
