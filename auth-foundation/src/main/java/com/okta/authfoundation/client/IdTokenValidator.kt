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

import com.okta.authfoundation.client.events.ValidateIdTokenEvent
import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.jwt.Jwt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.math.abs

/**
 * Used for validating Id Tokens minted by an Authorization Server.
 */
fun interface IdTokenValidator {
    /**
     * An error used for describing errors when validating the [Jwt].
     */
    class Error(message: String) : IllegalStateException(message)

    /**
     * The parameters used in [validate].
     */
    class Parameters internal constructor(
        /**
         * The `nonce` sent with the authorize request, if using Authorization Code Flow and available.
         */
        val nonce: String?,

        /**
         * The `max_age` sent to the authorize request, if using Authorization Code Flow and available.
         */
        val maxAge: Int?,
    )

    /**
     * Called when the [OidcClient] receives a [Token] response.
     *
     * This should throw an [Exception] if the token is invalid.
     *
     * @param oidcClient the [OidcClient] that made the [Token] request.
     * @param idToken the [Jwt] representing the id token from the [Token] response.
     * @param parameters the [Parameters] used to validate the id token.
     */
    suspend fun validate(oidcClient: OidcClient, idToken: Jwt, parameters: Parameters)
}

// https://openid.net/specs/openid-connect-core-1_0.html#IDTokenValidation
internal class DefaultIdTokenValidator : IdTokenValidator {
    override suspend fun validate(oidcClient: OidcClient, idToken: Jwt, parameters: IdTokenValidator.Parameters) {
        val idTokenPayload = idToken.deserializeClaims(IdTokenValidationPayload.serializer())

        val event = ValidateIdTokenEvent(600)
        oidcClient.configuration.eventCoordinator.sendEvent(event)

        if (idTokenPayload.iss.toHttpUrl() != oidcClient.endpointsOrNull()?.issuer) {
            throw IdTokenValidator.Error("Invalid issuer.")
        }
        if (idTokenPayload.aud != oidcClient.configuration.clientId) {
            throw IdTokenValidator.Error("Invalid audience.")
        }
        if (!idTokenPayload.iss.startsWith("https://")) {
            throw IdTokenValidator.Error("Issuer must use HTTPS.")
        }
        if (idToken.algorithm != "RS256") {
            throw IdTokenValidator.Error("Invalid JWT algorithm.")
        }
        if (idTokenPayload.exp < oidcClient.configuration.clock.currentTimeEpochSecond()) {
            throw IdTokenValidator.Error("The current time MUST be before the time represented by the exp Claim.")
        }
        if (abs(idTokenPayload.iat - oidcClient.configuration.clock.currentTimeEpochSecond()) > event.issuedAtGracePeriodInSeconds) {
            throw IdTokenValidator.Error("Issued at time is not within the allowed threshold of now.")
        }
        if (idTokenPayload.nonce != parameters.nonce) {
            throw IdTokenValidator.Error("Nonce mismatch.")
        }
        if (parameters.maxAge != null) {
            val authTime = idTokenPayload.authTime ?: throw IdTokenValidator.Error("Auth time not available.")
            val elapsedTime = idTokenPayload.iat - authTime
            if (elapsedTime < 0 || elapsedTime > parameters.maxAge) {
                throw IdTokenValidator.Error("Max age not satisfied.")
            }
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
    @SerialName("auth_time") val authTime: Int? = null,
)
