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
package com.okta.authfoundation.client.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

object AudienceSerializer : KSerializer<List<String>?> {
    // We define the descriptor as a STRING to allow flexibility,
    // though we are manually handling the JSON structure.
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Audience", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): List<String>? {
        // Ensure we are using the JSON format
        val jsonInput =
            decoder as? JsonDecoder
                ?: throw IllegalStateException("Can be deserialized only by JSON")

        return when (val element = jsonInput.decodeJsonElement()) {
            is JsonPrimitive -> listOf(element.content)
            is JsonArray -> element.map { it.jsonPrimitive.content }
            else -> null
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: List<String>?,
    ) {
        val jsonEncoder =
            encoder as? JsonEncoder
                ?: throw IllegalStateException("Can be serialized only by JSON")

        if (value == null) {
            jsonEncoder.encodeJsonElement(JsonNull)
            return
        }

        when {
            value.isEmpty() -> {
                jsonEncoder.encodeJsonElement(JsonNull)
            }

            value.size == 1 -> {
                jsonEncoder.encodeString(value.first())
            }

            else -> {
                val jsonArray = JsonArray(value.map { JsonPrimitive(it) })
                jsonEncoder.encodeJsonElement(jsonArray)
            }
        }
    }
}
