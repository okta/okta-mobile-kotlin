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
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test

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

        assertThat(response.challengeType, equalTo("oob"))
        assertThat(response.oobCode, equalTo("123456"))
        assertThat(response.channel, equalTo("sms"))
        assertThat(response.bindingMethod, equalTo("prompt"))
        assertThat(response.bindingCode, equalTo("abc"))
        assertThat(response.expiresIn, equalTo(300))
        assertThat(response.interval, equalTo(60))
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

        assertThat(response.challengeType, equalTo("oob"))
        assertThat(response.oobCode, nullValue())
        assertThat(response.channel, nullValue())
        assertThat(response.bindingMethod, nullValue())
        assertThat(response.bindingCode, nullValue())
        assertThat(response.expiresIn, nullValue())
        assertThat(response.interval, nullValue())
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

        assertThat(response.challengeType, equalTo("oob"))
        assertThat(response.oobCode, equalTo("123456"))
        assertThat(response.channel, nullValue())
        assertThat(response.bindingMethod, nullValue())
        assertThat(response.bindingCode, nullValue())
        assertThat(response.expiresIn, equalTo(300))
        assertThat(response.interval, nullValue())
    }
}
