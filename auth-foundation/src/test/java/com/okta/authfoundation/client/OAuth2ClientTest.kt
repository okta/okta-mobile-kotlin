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

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.client.dto.OidcIntrospectInfo
import com.okta.authfoundation.client.dto.OidcUserInfo
import com.okta.authfoundation.client.events.TokenCreatedEvent
import com.okta.authfoundation.credential.Credential
import com.okta.authfoundation.credential.RevokeTokenType
import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.credential.TokenType
import com.okta.authfoundation.credential.createToken
import com.okta.authfoundation.jwt.IdTokenClaims
import com.okta.authfoundation.jwt.Jwks
import com.okta.authfoundation.jwt.JwtBuilder.Companion.createJwtBuilder
import com.okta.authfoundation.jwt.createJwks
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.RequestMatchers.body
import com.okta.testhelpers.RequestMatchers.header
import com.okta.testhelpers.RequestMatchers.method
import com.okta.testhelpers.RequestMatchers.path
import com.okta.testhelpers.RequestMatchers.query
import com.okta.testhelpers.testBodyFromFile
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OAuth2ClientTest {
    private val mockPrefix = "client_test_responses"

    @get:Rule
    val oktaRule = OktaRule()

    @Before
    fun setup() {
        mockkObject(Credential)
        coEvery { Credential.credentialDataSource() } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testCreate(): Unit = runBlocking {
        oktaRule.enqueue(
            method("GET"),
            path("/.well-known/openid-configuration"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/endpoints.json")
        }
        val client = OAuth2Client.createFromConfiguration(oktaRule.configuration)
        val endpoints = (client.endpoints.get() as OAuth2ClientResult.Success<OidcEndpoints>).result
        assertThat(endpoints.issuer).isEqualTo("https://example.okta.com/oauth2/default".toHttpUrl())
        assertThat(endpoints.authorizationEndpoint).isEqualTo("https://example.okta.com/oauth2/default/v1/authorize".toHttpUrl())
        assertThat(endpoints.tokenEndpoint).isEqualTo("https://example.okta.com/oauth2/default/v1/token".toHttpUrl())
        assertThat(endpoints.userInfoEndpoint).isEqualTo("https://example.okta.com/oauth2/default/v1/userinfo".toHttpUrl())
        assertThat(endpoints.jwksUri).isEqualTo("https://example.okta.com/oauth2/default/v1/keys".toHttpUrl())
        assertThat(endpoints.introspectionEndpoint).isEqualTo("https://example.okta.com/oauth2/default/v1/introspect".toHttpUrl())
        assertThat(endpoints.revocationEndpoint).isEqualTo("https://example.okta.com/oauth2/default/v1/revoke".toHttpUrl())
        assertThat(endpoints.endSessionEndpoint).isEqualTo("https://example.okta.com/oauth2/default/v1/logout".toHttpUrl())
    }

    @Test
    fun testCreateNetworkFailure(): Unit = runBlocking {
        oktaRule.enqueue(path("/.well-known/openid-configuration")) { response ->
            response.setResponseCode(503)
        }
        val client = OAuth2Client.createFromConfiguration(oktaRule.configuration)
        val errorResult = (client.endpoints.get() as OAuth2ClientResult.Error<OidcEndpoints>)
        assertThat(errorResult.exception).isInstanceOf(OAuth2ClientResult.Error.HttpResponseException::class.java)
        assertThat(errorResult.exception).hasMessageThat()
            .isEqualTo("HTTP Error: status code - 503")
    }

    @Test
    fun testGetUserInfo(): Unit = runBlocking {
        oktaRule.enqueue(
            method("GET"),
            path("/oauth2/default/v1/userinfo"),
            header("authorization", "Bearer ExampleToken!"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/userinfo.json")
        }
        val result = oktaRule.createOAuth2Client().getUserInfo("ExampleToken!")
        val userInfo = (result as OAuth2ClientResult.Success<OidcUserInfo>).result

        @Serializable
        class ExampleUserInfo(
            @SerialName("sub") val sub: String
        )
        assertThat(userInfo.deserializeClaims(ExampleUserInfo.serializer()).sub).isEqualTo("00ub41z7mgzNqryMv696")
    }

    @Test
    fun testGetUserInfoFailsWithNoSub(): Unit = runBlocking {
        oktaRule.enqueue(
            method("GET"),
            path("/oauth2/default/v1/userinfo"),
            header("authorization", "Bearer ExampleToken!"),
        ) { response ->
            response.setBody("""{"name":"Jay Newstrom"}""")
        }
        val result = oktaRule.createOAuth2Client().getUserInfo("ExampleToken!")
        val exception = (result as OAuth2ClientResult.Error<OidcUserInfo>).exception

        assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(exception).hasMessageThat()
            .isEqualTo("The user info endpoint must return a valid sub claim.")
    }

    @Test
    fun testGetUserInfoFailure(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/oauth2/default/v1/userinfo"),
        ) { response ->
            response.setResponseCode(503)
        }
        val result = oktaRule.createOAuth2Client().getUserInfo("ExampleToken!")
        val errorResult = (result as OAuth2ClientResult.Error<OidcUserInfo>)
        assertThat(errorResult.exception).isInstanceOf(OAuth2ClientResult.Error.HttpResponseException::class.java)
        assertThat(errorResult.exception).hasMessageThat()
            .isEqualTo("HTTP Error: status code - 503")
    }

    @Test
    fun testRefreshToken(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            header("content-type", "application/x-www-form-urlencoded"),
            path("/oauth2/default/v1/token"),
            body("client_id=unit_test_client_id&grant_type=refresh_token&refresh_token=ExampleRefreshToken"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/token.json")
        }
        val result = oktaRule.createOAuth2Client()
            .refreshToken(createToken(refreshToken = "ExampleRefreshToken"))
        val token = (result as OAuth2ClientResult.Success<Token>).result
        assertThat(token.tokenType).isEqualTo("Bearer")
        assertThat(token.expiresIn).isEqualTo(3600)
        assertThat(token.accessToken).isEqualTo("exampleAccessToken")
        assertThat(token.scope).isEqualTo("offline_access profile openid email")
        assertThat(token.refreshToken).isEqualTo("exampleRefreshToken")
        assertThat(token.idToken).isEqualTo(
            "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6IjBvYThmdXAwbEFQWUZDNEkyNjk2IiwiaWF0IjoxNjQ0MzQ3MDY5LCJleHAiOjE2NDQzNTA2NjksImp0aSI6IklELjU1Y3hCdGRZbDhsNmFyS0lTUEJ3ZDB5T1QtOVVDVGFYYVFUWHQybGFSTHMiLCJhbXIiOlsicHdkIl0sImlkcCI6IjAwbzhmb3U3c1JhR0d3ZG40Njk2Iiwic2lkIjoiaWR4V3hrbHBfNGtTeHVDX25VMXBYRC1uQSIsInByZWZlcnJlZF91c2VybmFtZSI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwiYXV0aF90aW1lIjoxNjQ0MzQ3MDY4LCJhdF9oYXNoIjoiZ01jR1RiaEdUMUdfbGRzSG9Kc1B6USIsImRzX2hhc2giOiJEQWVMT0ZScWlmeXNiZ3NyYk9nYm9nIn0.z7LBgWT2O-DUZiOOUzr90qEgLoMiR5eHZsY1V2XPbhfOrjIv9ax9niHE7lPS5GYq02w4Cuf0DbdWjiNj96n4wTPmNU6N0x-XRluv4kved_wBBIvWNLGu_ZZZAFXaIFqmFGxPB6hIsYKvB3FmQCC0NvSXyDquadW9X7bBA7BO7VfX_jOKCkK_1MC1FZdU9n8rppu190Gk-z5dEWegHHtKy3vb12t4NR9CkA2uQgolnii8fNbie-3Z6zAdMXAZXkIcFu43Wn4TGwuzWK25IThcMNsPbLFFI4r0zo9E20IsH4gcJQiE_vFUzukzCsbppaiSAWBdSgES9K-QskWacZIWOg"
        )
    }

    @Test
    fun testRefreshTokenEmitsEvent(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            header("content-type", "application/x-www-form-urlencoded"),
            path("/oauth2/default/v1/token"),
            body("client_id=unit_test_client_id&grant_type=refresh_token&refresh_token=ExampleRefreshToken"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/token.json")
        }
        val result = oktaRule.createOAuth2Client()
            .refreshToken(createToken(refreshToken = "ExampleRefreshToken"))
        assertThat(result).isInstanceOf(OAuth2ClientResult.Success::class.java)
        val successResult = result as OAuth2ClientResult.Success<Token>
        assertThat(oktaRule.eventHandler).hasSize(1)
        val event = oktaRule.eventHandler[0]
        assertThat(event).isInstanceOf(TokenCreatedEvent::class.java)
        val tokenCreatedEvent = event as TokenCreatedEvent
        assertThat(tokenCreatedEvent.token).isNotNull()
        assertThat(tokenCreatedEvent.token).isEqualTo(successResult.result)
    }

    @Test
    fun testRefreshTokenFailure(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/oauth2/default/v1/token"),
        ) { response ->
            response.setResponseCode(503)
        }
        val result =
            oktaRule.createOAuth2Client().refreshToken(createToken(refreshToken = "ExampleToken!"))
        val errorResult = (result as OAuth2ClientResult.Error<Token>)
        assertThat(errorResult.exception).isInstanceOf(OAuth2ClientResult.Error.HttpResponseException::class.java)
        assertThat(errorResult.exception).hasMessageThat()
            .isEqualTo("HTTP Error: status code - 503")
    }

    @Test
    fun testRevokeToken(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            header("content-type", "application/x-www-form-urlencoded"),
            path("/oauth2/default/v1/revoke"),
            body("client_id=unit_test_client_id&token=ExampleRefreshToken"),
        ) { response ->
            response.setResponseCode(200)
        }
        val result = oktaRule.createOAuth2Client().revokeToken(
            RevokeTokenType.REFRESH_TOKEN,
            createToken(refreshToken = "ExampleRefreshToken")
        )
        assertThat(result).isInstanceOf(OAuth2ClientResult.Success::class.java)
    }

    @Test
    fun testRevokeTokenFailure(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/oauth2/default/v1/revoke"),
        ) { response ->
            response.setResponseCode(503)
        }
        val result = oktaRule.createOAuth2Client().revokeToken(
            RevokeTokenType.REFRESH_TOKEN,
            createToken(refreshToken = "ExampleRefreshToken")
        )
        val errorResult = (result as OAuth2ClientResult.Error<Unit>)
        assertThat(errorResult.exception).isInstanceOf(OAuth2ClientResult.Error.HttpResponseException::class.java)
        assertThat(errorResult.exception).hasMessageThat()
            .isEqualTo("HTTP Error: status code - 503")
    }

    @Test
    fun testIntrospectActiveAccessToken(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            header("content-type", "application/x-www-form-urlencoded"),
            path("/oauth2/default/v1/introspect"),
            body("client_id=unit_test_client_id&token=ExampleAccessToken&token_type_hint=access_token"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/introspectActive.json")
        }
        val result = oktaRule.createOAuth2Client().introspectToken(
            TokenType.ACCESS_TOKEN,
            createToken(accessToken = "ExampleAccessToken")
        )
        val successResult = (result as OAuth2ClientResult.Success<OidcIntrospectInfo>).result
        val activeResult = successResult as OidcIntrospectInfo.Active
        assertThat(successResult.active).isTrue()
        assertThat(activeResult.deserializeClaims(IntrospectExamplePayload.serializer()).username).isEqualTo(
            "example@gmail.com"
        )
    }

    @Test
    fun testIntrospectInactiveAccessToken(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            header("content-type", "application/x-www-form-urlencoded"),
            path("/oauth2/default/v1/introspect"),
            body("client_id=unit_test_client_id&token=ExampleAccessToken&token_type_hint=access_token"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/introspectInactive.json")
        }
        val result = oktaRule.createOAuth2Client().introspectToken(
            TokenType.ACCESS_TOKEN,
            createToken(accessToken = "ExampleAccessToken")
        )
        val successResult = (result as OAuth2ClientResult.Success<OidcIntrospectInfo>).result
        assertThat(successResult.active).isFalse()
        assertThat(successResult).isInstanceOf(OidcIntrospectInfo.Inactive::class.java)
    }

    @Test
    fun testIntrospectActiveRefreshToken(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            header("content-type", "application/x-www-form-urlencoded"),
            path("/oauth2/default/v1/introspect"),
            body("client_id=unit_test_client_id&token=ExampleRefreshToken&token_type_hint=refresh_token"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/introspectActive.json")
        }
        val result = oktaRule.createOAuth2Client()
            .introspectToken(TokenType.REFRESH_TOKEN, createToken(refreshToken = "ExampleRefreshToken"))
        val successResult = (result as OAuth2ClientResult.Success<OidcIntrospectInfo>).result
        val activeResult = successResult as OidcIntrospectInfo.Active
        assertThat(successResult.active).isTrue()
        assertThat(activeResult.deserializeClaims(IntrospectExamplePayload.serializer()).username).isEqualTo(
            "example@gmail.com"
        )
    }

    @Test
    fun testIntrospectActiveIdToken(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            header("content-type", "application/x-www-form-urlencoded"),
            path("/oauth2/default/v1/introspect"),
            body("client_id=unit_test_client_id&token=ExampleIdToken&token_type_hint=id_token"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/introspectActive.json")
        }
        val result =
            oktaRule.createOAuth2Client().introspectToken(TokenType.ID_TOKEN, createToken(idToken = "ExampleIdToken"))
        val successResult = (result as OAuth2ClientResult.Success<OidcIntrospectInfo>).result
        val activeResult = successResult as OidcIntrospectInfo.Active
        assertThat(successResult.active).isTrue()
        assertThat(activeResult.deserializeClaims(IntrospectExamplePayload.serializer()).username).isEqualTo(
            "example@gmail.com"
        )
    }

    @Test
    fun testIntrospectActiveDeviceSecret(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            header("content-type", "application/x-www-form-urlencoded"),
            path("/oauth2/default/v1/introspect"),
            body("client_id=unit_test_client_id&token=ExampleDeviceSecret&token_type_hint=device_secret"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/introspectActive.json")
        }
        val result = oktaRule.createOAuth2Client()
            .introspectToken(TokenType.DEVICE_SECRET, createToken(deviceSecret = "ExampleDeviceSecret"))
        val successResult = (result as OAuth2ClientResult.Success<OidcIntrospectInfo>).result
        val activeResult = successResult as OidcIntrospectInfo.Active
        assertThat(successResult.active).isTrue()
        assertThat(activeResult.deserializeClaims(IntrospectExamplePayload.serializer()).username).isEqualTo(
            "example@gmail.com"
        )
    }

    @Test
    fun testIntrospectWithNoActiveField(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            header("content-type", "application/x-www-form-urlencoded"),
            path("/oauth2/default/v1/introspect"),
            body("client_id=unit_test_client_id&token=ExampleDeviceSecret&token_type_hint=device_secret"),
        ) { response ->
            response.setBody("""{"missing":true}""")
        }
        val result = oktaRule.createOAuth2Client()
            .introspectToken(TokenType.DEVICE_SECRET, createToken(deviceSecret = "ExampleDeviceSecret"))
        assertThat(result).isInstanceOf(OAuth2ClientResult.Error::class.java)
    }

    @Test
    fun testIntrospectFailure(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/oauth2/default/v1/introspect"),
        ) { response ->
            response.setResponseCode(503)
        }
        val result =
            oktaRule.createOAuth2Client().introspectToken(TokenType.ID_TOKEN, createToken(idToken = "ExampleIdToken"))
        val errorResult = (result as OAuth2ClientResult.Error<OidcIntrospectInfo>)
        assertThat(errorResult.exception).isInstanceOf(OAuth2ClientResult.Error.HttpResponseException::class.java)
        assertThat(errorResult.exception).hasMessageThat()
            .isEqualTo("HTTP Error: status code - 503")
    }

    @Test
    fun testJwks(): Unit = runBlocking {
        oktaRule.enqueue(
            method("GET"),
            path("/oauth2/default/v1/keys"),
            query("client_id=unit_test_client_id")
        ) { response ->
            response.testBodyFromFile("$mockPrefix/jwks.json")
        }
        val result = oktaRule.createOAuth2Client(oktaRule.createEndpoints(includeJwks = true)).jwks()
        val jwks = (result as OAuth2ClientResult.Success<Jwks>).result
        assertThat(jwks.keys).hasSize(1)
        val key = jwks.keys[0]
        assertThat(key.algorithm).isEqualTo("RS256")
        assertThat(key.exponent).isEqualTo("AQAB")
        assertThat(key.modulus).isEqualTo("lbTQ6Q4bpE_htcqJ_36Z_WslsY5AVC9Gb-BisCEu-Pg0sdDXc0zPyhHtO4_VETO6VALz3ct_7PhTqbAmF71UcWNMQfBAwpkDfey4Pvl5X8zqRLsirRv-ufA0mnP85HdXrywE3_5mH_Se6ToPSWEIva516OMR66LgJPKQ09vWUavkwlWriU_NDjFCnj5a-IOJz73UGdPizweEVbmSNKzKArxH6r7ZyGnkGr4NLNDr5h_HvWj5lgE6eos87ZAID-lvxNdwREKXBrL8zmsaxQUfRYCI9pd_M4ZbGRzVq6n7LXo-SOvreI2cw9_wfJowWGqgGq655-X3bCbH9ob5_W44tw")
        assertThat(key.keyId).isEqualTo("8hivsGE-wrYPQsEOk5tLdAzPB-cVSpbbBvKFg0mjvLE")
        assertThat(key.keyType).isEqualTo("RSA")
        assertThat(key.use).isEqualTo("sig")
    }

    @Test
    fun testJwksIsCached(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/oauth2/default/v1/keys"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/jwks.json")
        }
        val client = oktaRule.createOAuth2Client(oktaRule.createEndpoints(includeJwks = true))
        val result = client.jwks()
        assertThat(result).isInstanceOf(OAuth2ClientResult.Success::class.java)
        val result2 =
            client.jwks() // This would fail, since we remove all network requests once it's consumed.
        assertThat(result2).isInstanceOf(OAuth2ClientResult.Success::class.java)
        assertThat(result).isSameInstanceAs(result2)
    }

    @Test
    fun testJwksIsNotCachedWhenTheRequestFails(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/oauth2/default/v1/keys"),
        ) { response ->
            response.setResponseCode(503)
        }
        oktaRule.enqueue(
            path("/oauth2/default/v1/keys"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/jwks.json")
        }
        val client = oktaRule.createOAuth2Client(oktaRule.createEndpoints(includeJwks = true))
        val result = client.jwks()
        assertThat(result).isInstanceOf(OAuth2ClientResult.Error::class.java)
        val result2 = client.jwks()
        assertThat(result2).isInstanceOf(OAuth2ClientResult.Success::class.java)
    }

    @Test
    fun testRefreshTokenValidationWithJwks(): Unit = runBlocking {
        val client = oktaRule.createOAuth2Client(oktaRule.createEndpoints(includeJwks = true))
        oktaRule.enqueue(
            path("/oauth2/default/v1/keys"),
        ) { response ->
            val keys = createJwks().toSerializableJwks()
            response.setBody(oktaRule.configuration.json.encodeToString(keys))
        }
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
        ) { response ->
            runBlocking {
                val token = createToken(
                    idToken = client.createJwtBuilder().createJwt(claims = IdTokenClaims()).rawValue
                )
                response.setBody(oktaRule.configuration.json.encodeToString(token.asSerializableToken()))
            }
        }
        val result = client.refreshToken(createToken(refreshToken = "ExampleRefreshToken"))
        assertThat(result).isInstanceOf(OAuth2ClientResult.Success::class.java)
    }

    @Test
    fun testRefreshTokenValidationFailsWithJwksError(): Unit = runBlocking {
        val client = oktaRule.createOAuth2Client(oktaRule.createEndpoints(includeJwks = true))
        oktaRule.enqueue(
            path("/oauth2/default/v1/keys"),
        ) { response ->
            response.setResponseCode(503)
        }
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
        ) { response ->
            response.testBodyFromFile("$mockPrefix/token.json")
        }
        val result = client.refreshToken(createToken(refreshToken = "ExampleRefreshToken"))
        assertThat(result).isInstanceOf(OAuth2ClientResult.Error::class.java)
        val exception = (result as OAuth2ClientResult.Error<Token>).exception
        assertThat(exception).hasMessageThat().isEqualTo("HTTP Error: status code - 503")
        assertThat(exception).isInstanceOf(OAuth2ClientResult.Error.HttpResponseException::class.java)
        val httpException = exception as OAuth2ClientResult.Error.HttpResponseException
        assertThat(httpException.responseCode).isEqualTo(503)
    }

    @Test
    fun testRefreshTokenValidationFailsWithInvalidSignature(): Unit = runBlocking {
        val client = oktaRule.createOAuth2Client(oktaRule.createEndpoints(includeJwks = true))
        oktaRule.enqueue(
            path("/oauth2/default/v1/keys"),
        ) { response ->
            val keys = createJwks().toSerializableJwks()
            response.setBody(oktaRule.configuration.json.encodeToString(keys))
        }
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
        ) { response ->
            runBlocking {
                val idTokenContent = client.createJwtBuilder()
                    .createJwt(claims = IdTokenClaims()).rawValue.substringBeforeLast(".")
                val token = createToken(idToken = "$idTokenContent.invalidSignature")
                response.setBody(oktaRule.configuration.json.encodeToString(token.asSerializableToken()))
            }
        }
        val result = client.refreshToken(createToken(refreshToken = "ExampleRefreshToken"))
        assertThat(result).isInstanceOf(OAuth2ClientResult.Error::class.java)
        val exception = (result as OAuth2ClientResult.Error<Token>).exception
        assertThat(exception).hasMessageThat().isEqualTo("Invalid id_token signature")
        assertThat(exception).isInstanceOf(IdTokenValidator.Error::class.java)
        val idTokenValidatorError = exception as IdTokenValidator.Error
        assertThat(idTokenValidatorError.identifier).isEqualTo(IdTokenValidator.Error.INVALID_JWT_SIGNATURE)
    }
}

@Serializable
private class IntrospectExamplePayload(
    @SerialName("username") val username: String
)
