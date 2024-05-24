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
import org.junit.Rule
import org.junit.Test

class JwtTest {
    @get:Rule val oktaRule = OktaRule()

    @Test fun testHasValidSignature(): Unit = runBlocking {
        val jwks = createJwks()
        val jwt = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
        assertThat(jwt.hasValidSignature(jwks)).isTrue()
    }

    @Test fun testHasValidSignatureWithMissingKey(): Unit = runBlocking {
        val jwks = Jwks(keys = listOf())
        val jwt = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
        assertThat(jwt.hasValidSignature(jwks)).isFalse()
    }

    @Test fun testHasValidSignatureWithDifferentKeyId(): Unit = runBlocking {
        val jwks = createJwks(keyId = "Different")
        val jwt = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
        assertThat(jwt.hasValidSignature(jwks)).isFalse()
    }

    @Test fun testHasValidSignatureWithInvalidAlgorithm(): Unit = runBlocking {
        val jwks = createJwks(algorithm = "RS512")
        val jwt = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
        assertThat(jwt.hasValidSignature(jwks)).isFalse()
    }

    @Test fun testHasValidSignatureWithNullAlgorithm(): Unit = runBlocking {
        val jwks = createJwks(algorithm = null)
        val jwt = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
        assertThat(jwt.hasValidSignature(jwks)).isFalse()
    }

    @Test fun testHasValidSignatureWithInvalidUse(): Unit = runBlocking {
        val jwks = createJwks(use = "enc")
        val jwt = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
        assertThat(jwt.hasValidSignature(jwks)).isFalse()
    }

    @Test fun testHasValidSignatureWithInvalidKeyType(): Unit = runBlocking {
        val jwks = createJwks(keyType = "HMAC")
        val jwt = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
        assertThat(jwt.hasValidSignature(jwks)).isFalse()
    }

    @Test fun testHasValidSignatureWithInvalidSignature(): Unit = runBlocking {
        val jwks = createJwks()
        val client = oktaRule.createOAuth2Client()
        val jwt = client.createJwtBuilder().createJwt(claims = IdTokenClaims())
        val jwtParser = JwtParser.create()
        val invalidJwt = jwtParser.parse("${jwt.rawValue.substringBeforeLast(".")}.invalid")
        assertThat(invalidJwt.hasValidSignature(jwks)).isFalse()
    }

    @Test fun testEqualsReturnsTrueSameInstance(): Unit = runBlocking {
        val jwt = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
        assertThat(jwt).isEqualTo(jwt)
    }

    @Test fun testEqualsReturnsTrueSameValues(): Unit = runBlocking {
        val jwt1 = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
        val jwt2 = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
        assertThat(jwt1).isEqualTo(jwt2)
    }

    @Test fun testEqualsReturnsFalseDifferentValues(): Unit = runBlocking {
        val jwt1 = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims(subject = "Different"))
        val jwt2 = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
        assertThat(jwt1).isNotEqualTo(jwt2)
    }

    @Test fun testEqualsReturnsFalseNonJwt(): Unit = runBlocking {
        val jwt = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims())
        assertThat(jwt).isNotEqualTo("Different!")
    }
}
