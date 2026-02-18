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
import kotlin.test.assertNull

class DirectAuthResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `test ChallengeResponse deserialization with all properties`() {
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
    fun `test ChallengeResponse deserialization with only required properties`() {
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
    fun `test ChallengeResponse deserialization with some properties`() {
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
}
