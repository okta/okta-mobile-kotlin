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
        val jwt = oktaRule.createOidcClient().createJwtBuilder().createJwt(claims = IdTokenClaims())
        assertThat(jwt.hasValidSignature(jwks)).isTrue()
    }

    @Test fun testHasValidSignatureWithMissingKey(): Unit = runBlocking {
        val jwks = Jwks(keys = listOf())
        val jwt = oktaRule.createOidcClient().createJwtBuilder().createJwt(claims = IdTokenClaims())
        assertThat(jwt.hasValidSignature(jwks)).isFalse()
    }

    @Test fun testHasValidSignatureWithInvalidAlgorithm(): Unit = runBlocking {
        val jwks = createJwks(algorithm = "RS512")
        val jwt = oktaRule.createOidcClient().createJwtBuilder().createJwt(claims = IdTokenClaims())
        assertThat(jwt.hasValidSignature(jwks)).isFalse()
    }

    @Test fun testHasValidSignatureWithInvalidUse(): Unit = runBlocking {
        val jwks = createJwks(use = "enc")
        val jwt = oktaRule.createOidcClient().createJwtBuilder().createJwt(claims = IdTokenClaims())
        assertThat(jwt.hasValidSignature(jwks)).isFalse()
    }

    @Test fun testHasValidSignatureWithInvalidKeyType(): Unit = runBlocking {
        val jwks = createJwks(keyType = "HMAC")
        val jwt = oktaRule.createOidcClient().createJwtBuilder().createJwt(claims = IdTokenClaims())
        assertThat(jwt.hasValidSignature(jwks)).isFalse()
    }

    @Test fun testHasValidSignatureWithInvalidSignature(): Unit = runBlocking {
        val jwks = createJwks()
        val client = oktaRule.createOidcClient()
        val jwt = client.createJwtBuilder().createJwt(claims = IdTokenClaims())
        val invalidJwt = client.parseJwt("${jwt.rawValue.substringBeforeLast(".")}.invalid")!!
        assertThat(invalidJwt.hasValidSignature(jwks)).isFalse()
    }
}
