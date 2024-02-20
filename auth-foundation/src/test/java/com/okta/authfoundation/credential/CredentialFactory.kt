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

import com.okta.authfoundation.client.OidcClient
import com.okta.testhelpers.OktaRule
import org.mockito.kotlin.mock

object CredentialFactory {
    const val tokenStorageId: String = "test_storage_id"
}

fun OktaRule.createCredential(
    token: Token,
    tags: Map<String, String> = emptyMap(),
    oidcClient: OidcClient = createOidcClient(),
    credentialDataSource: CredentialDataSource = mock(),
    storageId: String = CredentialFactory.tokenStorageId,
): Credential {
    return Credential(oidcClient, credentialDataSource, storageId, token, tags)
}

fun createToken(
    scope: String = "openid email profile offline_access",
    accessToken: String = "exampleAccessToken",
    idToken: String? = null,
    refreshToken: String? = null,
    deviceSecret: String? = null,
): Token {
    return Token(
        tokenType = "Bearer",
        expiresIn = MOCK_TOKEN_DURATION,
        accessToken = accessToken,
        scope = scope,
        refreshToken = refreshToken,
        deviceSecret = deviceSecret,
        idToken = idToken,
        issuedTokenType = null,
    )
}

const val MOCK_TOKEN_DURATION: Int = 3600
