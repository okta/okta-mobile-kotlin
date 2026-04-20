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
package com.okta.authfoundation.credential

import com.okta.authfoundation.claims.ClaimsProvider
import com.okta.authfoundation.claims.DefaultClaimsProvider
import com.okta.authfoundation.jwt.Jwt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Non-sensitive information about a token to be used in storage.
 */
data class TokenMetadata(
    /** Storage id of the token. */
    val id: String,
    /** A user-specified map of values when storing the token. */
    val tags: Map<String, String>,
    /** The object holding claim values. Use [claimsProvider] for a more convenient way of accessing claims. */
    val payloadData: JsonObject?,
    /** The JSON serializer used for deserializing claims. */
    val json: Json = Json { ignoreUnknownKeys = true },
) {
    constructor(
        id: String,
        tags: Map<String, String>,
        idToken: Jwt?,
        json: Json = Json { ignoreUnknownKeys = true },
    ) : this(id, tags, idToken?.deserializeClaims(JsonObject.serializer()), json)

    /** Convenience object for accessing claims of this token. */
    val claimsProvider: ClaimsProvider? =
        payloadData?.let {
            DefaultClaimsProvider(payloadData, json)
        }
}
