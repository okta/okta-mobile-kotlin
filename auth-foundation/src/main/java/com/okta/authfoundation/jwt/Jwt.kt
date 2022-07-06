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

import com.okta.authfoundation.claims.ClaimsProvider
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.decodeBase64
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import kotlin.coroutines.CoroutineContext

/**
 * Represents a Json Web Token.
 */
class Jwt internal constructor(
    /** Identifies the digital signature algorithm used. */
    val algorithm: String,
    /** Identifies the public key used to verify the ID token. */
    val keyId: String,

    claimsProvider: ClaimsProvider,

    /**
     * The base64 encoded signature.
     */
    val signature: String,

    /**
     * The raw value in standard JWT format.
     */
    val rawValue: String,

    private val computeDispatcher: CoroutineContext,
) : ClaimsProvider by claimsProvider {
    override fun equals(other: Any?): Boolean {
        if (other is Jwt) {
            return other.rawValue == rawValue
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return rawValue.hashCode()
    }

    override fun toString(): String {
        return rawValue
    }

    /**
     * Validates the [Jwt.signature] against the [Jwks].
     *
     * @param jwks the [Jwks] to validate the [Jwt] against.
     */
    suspend fun hasValidSignature(jwks: Jwks): Boolean {
        return withContext(computeDispatcher) {
            val key = jwks.keys.firstOrNull { it.keyId == keyId } ?: return@withContext false
            if (key.use != "sig") return@withContext false
            if (key.keyType != "RSA") return@withContext false
            if (key.algorithm != "RS256") return@withContext false

            val modulus = BigInteger(1, key.modulus?.decodeBase64()?.toByteArray() ?: return@withContext false)
            val exponent = BigInteger(1, key.exponent?.decodeBase64()?.toByteArray() ?: return@withContext false)

            val jwtContentBytes = rawValue.substringBeforeLast('.').toByteArray()

            try {
                val keyFactory = KeyFactory.getInstance("RSA")
                val publicKey = keyFactory.generatePublic(RSAPublicKeySpec(modulus, exponent))

                val rs256Signature = Signature.getInstance("SHA256withRSA")
                rs256Signature.initVerify(publicKey)
                rs256Signature.update(jwtContentBytes)
                return@withContext rs256Signature.verify(signature.decodeBase64()?.toByteArray() ?: return@withContext false)
            } catch (_: Exception) {
                return@withContext false
            }
        }
    }
}
