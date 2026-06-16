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
import org.junit.Test
import java.security.Signature
import java.security.interfaces.ECPublicKey

class EcSignatureVerifierTest {
    private val testData = "header.payload".toByteArray()

    // Signs [data] with the given private key and returns the signature in P1363 format.
    private fun signP1363(
        data: ByteArray,
        keyPair: java.security.KeyPair,
        jcaAlg: String,
    ): ByteArray {
        val ecPublicKey = keyPair.public as ECPublicKey
        val coordByteLen = (ecPublicKey.params.order.bitLength() + 7) / 8
        val derSig =
            Signature.getInstance(jcaAlg).run {
                initSign(keyPair.private)
                update(data)
                sign()
            }
        return derToP1363(derSig, coordByteLen)
    }

    @Test fun es256_ValidSignature_ReturnsTrue() {
        val keyPair = TestKeyFactory.createEcKeyPair("P-256")
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
        val sig = signP1363(testData, keyPair, "SHA256withECDSA")

        assertThat(verifyEcSignature(x, y, "P-256", testData, sig)).isTrue()
    }

    @Test fun es256_WrongKey_ReturnsFalse() {
        val signingKeyPair = TestKeyFactory.createEcKeyPair("P-256")
        val wrongKeyPair = TestKeyFactory.createEcKeyPair("P-256")
        val wrongPublicKey = wrongKeyPair.public as ECPublicKey
        val coordByteLen = (wrongPublicKey.params.order.bitLength() + 7) / 8
        val x =
            wrongPublicKey.w.affineX
                .toByteArray()
                .toCoordBytes(coordByteLen)
        val y =
            wrongPublicKey.w.affineY
                .toByteArray()
                .toCoordBytes(coordByteLen)
        val sig = signP1363(testData, signingKeyPair, "SHA256withECDSA")

        assertThat(verifyEcSignature(x, y, "P-256", testData, sig)).isFalse()
    }

    @Test fun es384_ValidSignature_ReturnsTrue() {
        val keyPair = TestKeyFactory.createEcKeyPair("P-384")
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
        val sig = signP1363(testData, keyPair, "SHA384withECDSA")

        assertThat(verifyEcSignature(x, y, "P-384", testData, sig)).isTrue()
    }

    @Test fun es384_WrongKey_ReturnsFalse() {
        val signingKeyPair = TestKeyFactory.createEcKeyPair("P-384")
        val wrongKeyPair = TestKeyFactory.createEcKeyPair("P-384")
        val wrongPublicKey = wrongKeyPair.public as ECPublicKey
        val coordByteLen = (wrongPublicKey.params.order.bitLength() + 7) / 8
        val x =
            wrongPublicKey.w.affineX
                .toByteArray()
                .toCoordBytes(coordByteLen)
        val y =
            wrongPublicKey.w.affineY
                .toByteArray()
                .toCoordBytes(coordByteLen)
        val sig = signP1363(testData, signingKeyPair, "SHA384withECDSA")

        assertThat(verifyEcSignature(x, y, "P-384", testData, sig)).isFalse()
    }

    @Test fun es512_ValidSignature_ReturnsTrue() {
        val keyPair = TestKeyFactory.createEcKeyPair("P-521")
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
        val sig = signP1363(testData, keyPair, "SHA512withECDSA")

        assertThat(verifyEcSignature(x, y, "P-521", testData, sig)).isTrue()
    }

    @Test fun es512_WrongKey_ReturnsFalse() {
        val signingKeyPair = TestKeyFactory.createEcKeyPair("P-521")
        val wrongKeyPair = TestKeyFactory.createEcKeyPair("P-521")
        val wrongPublicKey = wrongKeyPair.public as ECPublicKey
        val coordByteLen = (wrongPublicKey.params.order.bitLength() + 7) / 8
        val x =
            wrongPublicKey.w.affineX
                .toByteArray()
                .toCoordBytes(coordByteLen)
        val y =
            wrongPublicKey.w.affineY
                .toByteArray()
                .toCoordBytes(coordByteLen)
        val sig = signP1363(testData, signingKeyPair, "SHA512withECDSA")

        assertThat(verifyEcSignature(x, y, "P-521", testData, sig)).isFalse()
    }

