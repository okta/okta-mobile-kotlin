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
@file:UseSerializers(HttpUrlSerializer::class)

package com.okta.oidc.kotlin.client

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

// TODO: Documentation
@Serializable
// TODO: Move these into another class that maps to this.
class OidcEndpoints(
    @SerialName("issuer") internal val issuer: HttpUrl,
    @SerialName("authorization_endpoint") public val authorizationEndpoint: HttpUrl,
    @SerialName("token_endpoint") public val tokenEndpoint: HttpUrl,
    @SerialName("userinfo_endpoint") internal val userInfoEndpoint: HttpUrl,
    @SerialName("jwks_uri") internal val jwksUri: HttpUrl,
    @SerialName("registration_endpoint") internal val registrationEndpoint: HttpUrl,
    @SerialName("introspection_endpoint") internal val introspectionEndpoint: HttpUrl,
    @SerialName("revocation_endpoint") internal val revocationEndpoint: HttpUrl,
    @SerialName("end_session_endpoint") internal val endSessionEndpoint: HttpUrl,
)

internal object HttpUrlSerializer : KSerializer<HttpUrl> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("HttpUrl", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): HttpUrl {
        return decoder.decodeString().toHttpUrl()
    }

    override fun serialize(encoder: Encoder, value: HttpUrl) {
        encoder.encodeString(value.toString())
    }
}
