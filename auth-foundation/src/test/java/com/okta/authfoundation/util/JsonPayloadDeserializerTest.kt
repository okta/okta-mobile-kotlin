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
package com.okta.authfoundation.util

import com.google.common.truth.Truth.assertThat
import com.okta.testhelpers.OktaRule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Rule
import org.junit.Test
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertFailsWith

class JsonPayloadDeserializerTest {
    @get:Rule val oktaRule = OktaRule()

    private val json: Json = oktaRule.configuration.json
    private val dispatcher: CoroutineContext = oktaRule.configuration.computeDispatcher

    @Test fun testPayload(): Unit = runBlocking {
        val payload: JsonElement = json.decodeFromString("""{"sub":"foobar"}""")
        val subject = JsonPayloadDeserializer(payload, json, dispatcher)
        val result = subject.payload(ExampleUserInfo.serializer())
        assertThat(result.sub).isEqualTo("foobar")
    }

    @Test fun testWithDifferentType(): Unit = runBlocking {
        val payload: JsonElement = json.decodeFromString("""{"missing":"foobar"}""")
        val subject = JsonPayloadDeserializer(payload, json, dispatcher)
        assertFailsWith<SerializationException> {
            subject.payload(ExampleUserInfo.serializer())
        }
    }
}

@Serializable
private class ExampleUserInfo(
    @SerialName("sub") val sub: String
)
