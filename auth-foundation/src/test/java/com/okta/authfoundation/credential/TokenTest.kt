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

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.testhelpers.OktaRule
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.Test

class TokenTest {
    @get:Rule val oktaRule = OktaRule()

    private val oidcConfiguration = OidcConfiguration("clientId", "defaultScope", "issuer")

    @Test fun testDeserializingMinimal() {
        val json = """
        {
          "token_type": "Bearer",
          "expires_in": 3600,
          "access_token": "exampleAccessToken"
        }
        """.trimIndent()
        val token = oktaRule.configuration.json.decodeFromString(SerializableToken.serializer(), json).asToken("id", oktaRule.configuration)
        assertThat(token.tokenType).isEqualTo("Bearer")
        assertThat(token.expiresIn).isEqualTo(3600)
        assertThat(token.accessToken).isEqualTo("exampleAccessToken")
        assertThat(token.scope).isNull()
        assertThat(token.refreshToken).isNull()
        assertThat(token.idToken).isNull()
        assertThat(token.deviceSecret).isNull()
        assertThat(token.issuedTokenType).isNull()
        assertThat(token.issuedAt).isEqualTo(oktaRule.clock.currentTime)
    }

    @Test fun testDeserializingDefault() {
        val json = """
        {
          "token_type": "Bearer",
          "expires_in": 3600,
          "access_token": "exampleAccessToken",
          "scope": "offline_access profile openid email",
          "refresh_token": "exampleRefreshToken",
          "id_token": "exampleIdToken"
        }
        """.trimIndent()
        val token = oktaRule.configuration.json.decodeFromString(SerializableToken.serializer(), json).asToken("id", oktaRule.configuration)
        assertThat(token.tokenType).isEqualTo("Bearer")
        assertThat(token.expiresIn).isEqualTo(3600)
        assertThat(token.accessToken).isEqualTo("exampleAccessToken")
        assertThat(token.scope).isEqualTo("offline_access profile openid email")
        assertThat(token.refreshToken).isEqualTo("exampleRefreshToken")
        assertThat(token.idToken).isEqualTo("exampleIdToken")
        assertThat(token.issuedAt).isEqualTo(oktaRule.clock.currentTime)
    }

    @Test fun testDeserializingWithDeviceSecret() {
        val json = """
        {
          "token_type": "Bearer",
          "expires_in": 3600,
          "access_token": "exampleAccessToken",
          "scope": "offline_access profile openid email",
          "refresh_token": "exampleRefreshToken",
          "id_token": "exampleIdToken",
          "device_secret": "exampleDeviceSecret"
        }
        """.trimIndent()
        val token = oktaRule.configuration.json.decodeFromString(SerializableToken.serializer(), json).asToken("id", oktaRule.configuration)
        assertThat(token.tokenType).isEqualTo("Bearer")
        assertThat(token.expiresIn).isEqualTo(3600)
        assertThat(token.accessToken).isEqualTo("exampleAccessToken")
        assertThat(token.scope).isEqualTo("offline_access profile openid email")
        assertThat(token.refreshToken).isEqualTo("exampleRefreshToken")
        assertThat(token.idToken).isEqualTo("exampleIdToken")
        assertThat(token.deviceSecret).isEqualTo("exampleDeviceSecret")
    }

    @Test fun testDeserializingWithIssuedTokenType() {
        val json = """
        {
          "token_type": "Bearer",
          "expires_in": 3600,
          "access_token": "exampleAccessToken",
          "scope": "offline_access profile openid email",
          "refresh_token": "exampleRefreshToken",
          "id_token": "exampleIdToken",
          "issued_token_type": "urn:ietf:params:oauth:token-type:access_token"
        }
        """.trimIndent()
        val token = oktaRule.configuration.json.decodeFromString(SerializableToken.serializer(), json).asToken("id", oktaRule.configuration)
        assertThat(token.tokenType).isEqualTo("Bearer")
        assertThat(token.expiresIn).isEqualTo(3600)
        assertThat(token.accessToken).isEqualTo("exampleAccessToken")
        assertThat(token.scope).isEqualTo("offline_access profile openid email")
        assertThat(token.refreshToken).isEqualTo("exampleRefreshToken")
        assertThat(token.idToken).isEqualTo("exampleIdToken")
        assertThat(token.issuedTokenType).isEqualTo("urn:ietf:params:oauth:token-type:access_token")
    }

