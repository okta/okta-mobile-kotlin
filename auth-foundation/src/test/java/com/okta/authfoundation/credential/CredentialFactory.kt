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
package com.okta.authfoundation.credential

import com.okta.authfoundation.client.OidcConfiguration

fun createToken(
    id: String = "id",
    scope: String = "openid email profile offline_access",
    accessToken: String = "exampleAccessToken",
    idToken: String? = null,
    refreshToken: String? = null,
    deviceSecret: String? = null,
    oidcConfiguration: OidcConfiguration = OidcConfiguration("clientId", "defaultScope", "issuer"),
): Token {
    return Token(
        id = id,
        tokenType = "Bearer",
        expiresIn = MOCK_TOKEN_DURATION,
        accessToken = accessToken,
        scope = scope,
        refreshToken = refreshToken,
        deviceSecret = deviceSecret,
        idToken = idToken,
        issuedTokenType = null,
        oidcConfiguration = oidcConfiguration
    )
}

const val MOCK_TOKEN_DURATION: Int = 3600
