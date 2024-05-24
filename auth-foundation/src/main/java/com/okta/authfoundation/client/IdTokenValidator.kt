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
    class Error @JvmOverloads constructor(
        /**
         * The detailed message for the error.
         */
        message: String,
        /**
         * The identifier used to match the specific type of error.
         */
        val identifier: String = "",
    ) : IllegalStateException(message) {
        companion object {
            /** The error that's thrown when an ID Token has an issuer that doesn't match the issuer that made the request. */
            const val INVALID_ISSUER = "invalid_issuer"
            /** The error that's thrown when an ID Token has an `aud` that doesn't match the client ID that made the request. */
            const val INVALID_AUDIENCE = "invalid_audience"
            /** The error that's thrown when an ID Token has an issuer that doesn't use HTTPS */
            const val ISSUER_NOT_HTTPS = "issuer_not_https"
            /** The error that's thrown when an ID Token has a [Jwt] signing algorithm that isn't `RS256`. */
            const val INVALID_JWT_ALGORITHM = "invalid_jwt_algorithm"
            /** The error that's thrown when an ID Token has a [Jwt] signature that is invalid. */
            const val INVALID_JWT_SIGNATURE = "invalid_jwt_signature"
            /** The error that's thrown when an ID Token is validated after it has expired. */
            const val EXPIRED = "expired"
            /** The error that's thrown when an ID Token has an `iat` isn't within the specified threshold of the current time. */
            const val ISSUED_AT_THRESHOLD_NOT_SATISFIED = "issued_at_threshold_not_satisfied"
            /** The error that's thrown when an ID Token has a nonce that doesn't match the nonce that made the request. */
            const val NONCE_MISMATCH = "nonce_mismatch"
            /** The error that's thrown when an ID Token has an `auth_time` that isn't within the specified `max_age`. */
            const val MAX_AGE_NOT_SATISFIED = "max_age_not_satisfied"
            /** The error that's thrown when an ID Token doesn't contain a `sub` claim. */
            const val INVALID_SUBJECT = "invalid_subject"
        }
    }

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
     * Called when the [OAuth2Client] receives a [Token] response.
     *
     * This should throw an [Exception] if the token is invalid.
     *
     * @param client the [OAuth2Client] that made the [Token] request.
     * @param idToken the [Jwt] representing the id token from the [Token] response.
     * @param parameters the [Parameters] used to validate the id token.
     */
    suspend fun validate(client: OAuth2Client, idToken: Jwt, parameters: Parameters)
}

// https://openid.net/specs/openid-connect-core-1_0.html#IDTokenValidation
internal class DefaultIdTokenValidator : IdTokenValidator {
    override suspend fun validate(client: OAuth2Client, idToken: Jwt, parameters: IdTokenValidator.Parameters) {
        val idTokenPayload = idToken.deserializeClaims(IdTokenValidationPayload.serializer())

        val event = ValidateIdTokenEvent(600)
        client.configuration.eventCoordinator.sendEvent(event)

        if (idTokenPayload.iss.toHttpUrl() != client.endpointsOrNull()?.issuer) {
            throw IdTokenValidator.Error("Invalid issuer.", IdTokenValidator.Error.INVALID_ISSUER)
        }
        if (idTokenPayload.aud != client.configuration.clientId) {
            throw IdTokenValidator.Error("Invalid audience.", IdTokenValidator.Error.INVALID_AUDIENCE)
        }
        if (!idTokenPayload.iss.startsWith("https://")) {
            throw IdTokenValidator.Error("Issuer must use HTTPS.", IdTokenValidator.Error.ISSUER_NOT_HTTPS)
        }
        if (idToken.algorithm != "RS256") {
            throw IdTokenValidator.Error("Invalid JWT algorithm.", IdTokenValidator.Error.INVALID_JWT_ALGORITHM)
        }
        if (idTokenPayload.exp < client.configuration.clock.currentTimeEpochSecond()) {
            throw IdTokenValidator.Error(
                "The current time MUST be before the time represented by the exp Claim.",
                IdTokenValidator.Error.EXPIRED
            )
        }
        if (abs(idTokenPayload.iat - client.configuration.clock.currentTimeEpochSecond()) > event.issuedAtGracePeriodInSeconds) {
            throw IdTokenValidator.Error(
                "Issued at time is not within the allowed threshold of now.",
                IdTokenValidator.Error.ISSUED_AT_THRESHOLD_NOT_SATISFIED
            )
        }
        if (idTokenPayload.nonce != parameters.nonce) {
            throw IdTokenValidator.Error("Nonce mismatch.", IdTokenValidator.Error.NONCE_MISMATCH)
        }
        if (parameters.maxAge != null) {
            val authTime = idTokenPayload.authTime ?: throw IdTokenValidator.Error(
                "Auth time not available.",
                IdTokenValidator.Error.MAX_AGE_NOT_SATISFIED
            )
            val elapsedTime = idTokenPayload.iat - authTime
            if (elapsedTime < 0 || elapsedTime > parameters.maxAge) {
                throw IdTokenValidator.Error("Max age not satisfied.", IdTokenValidator.Error.MAX_AGE_NOT_SATISFIED)
            }
        }
        if (idTokenPayload.sub.isNullOrBlank()) {
            throw IdTokenValidator.Error("A valid sub claim is required.", IdTokenValidator.Error.INVALID_SUBJECT)
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
    @SerialName("sub") val sub: String? = null,
)
