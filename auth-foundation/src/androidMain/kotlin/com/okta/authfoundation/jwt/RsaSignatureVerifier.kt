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
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.RSAPublicKeySpec

private val logger = getDefaultAuthFoundationLogger()

internal actual fun verifyRs256Signature(
    modulus: ByteArray,
    exponent: ByteArray,
    data: ByteArray,
    signature: ByteArray,
): Boolean =
    try {
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(RSAPublicKeySpec(BigInteger(1, modulus), BigInteger(1, exponent)))
        val rs256Signature = Signature.getInstance("SHA256withRSA")
        rs256Signature.initVerify(publicKey)
        rs256Signature.update(data)
        rs256Signature.verify(signature)
    } catch (e: Exception) {
        runCatching { logger.write("RSA signature verification failed", tr = e, logLevel = LogLevel.WARN) }
        false
    }
