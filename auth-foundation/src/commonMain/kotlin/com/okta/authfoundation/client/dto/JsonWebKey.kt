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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A class representing a JSON Web Key, as defined by RFC 7517.
 *
 * https://datatracker.ietf.org/doc/html/rfc7517
 */
@Serializable
data class JsonWebKey(
    // Required per RFC 7517
    val kty: String,
    // Optional but common
    val use: String? = null,
    val alg: String? = null,
    val kid: String? = null,
    val x5c: List<String>? = null,
    val x5t: String? = null,
    // RSA specific parameters
    val n: String? = null,
    val e: String? = null,
    // EC specific parameters
    val crv: String? = null,
    val x: String? = null,
    val y: String? = null,
    // Catch-all for any other vendor-specific parameters
    val additionalProperties: JsonObject? = null,
)
