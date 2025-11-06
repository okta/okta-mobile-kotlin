package com.okta.directauth.http.model

import kotlinx.serialization.json.Json
import org.junit.Test
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue

class DirectAuthResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `test ChallengeResponse deserialization with all properties`() {
        val jsonString = """
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
        val jsonString = """
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
        val jsonString = """
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
