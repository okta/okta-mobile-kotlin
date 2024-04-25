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
import com.okta.authfoundation.jwt.Jwks
import com.okta.authfoundation.jwt.JwtParser

internal class TokenValidator(
    private val client: OAuth2Client,
    private val token: Token,
    private val nonce: String?,
    private val maxAge: Int?,
    private val jwksResult: OAuth2ClientResult<Jwks>?,
) {
    private val parser = JwtParser(client.configuration.json, client.configuration.computeDispatcher)

    suspend fun validate() {
        if (token.idToken != null) {
            val idToken = parser.parse(token.idToken)

            client.configuration.idTokenValidator.validate(
                client = client,
                idToken = idToken,
                parameters = IdTokenValidator.Parameters(
                    nonce = nonce,
                    maxAge = maxAge,
                ),
            )

            if (jwksResult != null) {
                when (jwksResult) {
                    is OAuth2ClientResult.Success -> {
                        if (!idToken.hasValidSignature(jwksResult.result)) {
                            throw IdTokenValidator.Error("Invalid id_token signature", IdTokenValidator.Error.INVALID_JWT_SIGNATURE)
                        }
                    }
                    is OAuth2ClientResult.Error -> {
                        throw jwksResult.exception
                    }
                }
            }

            client.configuration.accessTokenValidator.validate(
                client = client,
                accessToken = token.accessToken,
                idToken = idToken,
            )

            token.deviceSecret?.let { deviceSecret ->
                client.configuration.deviceSecretValidator.validate(
                    client = client,
                    deviceSecret = deviceSecret,
                    idToken = idToken,
                )
            }
        }
    }
}
