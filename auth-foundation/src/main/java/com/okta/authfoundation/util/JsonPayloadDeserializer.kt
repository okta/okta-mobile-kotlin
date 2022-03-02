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

import com.okta.authfoundation.client.OidcConfiguration
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.CoroutineContext

internal class JsonPayloadDeserializer(
    private val payload: JsonElement,
    private val json: Json,
    private val dispatcher: CoroutineContext,
) {
    companion object {
        fun OidcConfiguration.createJsonPayloadDeserializer(payload: JsonObject): JsonPayloadDeserializer {
            return JsonPayloadDeserializer(payload, json, computeDispatcher)
        }
    }

    suspend fun <T> payload(deserializationStrategy: DeserializationStrategy<T>): T {
        return withContext(dispatcher) {
            json.decodeFromJsonElement(deserializationStrategy, payload)
        }
    }
}
