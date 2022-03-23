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

import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.jwt.JwtParser

internal class TokenValidator(
    private val oidcClient: OidcClient,
    private val token: Token,
    private val nonce: String?,
    private val maxAge: Int?,
) {
    private val parser = JwtParser(oidcClient.configuration.json, oidcClient.configuration.computeDispatcher)

    suspend fun validate() {
        if (token.idToken != null) {
            val idToken = parser.parse(token.idToken)

            oidcClient.configuration.idTokenValidator.validate(
                oidcClient = oidcClient,
                idToken = idToken,
                nonce = nonce,
                maxAge = maxAge,
            )

            oidcClient.configuration.accessTokenValidator.validate(
                oidcClient = oidcClient,
                accessToken = token.accessToken,
                idToken = idToken,
            )

            token.deviceSecret?.let { deviceSecret ->
                oidcClient.configuration.deviceSecretValidator.validate(
                    oidcClient = oidcClient,
                    deviceSecret = deviceSecret,
                    idToken = idToken,
                )
            }
        }
    }
}
