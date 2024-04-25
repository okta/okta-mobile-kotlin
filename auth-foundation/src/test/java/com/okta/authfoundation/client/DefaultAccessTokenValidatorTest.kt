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
import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.credential.createToken
import com.okta.authfoundation.jwt.IdTokenClaims
import com.okta.authfoundation.jwt.JwtBuilder.Companion.createJwtBuilder
import com.okta.authfoundation.jwt.JwtParser
import com.okta.testhelpers.OktaRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFailsWith

class DefaultAccessTokenValidatorTest {
    private val accessTokenValidator: AccessTokenValidator = DefaultAccessTokenValidator()
    @get:Rule val oktaRule = OktaRule(accessTokenValidator = accessTokenValidator)

    private suspend fun validateToken(token: Token) {
        val parser = JwtParser(oktaRule.configuration.json, oktaRule.configuration.computeDispatcher)
        val idToken = parser.parse(token.idToken!!)

        accessTokenValidator.validate(
            client = oktaRule.createOAuth2Client(),
            accessToken = token.accessToken,
            idToken = idToken,
        )
    }

    @Test fun `validate a valid token`(): Unit = runBlocking {
        val accessToken = "exampleAccessToken"
        val idTokenClaims = IdTokenClaims(accessTokenHash = "W10Ltiz7WJ_3pGuNwc1DXg")
        val idToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = idTokenClaims)
        val token = createToken(accessToken = accessToken, idToken = idToken.rawValue)
        validateToken(token)
    }

    @Test fun `validate token when id doesn't have at_hash`(): Unit = runBlocking {
        val accessToken = "exampleAccessToken"
        val idTokenClaims = IdTokenClaims(accessTokenHash = null)
        val idToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = idTokenClaims)
        val token = createToken(accessToken = accessToken, idToken = idToken.rawValue)
        validateToken(token)
    }

    @Test fun `validate an invalid token`(): Unit = runBlocking {
        val accessToken = "exampleAccessToken"
        val idTokenClaims = IdTokenClaims(accessTokenHash = "mismatch!")
        val idToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = idTokenClaims)
        val token = createToken(accessToken = accessToken, idToken = idToken.rawValue)
        val exception = assertFailsWith<AccessTokenValidator.Error> {
            validateToken(token)
        }
        assertThat(exception).hasMessageThat().isEqualTo("ID Token at_hash didn't match the access token.")
    }

    @Test fun `validate invalid id token algorithm throws`(): Unit = runBlocking {
        val accessToken = "exampleAccessToken"
        val idTokenClaims = IdTokenClaims(accessTokenHash = "W10Ltiz7WJ_3pGuNwc1DXg")
        val idToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(algorithm = "RS512", claims = idTokenClaims)
        val token = createToken(accessToken = accessToken, idToken = idToken.rawValue)
        val exception = assertFailsWith<AccessTokenValidator.Error> {
            validateToken(token)
        }
        assertThat(exception).hasMessageThat().isEqualTo("Unsupported algorithm")
    }
}
