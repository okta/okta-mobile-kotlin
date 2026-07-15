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

/**
 * Used for validating ID tokens minted by an Authorization Server.
 *
 * See [OpenID Connect Core 1.0 - ID Token Validation](https://openid.net/specs/openid-connect-core-1_0.html#IDTokenValidation).
 */
interface IdTokenValidator {
    /**
     * An error describing a validation failure.
     *
     * @param message human-readable description of the failure.
     * @param identifier machine-readable error code (see companion constants).
     */
    class Error(
        message: String,
        val identifier: String = "",
    ) : IllegalStateException(message) {
        companion object {
            /** Issuer does not match the expected Authorization Server. */
            const val INVALID_ISSUER = "invalid_issuer"

            /** Audience (`aud`) does not match the client ID. */
            const val INVALID_AUDIENCE = "invalid_audience"

            /** Issuer does not use HTTPS. */
            const val ISSUER_NOT_HTTPS = "issuer_not_https"

            /** JWT signing algorithm is not RS256. */
            const val INVALID_JWT_ALGORITHM = "invalid_jwt_algorithm"

            /** JWT signature verification failed. */
            const val INVALID_JWT_SIGNATURE = "invalid_jwt_signature"

            /** Token has expired (`exp` is in the past). */
            const val EXPIRED = "expired"

            /** `iat` claim is outside the allowed grace period. */
            const val ISSUED_AT_THRESHOLD_NOT_SATISFIED = "issued_at_threshold_not_satisfied"

            /** Nonce does not match the value sent in the authorization request. */
            const val NONCE_MISMATCH = "nonce_mismatch"

            /** `auth_time` is missing or outside the allowed `max_age`. */
            const val MAX_AGE_NOT_SATISFIED = "max_age_not_satisfied"

            /** Token is missing a valid `sub` claim. */
            const val INVALID_SUBJECT = "invalid_subject"
        }
    }

    /**
     * Parameters for ID token validation.
     *
     * @param nonce the `nonce` sent with the authorization request, if available.
     * @param maxAge the `max_age` sent with the authorization request, if available.
     */
    class Parameters(
        val nonce: String?,
        val maxAge: Int?,
    )

    /**
     * Validates the given [idToken].
     *
     * Implementations should throw [Error] (or another [Exception]) if validation fails.
     *
     * @param issuerUrl the expected issuer URL.
     * @param clientId the expected client ID (audience).
     * @param idToken the parsed [Jwt] to validate.
     * @param clock the clock for time-based checks.
     * @param parameters optional nonce/maxAge parameters.
     */
    suspend fun validate(
        issuerUrl: String,
        clientId: String,
        idToken: Jwt,
        clock: OidcClock,
        parameters: Parameters = Parameters(nonce = null, maxAge = null),
    )
}
