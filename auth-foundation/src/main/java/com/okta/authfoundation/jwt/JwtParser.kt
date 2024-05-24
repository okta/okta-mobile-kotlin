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
package com.okta.authfoundation.jwt

import com.okta.authfoundation.AuthFoundationDefaults
import com.okta.authfoundation.claims.DefaultClaimsProvider
import com.okta.authfoundation.client.OidcConfiguration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okio.ByteString.Companion.decodeBase64
import kotlin.coroutines.CoroutineContext

/**
 * Used to parse [String]s into [Jwt]s.
 */
class JwtParser internal constructor(
    private val json: Json,
    private val computeDispatcher: CoroutineContext,
) {
    companion object {
        /**
         * Initialize a [JwtParser].
         */
        fun create(): JwtParser {
            return JwtParser(OidcConfiguration.defaultJson(), AuthFoundationDefaults.computeDispatcher)
        }
    }

    /**
     * Parses a given string into a [Jwt], if possible.
     *
     * @param rawValue the string representation of a [Jwt].
     * @throws IllegalArgumentException if [rawValue] is malformed.
     */
    fun parse(rawValue: String): Jwt {
        val sections = rawValue.split(".")
        if (sections.size != 3) {
            throw IllegalArgumentException("Token doesn't contain 3 parts. Needs header, claims data, and signature.")
        }
        val headerString = sections[0].decodeBase64()?.utf8() ?: throw IllegalArgumentException("Header isn't valid base64.")
        val header = json.decodeFromString<JwtHeader>(headerString)

        val claimsString = sections[1].decodeBase64()?.utf8() ?: throw IllegalArgumentException("Claims aren't valid base64.")
        val claims = json.decodeFromString<JsonObject>(claimsString)

        return Jwt(
            algorithm = header.alg,
            keyId = header.kid,
            claimsProvider = DefaultClaimsProvider(claims, json),
            signature = sections[2],
            rawValue = rawValue,
            computeDispatcher = computeDispatcher,
        )
    }
}

@Serializable
private class JwtHeader(
    @SerialName("alg") val alg: String,
    @SerialName("kid") val kid: String,
)
