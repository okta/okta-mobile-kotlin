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
package com.okta.authfoundation.claims

import com.google.common.truth.Truth.assertThat
import com.okta.testhelpers.OktaRule
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFailsWith

class DefaultClaimsProviderTest {
    @get:Rule val oktaRule = OktaRule()

    private val json: Json = oktaRule.configuration.json

    @Test fun testDeserializeClaims() {
        val claims: JsonObject = json.decodeFromString("""{"sub":"foobar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.deserializeClaims(ExampleUserInfo.serializer())
        assertThat(result.sub).isEqualTo("foobar")
    }

    @Test fun testDeserializeClaimsWithDifferentType() {
        val claims: JsonObject = json.decodeFromString("""{"missing":"foobar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        assertFailsWith<SerializationException> {
            subject.deserializeClaims(ExampleUserInfo.serializer())
        }
    }

    @Test fun testAvailableClaims() {
        val claims: JsonObject = json.decodeFromString("""{"sub":"foobar","exp":1234556}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.availableClaims()
        assertThat(result).hasSize(2)
        assertThat(result).contains("sub")
        assertThat(result).contains("exp")
    }

    @Test fun testDeserializeStringClaim() {
        val claims: JsonObject = json.decodeFromString("""{"sub":"foobar","exp":1234556}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.deserializeClaim("sub", String.serializer())
        assertThat(result).isEqualTo("foobar")
    }

    @Test fun testDeserializeIntClaim() {
        val claims: JsonObject = json.decodeFromString("""{"sub":"foobar","exp":1234556}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.deserializeClaim("exp", Int.serializer())
        assertThat(result).isEqualTo(1234556)
    }

    @Test fun testDeserializeNullStringClaim() {
        val claims: JsonObject = json.decodeFromString("""{"exp":1234556}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.deserializeClaim("sub", String.serializer())
        assertThat(result).isNull()
    }

    @Test fun testMissingSubject() {
        val claims: JsonObject = json.decodeFromString("""{}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.subject
        assertThat(result).isNull()
    }

    @Test fun `test subject`() {
        val claims: JsonObject = json.decodeFromString("""{"sub":"foobar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.subject
        assertThat(result).isEqualTo("foobar")
    }

    @Test fun `test name`() {
        val claims: JsonObject = json.decodeFromString("""{"name":"bar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.name
        assertThat(result).isEqualTo("bar")
    }

    @Test fun `test givenName`() {
        val claims: JsonObject = json.decodeFromString("""{"given_name":"bar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.givenName
        assertThat(result).isEqualTo("bar")
    }

    @Test fun `test familyName`() {
        val claims: JsonObject = json.decodeFromString("""{"family_name":"bar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.familyName
        assertThat(result).isEqualTo("bar")
    }

    @Test fun `test middleName`() {
        val claims: JsonObject = json.decodeFromString("""{"middle_name":"bar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.middleName
        assertThat(result).isEqualTo("bar")
    }

    @Test fun `test nickname`() {
        val claims: JsonObject = json.decodeFromString("""{"nickname":"bar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.nickname
        assertThat(result).isEqualTo("bar")
    }

    @Test fun `test preferredUsername`() {
        val claims: JsonObject = json.decodeFromString("""{"preferred_username":"bar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.preferredUsername
        assertThat(result).isEqualTo("bar")
    }

    @Test fun `test email`() {
        val claims: JsonObject = json.decodeFromString("""{"email":"bar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.email
        assertThat(result).isEqualTo("bar")
    }

    @Test fun `test phoneNumber`() {
        val claims: JsonObject = json.decodeFromString("""{"phone_number":"bar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.phoneNumber
        assertThat(result).isEqualTo("bar")
    }

    @Test fun `test gender`() {
        val claims: JsonObject = json.decodeFromString("""{"gender":"bar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.gender
        assertThat(result).isEqualTo("bar")
    }

    @Test fun `test active`() {
        val claims: JsonObject = json.decodeFromString("""{"active":true}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.active
        assertThat(result).isEqualTo(true)
    }

    @Test fun `test audience`() {
        val claims: JsonObject = json.decodeFromString("""{"aud":"bar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.audience
        assertThat(result).isEqualTo("bar")
    }

    @Test fun `test clientId`() {
        val claims: JsonObject = json.decodeFromString("""{"client_id":"bar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.clientId
        assertThat(result).isEqualTo("bar")
    }

    @Test fun `test deviceId`() {
        val claims: JsonObject = json.decodeFromString("""{"device_id":"bar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.deviceId
        assertThat(result).isEqualTo("bar")
    }

    @Test fun `test expirationTime`() {
        val claims: JsonObject = json.decodeFromString("""{"exp":123456}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.expirationTime
        assertThat(result).isEqualTo(123456)
    }

    @Test fun `test issuedAt`() {
        val claims: JsonObject = json.decodeFromString("""{"iat":123456}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.issuedAt
        assertThat(result).isEqualTo(123456)
    }

    @Test fun `test issuer`() {
        val claims: JsonObject = json.decodeFromString("""{"iss":"bar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.issuer
        assertThat(result).isEqualTo("bar")
    }

    @Test fun `test jwtId`() {
        val claims: JsonObject = json.decodeFromString("""{"jti":"bar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.jwtId
        assertThat(result).isEqualTo("bar")
    }

    @Test fun `test notBefore`() {
        val claims: JsonObject = json.decodeFromString("""{"nbf":123456}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.notBefore
        assertThat(result).isEqualTo(123456)
    }

    @Test fun `test scope`() {
        val claims: JsonObject = json.decodeFromString("""{"scope":"bar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.scope
        assertThat(result).isEqualTo("bar")
    }

    @Test fun `test tokenType`() {
        val claims: JsonObject = json.decodeFromString("""{"token_type":"bar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.tokenType
        assertThat(result).isEqualTo("bar")
    }

    @Test fun `test userId`() {
        val claims: JsonObject = json.decodeFromString("""{"uid":"bar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.userId
        assertThat(result).isEqualTo("bar")
    }

    @Test fun `test username`() {
        val claims: JsonObject = json.decodeFromString("""{"username":"bar"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.username
        assertThat(result).isEqualTo("bar")
    }

    @Test fun `test acr`() {
        val claims: JsonObject = json.decodeFromString("""{"acr":"pop"}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.authContextClassReference
        assertThat(result).isEqualTo("pop")
    }

    @Test fun `test amr`() {
        val claims: JsonObject = json.decodeFromString("""{"amr":["pwd", "mfa"]}""")
        val subject = DefaultClaimsProvider(claims, json)
        val result = subject.authMethodsReference
        assertThat(result).containsExactlyElementsIn(listOf("pwd", "mfa"))
    }
}

@Serializable
private class ExampleUserInfo(
    @SerialName("sub") val sub: String
)
