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

/**
 * The JSON Web Key Set.
 *
 * Expected to be retrieved via [OidcClient.jwks] and used to validate a [Jwt] signature via [Jwt.hasValidSignature].
 */
class Jwks internal constructor(
    internal val keys: List<Key>
) {
    internal class Key(
        val algorithm: String,
        val exponent: String?,
        val modulus: String?,
        val keyId: String,
        val keyType: String,
        val use: String,
    ) {
        internal fun toSerializableJwksKey(): SerializableJwks.Key {
            return SerializableJwks.Key(
                algorithm = algorithm,
                exponent = exponent,
                modulus = modulus,
                keyId = keyId,
                keyType = keyType,
                use = use,
            )
        }
    }

    internal fun toSerializableJwks(): SerializableJwks {
        return SerializableJwks(keys.map { it.toSerializableJwksKey() })
    }
}

@Serializable
internal class SerializableJwks(@SerialName("keys") val keys: List<Key>) {
    @Serializable
    class Key(
        @SerialName("alg") val algorithm: String,
        @SerialName("e") val exponent: String?,
        @SerialName("n") val modulus: String?,
        @SerialName("kid") val keyId: String,
        @SerialName("kty") val keyType: String,
        @SerialName("use") val use: String,
    ) {
        fun toJwksKey(): Jwks.Key {
            return Jwks.Key(
                algorithm = algorithm,
                exponent = exponent,
                modulus = modulus,
                keyId = keyId,
                keyType = keyType,
                use = use,
            )
        }
    }

    fun toJwks(): Jwks {
        return Jwks(keys.map { it.toJwksKey() })
    }
}
