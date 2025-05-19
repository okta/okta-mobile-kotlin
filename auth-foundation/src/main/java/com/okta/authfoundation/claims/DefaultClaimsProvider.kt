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
package com.okta.authfoundation.claims

import com.okta.authfoundation.client.OidcConfiguration
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal class DefaultClaimsProvider(
    private val claims: JsonObject,
    private val json: Json,
) : ClaimsProvider {
    companion object {
        fun OidcConfiguration.createClaimsDeserializer(claims: JsonObject): DefaultClaimsProvider = DefaultClaimsProvider(claims, json)
    }

    override fun <T> deserializeClaims(deserializationStrategy: DeserializationStrategy<T>): T = json.decodeFromJsonElement(deserializationStrategy, claims)

    override fun availableClaims(): Set<String> = claims.keys

    override fun <T> deserializeClaim(
        claim: String,
        deserializationStrategy: DeserializationStrategy<T>,
    ): T? {
        val element = claims[claim] ?: return null
        return json.decodeFromJsonElement(deserializationStrategy, element)
    }
}
