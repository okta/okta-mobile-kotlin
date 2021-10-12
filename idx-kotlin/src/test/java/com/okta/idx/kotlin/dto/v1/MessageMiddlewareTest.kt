/*
 * Copyright 2021-Present Okta, Inc.
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
package com.okta.idx.kotlin.dto.v1

import com.google.common.truth.Truth.assertThat
import com.okta.idx.kotlin.dto.IdxMessage
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test

class MessageMiddlewareTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun testDeserialization() {
        val messageJson = """
        {
          "message": "Authentication failed",
          "i18n": {
            "key": "errors.E0000004"
          },
          "class": "ERROR"
        }
        """.trimIndent()

        val v1Message = json.decodeFromString<Message>(messageJson)
        assertThat(v1Message.message).isEqualTo("Authentication failed")
        assertThat(v1Message.type).isEqualTo("ERROR")
        assertThat(v1Message.i18n?.key).isEqualTo("errors.E0000004")
    }

    @Test fun testMiddleware() {
        val messageJson = messageJson("ERROR")

        val v1Message = json.decodeFromString<Message>(messageJson)
        val message = v1Message.toIdxMessage()
        assertThat(message.message).isEqualTo("Authentication failed")
        assertThat(message.localizationKey).isEqualTo("errors.E0000004")
        assertThat(message.type).isEqualTo(IdxMessage.Severity.ERROR)
    }

    @Test fun testInfoClass() {
        val messageJson = messageJson("INFO")

        val v1Message = json.decodeFromString<Message>(messageJson)
        val message = v1Message.toIdxMessage()
        assertThat(message.message).isEqualTo("Authentication failed")
        assertThat(message.localizationKey).isEqualTo("errors.E0000004")
        assertThat(message.type).isEqualTo(IdxMessage.Severity.INFO)
    }

    @Test fun testMissingClass() {
        val messageJson = messageJson("nothere")
        val v1Message = json.decodeFromString<Message>(messageJson)
        val message = v1Message.toIdxMessage()
        assertThat(message.message).isEqualTo("Authentication failed")
        assertThat(message.localizationKey).isEqualTo("errors.E0000004")
        assertThat(message.type).isEqualTo(IdxMessage.Severity.UNKNOWN)
    }

    private fun messageJson(type: String): String {
        return """
        {
          "message": "Authentication failed",
          "i18n": {
            "key": "errors.E0000004"
          },
          "class": "$type"
        }
        """.trimIndent()
    }
}