    @Test fun testSerializingMinimal() {
        val token = Token(
            id = "id",
            tokenType = "Bearer",
            expiresIn = 3600,
            accessToken = "exampleAccessToken",
            scope = "offline_access profile openid email",
            idToken = null,
            refreshToken = null,
            deviceSecret = null,
            issuedTokenType = null,
            oidcConfiguration = oidcConfiguration
        )
        val json = oktaRule.configuration.json.encodeToString(SerializableToken.serializer(), token.asSerializableToken())
        assertThat(json).isEqualTo(
            """
            {"token_type":"Bearer","expires_in":3600,"access_token":"exampleAccessToken","scope":"offline_access profile openid email"}
            """.trimIndent()
        )
    }

    @Test fun testSerializingEverything() {
        val token = Token(
            id = "id",
            tokenType = "Bearer",
            expiresIn = 3600,
            accessToken = "a",
            scope = "b",
            refreshToken = "c",
            idToken = "d",
            deviceSecret = "e",
            issuedTokenType = "f",
            oidcConfiguration = oidcConfiguration
        )
        val json = oktaRule.configuration.json.encodeToString(SerializableToken.serializer(), token.asSerializableToken())
        assertThat(json).isEqualTo(
            """
            {"token_type":"Bearer","expires_in":3600,"access_token":"a","scope":"b","refresh_token":"c","id_token":"d","device_secret":"e","issued_token_type":"f"}
            """.trimIndent()
        )
    }

    @Test
    fun `test deserializing without issuedAt`() {
        val json = """
            {
                "id": "id",
                "tokenType": "Bearer",
                "expiresIn": 3600,
                "accessToken": "accessToken",
                "scope": null,
                "refreshToken": null,
                "idToken": null,
                "deviceSecret": null,
                "issuedTokenType": null,
                "oidcConfiguration": {
                    "clientId": "clientId",
                    "defaultScope": "defaultScope",
                    "discoveryUrl": "issuer/.well-known/openid-configuration"
                }
            }
        """.trimIndent()
        val token = oktaRule.configuration.json.decodeFromString(Token.serializer(), json)
        assertThat(token.issuedAt).isEqualTo(oktaRule.clock.currentTime - token.expiresIn)
    }

    @Test
    fun `test deserializing with issuedAt`() {
        val issuedAt = 1234
        val json = """
            {
                "id": "id",
                "tokenType": "Bearer",
                "expiresIn": 3600,
                "accessToken": "accessToken",
                "scope": null,
                "refreshToken": null,
                "idToken": null,
                "deviceSecret": null,
                "issuedTokenType": null,
                "oidcConfiguration": {
                    "clientId": "clientId",
                    "defaultScope": "defaultScope",
                    "discoveryUrl": "issuer/.well-known/openid-configuration"
                },
                "issuedAt": $issuedAt
            }
        """.trimIndent()
        val token = oktaRule.configuration.json.decodeFromString(Token.serializer(), json)
        assertThat(token.issuedAt).isEqualTo(issuedAt)
    }

    @Test fun `test fetching claims from Token Metadata`() {
        val tokenMetadata = Token.Metadata(
            "id",
            tags = emptyMap(),
            payloadData = buildJsonObject {
                put("claim", "claimValue")
            }
        )
        assertThat(tokenMetadata.claimsProvider?.availableClaims()).isEqualTo(setOf("claim"))
        assertThat(tokenMetadata.claimsProvider?.deserializeClaim("claim", String.serializer())).isEqualTo("claimValue")
    }
}
