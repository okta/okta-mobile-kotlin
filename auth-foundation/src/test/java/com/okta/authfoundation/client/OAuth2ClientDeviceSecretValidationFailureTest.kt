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
import com.okta.authfoundation.credential.Credential
import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.credential.createToken
import com.okta.authfoundation.jwt.IdTokenClaims
import com.okta.authfoundation.jwt.JwtBuilder.Companion.createJwtBuilder
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.RequestMatchers.body
import com.okta.testhelpers.RequestMatchers.header
import com.okta.testhelpers.RequestMatchers.method
import com.okta.testhelpers.RequestMatchers.path
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Rule
import org.junit.Test

class OAuth2ClientDeviceSecretValidationFailureTest {
    @get:Rule val oktaRule = OktaRule(deviceSecretValidator = { _, _, _ ->
        throw IllegalStateException("Failure!")
    })

    @Test fun testRefreshTokenWithNoDeviceSecretPassesValidation(): Unit = runBlocking {
        mockkObject(Credential) {
            coEvery { Credential.credentialDataSource() } returns mockk(relaxed = true)
            val client = oktaRule.createOAuth2Client()
            oktaRule.enqueue(
                method("POST"),
                header("content-type", "application/x-www-form-urlencoded"),
                path("/oauth2/default/v1/token"),
                body("client_id=unit_test_client_id&grant_type=refresh_token&refresh_token=ExampleRefreshToken"),
            ) { response ->
                runBlocking {
                    val idTokenClaims = IdTokenClaims(deviceSecretHash = "not matching")
                    val idToken = client.createJwtBuilder().createJwt(claims = idTokenClaims)
                    val token = createToken(
                        deviceSecret = null,
                        idToken = idToken.rawValue
                    ).asSerializableToken()
                    val body = oktaRule.configuration.json.encodeToString(token)
                    response.setBody(body)
                }
            }
            val result = client.refreshToken(createToken(refreshToken = "ExampleRefreshToken"))
            assertThat(result).isInstanceOf(OAuth2ClientResult.Success::class.java)
        }
    }

    @Test fun testRefreshTokenWithDeviceSecretFailsValidation(): Unit = runBlocking {
        val client = oktaRule.createOAuth2Client()
        oktaRule.enqueue(
            method("POST"),
            header("content-type", "application/x-www-form-urlencoded"),
            path("/oauth2/default/v1/token"),
            body("client_id=unit_test_client_id&grant_type=refresh_token&refresh_token=ExampleRefreshToken"),
        ) { response ->
            runBlocking {
                val idTokenClaims = IdTokenClaims(deviceSecretHash = "not matching")
                val idToken = client.createJwtBuilder().createJwt(claims = idTokenClaims)
                val token = createToken(deviceSecret = "exampleDeviceSecret", idToken = idToken.rawValue).asSerializableToken()
                val body = oktaRule.configuration.json.encodeToString(token)
                response.setBody(body)
            }
        }
        val result = client.refreshToken(createToken(refreshToken = "ExampleRefreshToken"))
        val exception = (result as OAuth2ClientResult.Error<Token>).exception
        assertThat(exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(exception).hasMessageThat().isEqualTo("Failure!")
    }
}
