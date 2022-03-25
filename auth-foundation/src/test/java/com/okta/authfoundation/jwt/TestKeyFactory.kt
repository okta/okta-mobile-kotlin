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

import okio.ByteString.Companion.decodeBase64
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

object TestKeyFactory {
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

    private val publicKeyString = """
    -----BEGIN PUBLIC KEY-----
    MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA2xcCZycepeC6tbbIldJ6
    d2qMN/absNkv84h9NA/UzOlrbBil3ZlhZ/1471fOSQ3tJjT+6OcOIH1Wp3JvOurz
    puoGrKRyHJfkPD6jNoGb+5Cm2nCM5k4BJjK4pS/X6fkNhYZO62V1jR8rVNQtuE+O
    AGjDX6QqfhBFZsimScfBF1oA4wmdTIHdfmywweT0uQoGmm0Kymnc8A4Rn3Grp6rb
    mMm9crlF3xC+Aglb4kHb5LRngyPvvP1HcI5vNph7Do09t/6Lm+Wc59ZLKhgeJLXE
    hCZOeUxMo048R/vLkNtq5SUK9glQ3vlOZHl4ldhvCShKuBtyFimkqnkL6ARjYDsM
    +QIDAQAB
    -----END PUBLIC KEY-----
    """.trimIndent()

    fun privateKey(): PrivateKey {
        val privateKeyBytes = privateKeyString.replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("\n", "")
            .decodeBase64()?.toByteArray() ?: throw IllegalStateException("Invalid private key.")

        val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(keySpec)
    }

    fun publicKey(): PublicKey {
        val publicKeyBytes = publicKeyString.replace("-----END PUBLIC KEY-----", "")
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("\n", "")
            .decodeBase64()?.toByteArray() ?: throw IllegalStateException("Invalid public key.")

        val keySpec = X509EncodedKeySpec(publicKeyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(keySpec)
    }
}
