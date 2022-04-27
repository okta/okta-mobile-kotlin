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
package com.okta.authfoundation.client.dto

import com.okta.authfoundation.claims.ClaimsProvider
import com.okta.authfoundation.claims.DefaultClaimsProvider.Companion.createClaimsDeserializer
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.credential.Token
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean

/**
 * Introspect Info.
 *
 * This provides a convenience mechanism for accessing information related to a [Token] introspection.
 */
sealed class OidcIntrospectInfo(
    /**
     * True if the introspected [Token] is active.
     */
    val active: Boolean,
) {
    internal companion object {
        internal fun JsonObject.asOidcIntrospectInfo(configuration: OidcConfiguration): OidcIntrospectInfo {
            val active = (get("active") as JsonPrimitive).boolean
            if (active) {
                return Active(configuration.createClaimsDeserializer(this))
            } else {
                return Inactive
            }
        }
    }

    /**
     * A model representing an active introspection, which includes the claims returned by the Authorization Server.
     */
    class Active internal constructor(
        claimsProvider: ClaimsProvider
    ) : OidcIntrospectInfo(true), ClaimsProvider by claimsProvider

    internal object Inactive : OidcIntrospectInfo(false)
}
