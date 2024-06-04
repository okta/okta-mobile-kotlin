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
import com.okta.authfoundation.client.events.ValidateIdTokenEvent
import com.okta.authfoundation.events.Event
import com.okta.authfoundation.events.EventHandler
import com.okta.authfoundation.jwt.IdTokenClaims
import com.okta.authfoundation.jwt.JwtBuilder.Companion.createJwtBuilder
import com.okta.testhelpers.OktaRule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Rule
import org.junit.Test
import kotlin.test.fail

class DefaultIdTokenValidatorWithCustomizedGracePeriodTest {
    private val idTokenValidator: IdTokenValidator = DefaultIdTokenValidator()
    @get:Rule val oktaRule = OktaRule(idTokenValidator = idTokenValidator)

    init {
        oktaRule.eventHandler.registerEventHandler(UpdateIssuedAtGracePeriodEventHandler())
    }

    @Test fun testValidIdToken(): Unit = runBlocking {
        val client = oktaRule.createOAuth2Client(oktaRule.createEndpoints("https://example-test.okta.com".toHttpUrl().newBuilder()))
        val idTokenClaims = IdTokenClaims()
        val idToken = client.createJwtBuilder().createJwt(claims = idTokenClaims)
        idTokenValidator.validate(client, idToken, IdTokenValidator.Parameters(null, null))
    }

    @Test fun testIssuedAtTooFarBeforeNow() {
        val idTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime - 1801)
        assertFailsWithMessage("Issued at time is not within the allowed threshold of now.", idTokenClaims)
    }

    @Test fun testIssuedAtTooFarAfterNow() {
        val idTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime + 1801)
        assertFailsWithMessage("Issued at time is not within the allowed threshold of now.", idTokenClaims)
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
            val client = oktaRule.createOAuth2Client(
                oktaRule.createEndpoints("$issuerPrefix://example-test.okta.com".toHttpUrl().newBuilder())
            )
            runBlocking {
                idTokenValidator.validate(
                    client,
                    client.createJwtBuilder().createJwt(algorithm = algorithm, claims = idTokenClaims),
                    IdTokenValidator.Parameters(nonce, maxAge),
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

private class UpdateIssuedAtGracePeriodEventHandler : EventHandler {
    override fun onEvent(event: Event) {
        if (event is ValidateIdTokenEvent) {
            event.issuedAtGracePeriodInSeconds = 30 * 60
        }
    }
}
