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

import com.okta.authfoundation.claims.ClaimsProvider
import com.okta.authfoundation.claims.DefaultClaimsProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean

/**
 * Token introspection response per [RFC 7662](https://datatracker.ietf.org/doc/html/rfc7662).
 *
 * Callers should check [active] first. When active, claims such as `scope`, `client_id`,
 * `username`, `exp`, etc. are accessible via the [ClaimsProvider] delegation on [Active].
 */
sealed class IntrospectInfo(
    /** `true` if the introspected token is currently active, `false` if it has been revoked or expired. */
    val active: Boolean,
) {
    /**
     * An active token with claims returned by the Authorization Server.
     *
     * Delegates to [ClaimsProvider] for convenient claim access, e.g.:
     * ```kotlin
     * val info = credential.introspectToken(TokenType.ACCESS_TOKEN).getOrThrow()
     * if (info is IntrospectInfo.Active) {
     *     val scope: String? = info.deserializeClaim("scope", String.serializer())
     * }
     * ```
     */
    class Active internal constructor(
        claimsProvider: ClaimsProvider,
    ) : IntrospectInfo(true),
        ClaimsProvider by claimsProvider

    /** An inactive (revoked or expired) token. No claims are available. */
    object Inactive : IntrospectInfo(false)

    internal companion object {
        internal fun fromJsonObject(
            claims: JsonObject,
            json: Json,
        ): IntrospectInfo {
            val active = (claims["active"] as? JsonPrimitive)?.boolean ?: false
            return if (active) {
                Active(DefaultClaimsProvider(claims, json))
            } else {
                Inactive
            }
        }
    }
}