    @Test fun ec_MissingXCoord_ReturnsFalse() {
        val keyPair = TestKeyFactory.createEcKeyPair("P-256")
        val ecPublicKey = keyPair.public as ECPublicKey
        val coordByteLen = (ecPublicKey.params.order.bitLength() + 7) / 8
        val y =
            ecPublicKey.w.affineY
                .toByteArray()
                .toCoordBytes(coordByteLen)
        val sig = signP1363(testData, keyPair, "SHA256withECDSA")

        // Pass all-zero x (not the real key)
        assertThat(verifyEcSignature(ByteArray(coordByteLen), y, "P-256", testData, sig)).isFalse()
    }

    @Test fun ec_MissingYCoord_ReturnsFalse() {
        val keyPair = TestKeyFactory.createEcKeyPair("P-256")
        val ecPublicKey = keyPair.public as ECPublicKey
        val coordByteLen = (ecPublicKey.params.order.bitLength() + 7) / 8
        val x =
            ecPublicKey.w.affineX
                .toByteArray()
                .toCoordBytes(coordByteLen)
        val sig = signP1363(testData, keyPair, "SHA256withECDSA")

        // Pass all-zero y (invalid point)
        assertThat(verifyEcSignature(x, ByteArray(coordByteLen), "P-256", testData, sig)).isFalse()
    }

    @Test fun ec_UnsupportedCurve_ReturnsFalse() {
        val keyPair = TestKeyFactory.createEcKeyPair("P-256")
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
        val sig = signP1363(testData, keyPair, "SHA256withECDSA")

        assertThat(verifyEcSignature(x, y, "P-224", testData, sig)).isFalse()
    }

    @Test fun ec_InvalidBase64Signature_ReturnsFalse() {
        val keyPair = TestKeyFactory.createEcKeyPair("P-256")
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

        assertThat(verifyEcSignature(x, y, "P-256", testData, ByteArray(4) { 0xFF.toByte() })).isFalse()
    }
}

/** Strips/pads a BigInteger byte array to [coordByteLen] bytes for use as EC coordinate. */
private fun ByteArray.toCoordBytes(coordByteLen: Int): ByteArray =
    when {
        size == coordByteLen -> this
        size > coordByteLen -> copyOfRange(size - coordByteLen, size)
        else -> ByteArray(coordByteLen - size) + this
    }

/**
 * Converts a DER-encoded ECDSA signature to P1363 (raw r‖s) format.
 *
 * Used in tests to produce P1363 input from JCA's DER output.
 */
private fun derToP1363(
    der: ByteArray,
    coordByteLen: Int,
): ByteArray {
    // DER: 0x30 <len> [0x81 <len>] 0x02 <r_len> <r_bytes> 0x02 <s_len> <s_bytes>
    var pos = 1 // skip 0x30
    // Sequence length: short or long form
    val lenByte = der[pos++].toInt() and 0xFF
    if (lenByte and 0x80 != 0) {
        // Long form: skip the length bytes (0x81 <byte> for one-byte length)
        pos += lenByte and 0x7F
    }
    // Read r integer
    pos++ // skip 0x02 tag
    val rLen = der[pos++].toInt() and 0xFF
    val rBytes = der.copyOfRange(pos, pos + rLen)
    pos += rLen
    // Read s integer
    pos++ // skip 0x02 tag
    val sLen = der[pos++].toInt() and 0xFF
    val sBytes = der.copyOfRange(pos, pos + sLen)

    // Pad each component to coordByteLen (strip leading 0x00 sign byte if present)
    val p1363 = ByteArray(coordByteLen * 2)
    rBytes.toCoordBytes(coordByteLen).copyInto(p1363, 0)
    sBytes.toCoordBytes(coordByteLen).copyInto(p1363, coordByteLen)
    return p1363
}
