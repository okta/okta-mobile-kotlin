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
package com.okta.oauth2.kmp

import com.okta.authfoundation.client.TokenInfo

internal class FakeTokenInfo : TokenInfo {
    override val id: String = "test-id"
    override val clientId: String = "test-client"
    override val issuerUrl: String = "https://example.okta.com"
    override val tokenType: String = "Bearer"
    override val expiresIn: Int = 3600
    override val accessToken: String = "test-access-token"
    override val scope: String? = "openid profile"
    override val refreshToken: String? = "test-refresh-token"
    override val idToken: String? = null
    override val deviceSecret: String? = null
    override val issuedTokenType: String? = null
}
