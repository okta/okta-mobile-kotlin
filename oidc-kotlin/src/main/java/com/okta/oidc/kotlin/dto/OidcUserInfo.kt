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
package com.okta.oidc.kotlin.dto

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class OidcUserInfo internal constructor(
    private val json: JsonObject
) {
    fun getString(key: String): String? {
        return (json[key] as? JsonPrimitive)?.content
    }

    // TODO: Need to come up with a better data structure here.
    fun asMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (entry in json) {
            val value = entry.value
            if (value is JsonPrimitive) {
                map[entry.key] = value.content
            } else {
                map[entry.key] = value.toString()
            }
        }
        return map
    }
}
