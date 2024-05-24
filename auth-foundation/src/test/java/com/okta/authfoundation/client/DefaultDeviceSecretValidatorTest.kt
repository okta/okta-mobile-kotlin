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

class DefaultDeviceSecretValidatorTest {
    private val deviceSecretValidator: DeviceSecretValidator = DefaultDeviceSecretValidator()
    @get:Rule val oktaRule = OktaRule(deviceSecretValidator = deviceSecretValidator)

    private suspend fun validateToken(token: Token) {
        val parser = JwtParser(oktaRule.configuration.json, oktaRule.configuration.computeDispatcher)
        val idToken = parser.parse(token.idToken!!)

        deviceSecretValidator.validate(
            client = oktaRule.createOAuth2Client(),
            deviceSecret = token.deviceSecret!!,
            idToken = idToken,
        )
    }

    @Test fun `validate a valid token`(): Unit = runBlocking {
        val deviceSecret = "exampleDeviceSecret"
        val idTokenClaims = IdTokenClaims(deviceSecretHash = "5PuHaT28DJPEaOveu9ZCmg")
        val idToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = idTokenClaims)
        val token = createToken(deviceSecret = deviceSecret, idToken = idToken.rawValue)
        validateToken(token)
    }

    @Test fun `validate token when id doesn't have dt_hash`(): Unit = runBlocking {
        val deviceSecret = "exampleDeviceSecret"
        val idTokenClaims = IdTokenClaims(deviceSecretHash = null)
        val idToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = idTokenClaims)
        val token = createToken(deviceSecret = deviceSecret, idToken = idToken.rawValue)
        validateToken(token)
    }

    @Test fun `validate an invalid token`(): Unit = runBlocking {
        val deviceSecret = "exampleDeviceSecret"
        val idTokenClaims = IdTokenClaims(deviceSecretHash = "mismatch!")
        val idToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = idTokenClaims)
        val token = createToken(deviceSecret = deviceSecret, idToken = idToken.rawValue)
        val exception = assertFailsWith<DeviceSecretValidator.Error> {
            validateToken(token)
        }
        assertThat(exception).hasMessageThat().isEqualTo("ID Token ds_hash didn't match the device secret.")
    }

    @Test fun `validate invalid id token algorithm throws`(): Unit = runBlocking {
        val deviceSecret = "exampleDeviceSecret"
        val idTokenClaims = IdTokenClaims(deviceSecretHash = "5PuHaT28DJPEaOveu9ZCmg")
        val idToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(algorithm = "RS512", claims = idTokenClaims)
        val token = createToken(deviceSecret = deviceSecret, idToken = idToken.rawValue)
        val exception = assertFailsWith<DeviceSecretValidator.Error> {
            validateToken(token)
        }
        assertThat(exception).hasMessageThat().isEqualTo("Unsupported algorithm")
    }
}
