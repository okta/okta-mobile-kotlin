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
package com.okta.authfoundation.credential.kmp.storage.typeconverters

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal class JsonObjectTypeConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun convertToJsonString(jsonObject: JsonObject?): String? =
        jsonObject?.let {
            json.encodeToString(JsonObject.serializer(), it)
        }

    @TypeConverter
    fun convertToObject(jsonString: String?): JsonObject? =
        jsonString?.let {
            json.decodeFromString(JsonObject.serializer(), it)
        }
}
