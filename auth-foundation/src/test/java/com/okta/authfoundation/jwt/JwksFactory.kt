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
import java.security.interfaces.RSAPublicKey

fun createJwks(
    algorithm: String? = "RS256",
    keyId: String = JwtBuilder.KEY_ID,
    keyType: String = "RSA",
    use: String = "sig",
    publicKey: RSAPublicKey = TestKeyFactory.publicKey() as RSAPublicKey,
): Jwks {
    return Jwks(
        keys = listOf(
            Jwks.Key(
                algorithm = algorithm,
                exponent = publicKey.publicExponent.toByteArray().toByteString().base64Url(),
                modulus = publicKey.modulus.toByteArray().toByteString().base64Url(),
                keyId = keyId,
                keyType = keyType,
                use = use,
            )
        )
    )
}
