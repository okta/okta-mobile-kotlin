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
package com.okta.authfoundation.client.kmp

import com.okta.authfoundation.client.OidcClock
import com.okta.authfoundation.jwt.Jwt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Default [IdTokenValidator] implementing the
 * [OpenID Connect Core 1.0 ID Token Validation](https://openid.net/specs/openid-connect-core-1_0.html#IDTokenValidation) checks.
 *
 * Validates: issuer, audience, HTTPS, algorithm (RS256), expiration, issued-at grace period,
 * nonce, max_age, and subject.
 */
class DefaultIdTokenValidator : IdTokenValidator {
    override suspend fun validate(
        issuerUrl: String,
        clientId: String,
        idToken: Jwt,
        clock: OidcClock,
        issuedAtGracePeriodInSeconds: Int,
        parameters: IdTokenValidator.Parameters,
    ) {
        val payload = idToken.deserializeClaims(IdTokenValidationPayload.serializer())

        val normalizedIssuer = issuerUrl.trimEnd('/')
        val normalizedTokenIssuer = payload.iss.trimEnd('/')
        if (normalizedTokenIssuer != normalizedIssuer) {
            throw IdTokenValidator.Error("Invalid issuer.", IdTokenValidator.Error.INVALID_ISSUER)
        }
        if (payload.aud != clientId) {
            throw IdTokenValidator.Error("Invalid audience.", IdTokenValidator.Error.INVALID_AUDIENCE)
        }
        if (!payload.iss.startsWith("https://")) {
            throw IdTokenValidator.Error("Issuer must use HTTPS.", IdTokenValidator.Error.ISSUER_NOT_HTTPS)
        }
        if (idToken.algorithm != "RS256") {
            throw IdTokenValidator.Error("Invalid JWT algorithm.", IdTokenValidator.Error.INVALID_JWT_ALGORITHM)
        }
        if (payload.exp < clock.currentTimeEpochSecond()) {
            throw IdTokenValidator.Error(
                "The current time MUST be before the time represented by the exp Claim.",
                IdTokenValidator.Error.EXPIRED
            )
        }
        if (abs(payload.iat - clock.currentTimeEpochSecond()) > issuedAtGracePeriodInSeconds) {
            throw IdTokenValidator.Error(
                "Issued at time is not within the allowed threshold of now.",
                IdTokenValidator.Error.ISSUED_AT_THRESHOLD_NOT_SATISFIED
            )
        }
        if (payload.nonce != parameters.nonce) {
            throw IdTokenValidator.Error("Nonce mismatch.", IdTokenValidator.Error.NONCE_MISMATCH)
        }
        if (parameters.maxAge != null) {
            val authTime =
                payload.authTime ?: throw IdTokenValidator.Error(
                    "Auth time not available.",
                    IdTokenValidator.Error.MAX_AGE_NOT_SATISFIED
                )
            val elapsedTime = payload.iat - authTime
            if (elapsedTime < 0 || elapsedTime > parameters.maxAge) {
                throw IdTokenValidator.Error("Max age not satisfied.", IdTokenValidator.Error.MAX_AGE_NOT_SATISFIED)
            }
        }
        if (payload.sub.isNullOrBlank()) {
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
