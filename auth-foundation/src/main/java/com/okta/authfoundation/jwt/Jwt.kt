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
package com.okta.authfoundation.jwt

import com.okta.authfoundation.claims.ClaimsProvider

/**
 * Represents a Json Web Token.
 */
class Jwt internal constructor(
    /** Identifies the digital signature algorithm used. */
    val algorithm: String,
    /** Identifies the public key used to verify the ID token. */
    val keyId: String,

    claimsProvider: ClaimsProvider,

    /**
     * The base64 encoded signature.
     */
    val signature: String,
) : ClaimsProvider by claimsProvider
