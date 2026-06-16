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

import com.okta.authfoundation.api.log.LogLevel
import com.okta.authfoundation.api.log.getDefaultAuthFoundationLogger
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec

private val logger = getDefaultAuthFoundationLogger()

internal actual fun verifyEcSignature(
    x: ByteArray,
    y: ByteArray,
    crv: String,
    data: ByteArray,
    signature: ByteArray,
): Boolean =
    try {
        val curveName =
            when (crv) {
                "P-256" -> "secp256r1"
                "P-384" -> "secp384r1"
                "P-521" -> "secp521r1"
                else -> return false
            }
        val jcaAlg =
            when (crv) {
                "P-256" -> "SHA256withECDSAinP1363Format"
                "P-384" -> "SHA384withECDSAinP1363Format"
                "P-521" -> "SHA512withECDSAinP1363Format"
                else -> return false
            }

        val ecPoint = ECPoint(BigInteger(1, x), BigInteger(1, y))
        val ecParams =
            AlgorithmParameters.getInstance("EC").run {
                init(ECGenParameterSpec(curveName))
                getParameterSpec(ECParameterSpec::class.java)
            }
        val publicKey = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ecPoint, ecParams))

        Signature.getInstance(jcaAlg).run {
            initVerify(publicKey)
            update(data)
            verify(signature)
        }
    } catch (e: Exception) {
        logger.write("EC signature verification failed", tr = e, logLevel = LogLevel.WARN)
        false
    }
