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
import com.okta.testhelpers.OktaRule
import org.junit.Rule
import org.junit.Test

class JwksTest {
    @get:Rule val oktaRule = OktaRule()

    @Test fun testDeserializingDefault() {
        val json = """
        {
            "keys": [
                {
                    "kty": "RSA",
                    "alg": "RS256",
                    "kid": "p014K-d3IwPWLc0od5LHM1s1u0YDqX4LIl1xg6ik3j4",
                    "use": "sig",
                    "e": "AQAB",
                    "n": "yUPh0wNqXh1CMSxzud4uHkfBKkNX7powR4cRS_i0VxkbiicbNZ0IQhw-enDhZieRti4NhygOJfN8DPmtHsWJxt_pCsibc--bNgylcESpn9K4OxtiQrjUvtRM4WX3PWsKUREDZ0Vp-WAXC2nibvqRP_Ky38DkZMinzvCLabr0IOzyGc9AJrUHib61X6FucSoLM_YrKi2hd2UUHqeGiZrmUcHCrgrxcJIBTSbJq47hZrFzFN5RDq0Ium-lm8DU3bfoSlyc7minHlCWcOd90LtjonIHYqUVlpRYUzj_n4AM7DPKI6DDxC0-hio37qxfdmV_5Zvo6fpxIe8EUbI-oUoS3Q"
                }
            ]
        }
        """.trimIndent()
        val jwks = oktaRule.configuration.json.decodeFromString(SerializableJwks.serializer(), json).toJwks()
        assertThat(jwks.keys).hasSize(1)
        val key = jwks.keys.first()
        assertThat(key.keyType).isEqualTo("RSA")
        assertThat(key.algorithm).isEqualTo("RS256")
        assertThat(key.keyId).isEqualTo("p014K-d3IwPWLc0od5LHM1s1u0YDqX4LIl1xg6ik3j4")
        assertThat(key.use).isEqualTo("sig")
        assertThat(key.exponent).isEqualTo("AQAB")
        assertThat(key.modulus).isEqualTo("yUPh0wNqXh1CMSxzud4uHkfBKkNX7powR4cRS_i0VxkbiicbNZ0IQhw-enDhZieRti4NhygOJfN8DPmtHsWJxt_pCsibc--bNgylcESpn9K4OxtiQrjUvtRM4WX3PWsKUREDZ0Vp-WAXC2nibvqRP_Ky38DkZMinzvCLabr0IOzyGc9AJrUHib61X6FucSoLM_YrKi2hd2UUHqeGiZrmUcHCrgrxcJIBTSbJq47hZrFzFN5RDq0Ium-lm8DU3bfoSlyc7minHlCWcOd90LtjonIHYqUVlpRYUzj_n4AM7DPKI6DDxC0-hio37qxfdmV_5Zvo6fpxIe8EUbI-oUoS3Q")
    }

    @Test fun testDeserializingRsaWithoutAlgDefaultsToRs256() {
        val json = """
        {
            "keys": [
                {
                    "kty": "RSA",
                    "kid": "p014K-d3IwPWLc0od5LHM1s1u0YDqX4LIl1xg6ik3j4",
                    "use": "sig",
                    "e": "AQAB",
                    "n": "yUPh0wNqXh1CMSxzud4uHkfBKkNX7powR4cRS_i0VxkbiicbNZ0IQhw-enDhZieRti4NhygOJfN8DPmtHsWJxt_pCsibc--bNgylcESpn9K4OxtiQrjUvtRM4WX3PWsKUREDZ0Vp-WAXC2nibvqRP_Ky38DkZMinzvCLabr0IOzyGc9AJrUHib61X6FucSoLM_YrKi2hd2UUHqeGiZrmUcHCrgrxcJIBTSbJq47hZrFzFN5RDq0Ium-lm8DU3bfoSlyc7minHlCWcOd90LtjonIHYqUVlpRYUzj_n4AM7DPKI6DDxC0-hio37qxfdmV_5Zvo6fpxIe8EUbI-oUoS3Q"
                }
            ]
        }
        """.trimIndent()
        val jwks = oktaRule.configuration.json.decodeFromString(SerializableJwks.serializer(), json).toJwks()
        assertThat(jwks.keys).hasSize(1)
        val key = jwks.keys.first()
        assertThat(key.keyType).isEqualTo("RSA")
        assertThat(key.algorithm).isEqualTo("RS256")
        assertThat(key.keyId).isEqualTo("p014K-d3IwPWLc0od5LHM1s1u0YDqX4LIl1xg6ik3j4")
        assertThat(key.use).isEqualTo("sig")
        assertThat(key.exponent).isEqualTo("AQAB")
        assertThat(key.modulus).isEqualTo("yUPh0wNqXh1CMSxzud4uHkfBKkNX7powR4cRS_i0VxkbiicbNZ0IQhw-enDhZieRti4NhygOJfN8DPmtHsWJxt_pCsibc--bNgylcESpn9K4OxtiQrjUvtRM4WX3PWsKUREDZ0Vp-WAXC2nibvqRP_Ky38DkZMinzvCLabr0IOzyGc9AJrUHib61X6FucSoLM_YrKi2hd2UUHqeGiZrmUcHCrgrxcJIBTSbJq47hZrFzFN5RDq0Ium-lm8DU3bfoSlyc7minHlCWcOd90LtjonIHYqUVlpRYUzj_n4AM7DPKI6DDxC0-hio37qxfdmV_5Zvo6fpxIe8EUbI-oUoS3Q")
    }
}
