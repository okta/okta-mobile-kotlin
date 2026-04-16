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

import com.okta.authfoundation.jwt.Jwt

/**
 * Validates access tokens by comparing the `at_hash` claim in the ID token
 * against the actual access token hash.
 *
 * See [OpenID Connect Core 1.0 - Access Token Validation](https://openid.net/specs/openid-connect-core-1_0.html#ImplicitTokenValidation).
 */
fun interface AccessTokenValidator {
    /**
     * An error describing a validation failure.
     *
     * @param message human-readable description of the failure.
     */
    class Error(
        message: String,
    ) : IllegalStateException(message)

    /**
     * Validates the [accessToken] against the `at_hash` claim in the [idToken].
     *
     * Implementations should throw [Error] if validation fails, or return normally
     * if the `at_hash` claim is absent (validation not applicable).
     *
     * @param accessToken the access token string to validate.
     * @param idToken the parsed [Jwt] containing the `at_hash` claim.
     */
    suspend fun validate(
        accessToken: String,
        idToken: Jwt,
    )
}
