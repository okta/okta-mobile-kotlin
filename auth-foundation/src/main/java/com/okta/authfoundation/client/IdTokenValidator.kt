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
package com.okta.authfoundation.client

import com.okta.authfoundation.jwt.Jwt
import com.okta.authfoundation.credential.Token
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Used for validating Id Tokens minted by an Authorization Server.
 */
fun interface IdTokenValidator {
    /**
     * Called when the [OidcClient] receives a [Token] response.
     *
     * @param oidcClient the [OidcClient] that made the [Token] request.
     * @param idToken the [Jwt] representing the id token from the [Token] response.
     * @param nonce the nonce sent with the authorize request, if using Authorization Code Flow and available.
     */
    suspend fun validate(oidcClient: OidcClient, idToken: Jwt, nonce: String?)
}

// https://openid.net/specs/openid-connect-core-1_0.html#IDTokenValidation
internal class DefaultIdTokenValidator : IdTokenValidator {
    override suspend fun validate(oidcClient: OidcClient, idToken: Jwt, nonce: String?) {
        val idTokenPayload = idToken.deserializeClaims(IdTokenValidationPayload.serializer())

        if (idTokenPayload.iss != oidcClient.endpointsOrNull()?.issuer.toString()) {
            throw IllegalStateException("Invalid issuer.")
        }
        if (idTokenPayload.aud != oidcClient.configuration.clientId) {
            throw IllegalStateException("Invalid audience.")
        }
        if (!idTokenPayload.iss.startsWith("https://")) {
            throw IllegalStateException("Issuer must use HTTPS.")
        }
        if (idToken.algorithm != "RS256") {
            throw IllegalStateException("Invalid JWT algorithm.")
        }
        if (idTokenPayload.exp < oidcClient.configuration.clock.currentTimeMillis()) {
            throw IllegalStateException("The current time MUST be before the time represented by the exp Claim.")
        }
        if (abs(idTokenPayload.iat - oidcClient.configuration.clock.currentTimeMillis()) > 600) {
            throw IllegalStateException("Issued at time is not within the allowed threshold of now.")
        }
        if (idTokenPayload.nonce != nonce) {
            throw IllegalStateException("Nonce mismatch.")
        }
    }
}

@Serializable
internal class IdTokenValidationPayload(
    @SerialName("iss") val iss: String,
    @SerialName("aud") val aud: String,
    @SerialName("exp") val exp: Int,
    @SerialName("iat") val iat: Int,
    @SerialName("nonce") val nonce: String? = null,
    @SerialName("auth_time") val auth_time: Int,
)
