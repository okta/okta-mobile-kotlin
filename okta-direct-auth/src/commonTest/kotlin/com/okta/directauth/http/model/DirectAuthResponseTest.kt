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
package com.okta.directauth.http.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class DirectAuthResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun test_ChallengeResponse_deserialization_withAllProperties() {
        val jsonString =
            """
            {
                "challenge_type": "oob",
                "oob_code": "123456",
                "channel": "sms",
                "binding_method": "prompt",
                "binding_code": "abc",
                "expires_in": 300,
                "interval": 60
            }
            """.trimIndent()

        val response = json.decodeFromString(ChallengeResponse.serializer(), jsonString)

        assertEquals("oob", response.challengeType)
        assertEquals("123456", response.oobCode)
        assertEquals("sms", response.channel)
        assertEquals("prompt", response.bindingMethod)
        assertEquals("abc", response.bindingCode)
        assertEquals(300, response.expiresIn)
        assertEquals(60, response.interval)
    }

    @Test
    fun test_ChallengeResponse_deserialization_withOnlyRequiredProperties() {
        val jsonString =
            """
            {
                "challenge_type": "oob"
            }
            """.trimIndent()

        val response = json.decodeFromString(ChallengeResponse.serializer(), jsonString)

        assertEquals("oob", response.challengeType)
        assertNull(response.oobCode)
        assertNull(response.channel)
        assertNull(response.bindingMethod)
        assertNull(response.bindingCode)
        assertNull(response.expiresIn)
        assertNull(response.interval)
    }

    @Test
    fun test_ChallengeResponse_deserialization_withSomeProperties() {
        val jsonString =
            """
            {
                "challenge_type": "oob",
                "oob_code": "123456",
                "expires_in": 300
            }
            """.trimIndent()

        val response = json.decodeFromString(ChallengeResponse.serializer(), jsonString)

        assertEquals("oob", response.challengeType)
        assertEquals("123456", response.oobCode)
        assertNull(response.channel)
        assertNull(response.bindingMethod)
        assertNull(response.bindingCode)
        assertEquals(300, response.expiresIn)
        assertNull(response.interval)
    }

    @Test
    fun test_ChallengeApiResponse_deserializesWebAuthnJson() {
        val jsonString =
            """
            {
                "publicKey": {
                    "challenge": "dGVzdC1jaGFsbGVuZ2U",
                    "rpId": "example.okta.com",
                    "allowCredentials": [
                        {"id": "Y3JlZC0x", "type": "public-key"}
                    ],
                    "userVerification": "preferred"
                }
            }
            """.trimIndent()

        val response = json.decodeFromString<ChallengeApiResponse>(jsonString)
        assertIs<WebAuthnChallengeResponse>(response)
        assertEquals("dGVzdC1jaGFsbGVuZ2U", response.publicKey.challenge)
        assertEquals("example.okta.com", response.publicKey.rpId)
    }

    @Test
    fun test_ChallengeApiResponse_deserializesOtpJson() {
        val jsonString =
            """
            {
                "challenge_type": "http://auth0.com/oauth/grant-type/mfa-otp"
            }
            """.trimIndent()

        val response = json.decodeFromString<ChallengeApiResponse>(jsonString)
        assertIs<ChallengeResponse>(response)
        assertEquals("http://auth0.com/oauth/grant-type/mfa-otp", response.challengeType)
    }

    @Test
    fun test_ApiErrorResponse_deserializesOAuth2Error() {
        val jsonString =
            """
            {
                "error": "mfa_required",
                "error_description": "MFA is required",
                "mfa_token": "abc123"
            }
            """.trimIndent()

        val response = json.decodeFromString<ApiErrorResponse>(jsonString)
        assertIs<DirectAuthenticationErrorResponse>(response)
        assertEquals("mfa_required", response.error)
        assertEquals("MFA is required", response.errorDescription)
        assertEquals("abc123", response.mfaToken)
    }

    @Test
    fun test_ApiErrorResponse_deserializesServerError() {
        val jsonString =
            """
            {
                "errorCode": "E0000011",
                "errorSummary": "Invalid token provided",
                "errorLink": "E0000011",
                "errorId": "oae-123",
                "errorCauses": []
            }
            """.trimIndent()

        val response = json.decodeFromString<ApiErrorResponse>(jsonString)
        assertIs<ErrorResponse>(response)
        assertEquals("E0000011", response.errorCode)
        assertEquals("Invalid token provided", response.errorSummary)
    }

    @Test
    fun test_ApiErrorResponse_throwsOnUnrecognizedStructure() {
        val jsonString =
            """
            {
                "unexpected": "value"
            }
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<ApiErrorResponse>(jsonString)
        }
    }
}
