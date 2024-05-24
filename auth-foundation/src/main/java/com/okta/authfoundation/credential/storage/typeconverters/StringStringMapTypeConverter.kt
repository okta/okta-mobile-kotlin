/*
 * Copyright 2024-Present Okta, Inc.
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
package com.okta.authfoundation.credential.storage.typeconverters

import androidx.room.TypeConverter
import com.okta.authfoundation.client.OidcConfiguration
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

class StringStringMapTypeConverter {
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    @TypeConverter
    fun convertToJsonString(stringStringMap: Map<String, String>?): String? {
        return stringStringMap?.let {
            OidcConfiguration.defaultJson().encodeToString(mapSerializer, it)
        }
    }

    @TypeConverter
    fun convertToObject(json: String?): Map<String, String>? {
        return json?.let {
            OidcConfiguration.defaultJson().decodeFromString(mapSerializer, it)
        }
    }
}
