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
package com.okta.authfoundation.client

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.jwt.IdTokenClaims
import com.okta.authfoundation.jwt.JwtBuilder.Companion.createJwtBuilder
import com.okta.testhelpers.OktaRule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Rule
import org.junit.Test
import kotlin.test.fail

class DefaultIdTokenValidatorTest {
    private val idTokenValidator: IdTokenValidator = DefaultIdTokenValidator()
    @get:Rule val oktaRule = OktaRule(idTokenValidator = idTokenValidator)

    @Test fun testValidIdToken(): Unit = runBlocking {
        val client = oktaRule.createOidcClient(oktaRule.createEndpoints("https://example-test.okta.com".toHttpUrl().newBuilder()))
        val idTokenClaims = IdTokenClaims()
        val idToken = client.createJwtBuilder().createJwt(claims = idTokenClaims)
        idTokenValidator.validate(client, idToken, null, null)
    }

    @Test fun testValidIdTokenWithNonce(): Unit = runBlocking {
        val client = oktaRule.createOidcClient(oktaRule.createEndpoints("https://example-test.okta.com".toHttpUrl().newBuilder()))
        val idTokenClaims = IdTokenClaims(nonce = "6ccdb66d-ad56-4072-b864-7a8fe73c0ac2")
        val idToken = client.createJwtBuilder().createJwt(claims = idTokenClaims)
        idTokenValidator.validate(client, idToken, "6ccdb66d-ad56-4072-b864-7a8fe73c0ac2", null)
    }

    @Test fun testValidIdTokenWithNonceAndMaxAge(): Unit = runBlocking {
        val client = oktaRule.createOidcClient(oktaRule.createEndpoints("https://example-test.okta.com".toHttpUrl().newBuilder()))
        val idTokenClaims = IdTokenClaims(nonce = "6ccdb66d-ad56-4072-b864-7a8fe73c0ac2")
        val idToken = client.createJwtBuilder().createJwt(claims = idTokenClaims)
        idTokenValidator.validate(client, idToken, "6ccdb66d-ad56-4072-b864-7a8fe73c0ac2", 300)
    }

    @Test fun testInvalidIssuer() {
        val idTokenClaims = IdTokenClaims(issuer = "https://invalid-test.okta.com/oauth2/default")
        assertFailsWithMessage("Invalid issuer.", idTokenClaims)
    }

    @Test fun testInvalidAudience() {
        val idTokenClaims = IdTokenClaims(audience = "mismatch")
        assertFailsWithMessage("Invalid audience.", idTokenClaims)
    }

    @Test fun testInvalidHttps() {
        val idTokenClaims = IdTokenClaims(issuer = "http://example-test.okta.com/oauth2/default")
        assertFailsWithMessage("Issuer must use HTTPS.", idTokenClaims, "http")
    }

    @Test fun testInvalidAlgorithm() {
        val idTokenClaims = IdTokenClaims()
        assertFailsWithMessage("Invalid JWT algorithm.", idTokenClaims, algorithm = "RS328")
    }

    @Test fun testCurrentTimeAfterExpires() {
        val idTokenClaims = IdTokenClaims(expiresAt = oktaRule.clock.currentTime - 1)
        assertFailsWithMessage("The current time MUST be before the time represented by the exp Claim.", idTokenClaims)
    }

    @Test fun testIssuedAtTooFarBeforeNow() {
        val idTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime - 601)
        assertFailsWithMessage("Issued at time is not within the allowed threshold of now.", idTokenClaims)
    }

    @Test fun testIssuedAtTooFarAfterNow() {
        val idTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime + 601)
        assertFailsWithMessage("Issued at time is not within the allowed threshold of now.", idTokenClaims)
    }

    @Test fun testMalformedPayload() {
        val idTokenClaims = IdTokenClaims(issuer = null)
        assertFailsWithMessage(
            "Unexpected 'null' when string was expected",
            idTokenClaims
        )
    }

    @Test fun testMismatchNonce() {
        val idTokenClaims = IdTokenClaims(nonce = "6ccdb66d-ad56-4072-b864-7a8fe73c0ac2")
        assertFailsWithMessage(
            "Nonce mismatch.",
            idTokenClaims,
            nonce = "mismatch!"
        )
    }

    @Test fun testNullNonce() {
        val idTokenClaims = IdTokenClaims(nonce = "6ccdb66d-ad56-4072-b864-7a8fe73c0ac2")
        assertFailsWithMessage(
            "Nonce mismatch.",
            idTokenClaims,
            nonce = null
        )
    }

    @Test fun testNullAuthTime() {
        val idTokenClaims = IdTokenClaims(authTime = null)
        assertFailsWithMessage(
            "Auth time not available.",
            idTokenClaims,
            maxAge = 300,
        )
    }

    @Test fun testNegativeMaxAge() {
        val issuedAt = IdTokenClaims().issuedAt!!
        val idTokenClaims = IdTokenClaims(issuedAt = issuedAt, authTime = issuedAt + 1)
        assertFailsWithMessage(
            "Max age not satisfied.",
            idTokenClaims,
            maxAge = 300,
        )
    }

    @Test fun testOutsideOfRangeMaxAge() {
        val issuedAt = IdTokenClaims().issuedAt!!
        val idTokenClaims = IdTokenClaims(issuedAt = issuedAt, authTime = issuedAt - 301)
        assertFailsWithMessage(
            "Max age not satisfied.",
            idTokenClaims,
            maxAge = 300,
        )
    }

    private fun assertFailsWithMessage(
        message: String,
        idTokenClaims: IdTokenClaims,
        issuerPrefix: String = "https",
        nonce: String? = null,
        maxAge: Int? = null,
        algorithm: String = "RS256",
    ) {
        try {
            val client = oktaRule.createOidcClient(
                oktaRule.createEndpoints("$issuerPrefix://example-test.okta.com".toHttpUrl().newBuilder())
            )
            runBlocking {
                idTokenValidator.validate(
                    client,
                    client.createJwtBuilder().createJwt(algorithm = algorithm, claims = idTokenClaims),
                    nonce,
                    maxAge,
                )
            }
            fail()
        } catch (e: IdTokenValidator.Error) {
            assertThat(e).hasMessageThat().isEqualTo(message)
        } catch (e: SerializationException) {
            assertThat(e).hasMessageThat().isEqualTo(message)
        }
    }
}
