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

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.claims.preferredUsername
import com.okta.authfoundation.jwt.JwtBuilder.Companion.createJwtBuilder
import com.okta.testhelpers.OktaRule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertFailsWith

class JwtParserTest {
    @get:Rule val oktaRule = OktaRule()

    @Test fun testJwtParserCreate(): Unit = runBlocking {
        val input = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims()).rawValue

        val parser = JwtParser.create()
        val jwt = parser.parse(input)
        assertThat(jwt.preferredUsername).isEqualTo("jaynewstrom@example.com")
    }

    @Test fun testJwtParser(): Unit = runBlocking {
        val input = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims()).rawValue

        val parser = JwtParser(Json { ignoreUnknownKeys = true }, EmptyCoroutineContext)
        val jwt = parser.parse(input)
        assertThat(jwt).isNotNull()

        assertThat(jwt.algorithm).isEqualTo("RS256")
        assertThat(jwt.keyId).isEqualTo("FJA0HGNtsuuda_Pl45J42kvQqcsu_0C4Fg7pbJLXTHY")

        @Serializable
        class ExamplePayload(
            @SerialName("sub") val sub: String
        )
        assertThat(jwt.deserializeClaims(ExamplePayload.serializer()).sub).isEqualTo("00ub41z7mgzNqryMv696")

        assertThat(jwt.signature).isEqualTo("Py4hkTtY4dnBTzlZuS-oMLuPa-08SnBKHqQEB7PPLxtKak6RXRiYcEFSoqLJlflYrloWt5iHbbWVNsUpx9EoCt8hfnbinFjRq99A__Zky1pW6WF6l6hCxGK60tI_kOLFIBc3Wzu4w-Kj6z1B7RXl0R_W739cxkYHrlpqiCuJyZAwX5Qwf1SmZZDmrqWlUdHefaO6L2TlNSgJGLrsyb2vZ8JYdvIANCbkl5gCF5gx6ebd9QkXf2EZdyxbIlBI2weSLtlGwTtLZi3k4Q2pk8pz_zDqyfOtVhOb4OxAiu0YuPikvJ8iddAxM94-f_n8SUZTSlQ2n39UWPrQw0mcUsmWzA")

        assertThat(jwt.rawValue).isEqualTo(input)
        assertThat(jwt.toString()).isEqualTo(input)
    }

    @Test fun testJwtParserMalformed(): Unit = runBlocking {
        val validInput = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims()).rawValue
        val input = validInput.substringAfter(".") // Removes header.

        val parser = JwtParser(Json { ignoreUnknownKeys = true }, EmptyCoroutineContext)

        val exception = assertFailsWith<IllegalArgumentException> {
            parser.parse(input)
        }
        assertThat(exception).hasMessageThat().isEqualTo("Token doesn't contain 3 parts. Needs header, claims data, and signature.")
    }

    @Test fun testJwtInstancesAreEqual(): Unit = runBlocking {
        val input = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims()).rawValue

        val parser = JwtParser(Json { ignoreUnknownKeys = true }, EmptyCoroutineContext)
        val jwt1 = parser.parse(input)
        val jwt2 = parser.parse(input)
        assertThat(jwt1).isEqualTo(jwt2)
        assertThat(jwt1).isNotSameInstanceAs(jwt2)
        assertThat(jwt1.hashCode()).isEqualTo(jwt2.hashCode())
    }

    @Test fun testJwtParserNonBase64Header(): Unit = runBlocking {
        val validInput = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims()).rawValue
        val input = validInput.replaceBefore(".", "+") // Replaces header with `+`.

        val parser = JwtParser(Json { ignoreUnknownKeys = true }, EmptyCoroutineContext)

        val exception = assertFailsWith<IllegalArgumentException> {
            parser.parse(input)
        }
        assertThat(exception).hasMessageThat().isEqualTo("Header isn't valid base64.")
    }

    @Test fun testJwtParserNonBase64Claims(): Unit = runBlocking {
        val validInput = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = IdTokenClaims()).rawValue
        val input = validInput.replaceAfter(".", "+.irrelevantSignature") // Replaces claims with `+`.

        val parser = JwtParser(Json { ignoreUnknownKeys = true }, EmptyCoroutineContext)

        val exception = assertFailsWith<IllegalArgumentException> {
            parser.parse(input)
        }
        assertThat(exception).hasMessageThat().isEqualTo("Claims aren't valid base64.")
    }
}
