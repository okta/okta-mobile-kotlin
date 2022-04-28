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

package com.okta.authfoundation.client

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

/**
 * Represents the routes used to make network calls to the Authorization Server.
 *
 * See [OIDC Endpoints](https://developer.okta.com/docs/reference/api/oidc/#endpoints)
 */
class OidcEndpoints(
    val issuer: HttpUrl,
    val authorizationEndpoint: HttpUrl?,
    val tokenEndpoint: HttpUrl,
    val userInfoEndpoint: HttpUrl?,
    val jwksUri: HttpUrl?,
    val introspectionEndpoint: HttpUrl?,
    val revocationEndpoint: HttpUrl?,
    val endSessionEndpoint: HttpUrl?,
    val deviceAuthorizationEndpoint: HttpUrl?,
)

@Serializable
internal class SerializableOidcEndpoints(
    @SerialName("issuer") val issuer: HttpUrl,
    @SerialName("authorization_endpoint") val authorizationEndpoint: HttpUrl? = null,
    @SerialName("token_endpoint") val tokenEndpoint: HttpUrl,
    @SerialName("userinfo_endpoint") val userInfoEndpoint: HttpUrl? = null,
    @SerialName("jwks_uri") val jwksUri: HttpUrl? = null,
    @SerialName("introspection_endpoint") val introspectionEndpoint: HttpUrl? = null,
    @SerialName("revocation_endpoint") val revocationEndpoint: HttpUrl? = null,
    @SerialName("end_session_endpoint") val endSessionEndpoint: HttpUrl? = null,
    @SerialName("device_authorization_endpoint") val deviceAuthorizationEndpoint: HttpUrl? = null,
) {
    fun asOidcEndpoints(): OidcEndpoints {
        return OidcEndpoints(
            issuer = issuer,
            authorizationEndpoint = authorizationEndpoint,
            tokenEndpoint = tokenEndpoint,
            userInfoEndpoint = userInfoEndpoint,
            jwksUri = jwksUri,
            introspectionEndpoint = introspectionEndpoint,
            revocationEndpoint = revocationEndpoint,
            endSessionEndpoint = endSessionEndpoint,
            deviceAuthorizationEndpoint = deviceAuthorizationEndpoint,
        )
    }
}

internal object HttpUrlSerializer : KSerializer<HttpUrl> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("HttpUrl", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): HttpUrl {
        return decoder.decodeString().toHttpUrl()
    }

    override fun serialize(encoder: Encoder, value: HttpUrl) {
        encoder.encodeString(value.toString())
    }
}
