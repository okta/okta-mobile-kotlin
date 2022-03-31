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
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.client.dto.OidcIntrospectInfo
import com.okta.authfoundation.client.dto.OidcUserInfo
import com.okta.authfoundation.client.events.TokenCreatedEvent
import com.okta.authfoundation.credential.events.CredentialStoredAfterRemovedEvent
import com.okta.authfoundation.jwt.IdTokenClaims
import com.okta.authfoundation.jwt.JwtBuilder.Companion.createJwtBuilder
import com.okta.testhelpers.InMemoryTokenStorage
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.RequestMatchers.header
import com.okta.testhelpers.RequestMatchers.method
import com.okta.testhelpers.RequestMatchers.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.Request
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

class CredentialTest {
    @get:Rule val oktaRule = OktaRule()

    @Test fun testOidcClient() {
        val credential = oktaRule.createCredential()
        assertThat(credential.oidcClient.credential).isEqualTo(credential)
    }

    @Test fun testMetadataChangeThrows() {
        val map = mutableMapOf<String, String>()
        map["name"] = "Default"
        val credential = oktaRule.createCredential(metadata = map)
        assertFailsWith<UnsupportedOperationException> {
            (credential.metadata as MutableMap<String, String>)["secure"] = "true"
        }
    }

    @Test fun testRemove(): Unit = runBlocking {
        val storage = mock<TokenStorage>()
        val credentialDataSource = mock<CredentialDataSource>()
        val token = createToken()
        val credential = oktaRule.createCredential(token = token, tokenStorage = storage, credentialDataSource = credentialDataSource)
        credential.delete()
        verify(credentialDataSource).remove(credential)
        verify(storage).remove(eq(CredentialFactory.tokenStorageId))
        assertThat(credential.token).isNull()
    }

    @Test fun testRemoveIsNoOpAfterRemove(): Unit = runBlocking {
        val storage = mock<TokenStorage>()
        val credentialDataSource = mock<CredentialDataSource>()
        val token = createToken()
        val credential = oktaRule.createCredential(token = token, tokenStorage = storage, credentialDataSource = credentialDataSource)
        credential.delete()
        credential.delete()
        verify(credentialDataSource).remove(credential)
        verify(storage).remove(eq(CredentialFactory.tokenStorageId))
        assertThat(credential.token).isNull()
    }

    @Test fun testStoreTokenIsNoOpAfterRemove(): Unit = runBlocking {
        val storage = mock<TokenStorage>()
        val credentialDataSource = mock<CredentialDataSource>()
        val token = createToken()
        val credential = oktaRule.createCredential(token = token, tokenStorage = storage, credentialDataSource = credentialDataSource)
        credential.delete()
        credential.storeToken(token = createToken())
        verify(storage, never()).replace(any())
    }

    @Test fun testStoreTokenEmitsEventAfterRemove(): Unit = runBlocking {
        val storage = mock<TokenStorage>()
        val credentialDataSource = mock<CredentialDataSource>()
        val token = createToken()
        val credential = oktaRule.createCredential(token = token, tokenStorage = storage, credentialDataSource = credentialDataSource)
        credential.delete()
        credential.storeToken(token = createToken())
        assertThat(oktaRule.eventHandler).hasSize(1)
        val event = oktaRule.eventHandler[0]
        assertThat(event).isInstanceOf(CredentialStoredAfterRemovedEvent::class.java)
        val removedEvent = event as CredentialStoredAfterRemovedEvent
        assertThat(removedEvent.credential).isEqualTo(credential)
    }

    @Test fun testRevokeAccessToken(): Unit = runBlocking {
        val oidcClient = mock<OidcClient> {
            onBlocking { revokeToken(any()) } doReturn OidcClientResult.Success(Unit)
            on { withCredential(any()) } doReturn it
        }
        val credential = oktaRule.createCredential(token = createToken(), oidcClient = oidcClient)
        credential.revokeToken(RevokeTokenType.ACCESS_TOKEN)
        verify(oidcClient).revokeToken(eq("exampleAccessToken"))
    }

    @Test fun testRevokeRefreshToken(): Unit = runBlocking {
        val oidcClient = mock<OidcClient> {
            onBlocking { revokeToken(any()) } doReturn OidcClientResult.Success(Unit)
            on { withCredential(any()) } doReturn it
        }
        val credential = oktaRule.createCredential(token = createToken(refreshToken = "exampleRefreshToken"), oidcClient = oidcClient)
        credential.revokeToken(RevokeTokenType.REFRESH_TOKEN)
        verify(oidcClient).revokeToken(eq("exampleRefreshToken"))
    }

    @Test fun testRevokeDeviceSecret(): Unit = runBlocking {
        val oidcClient = mock<OidcClient> {
            onBlocking { revokeToken(any()) } doReturn OidcClientResult.Success(Unit)
            on { withCredential(any()) } doReturn it
        }
        val credential = oktaRule.createCredential(token = createToken(deviceSecret = "exampleDeviceSecret"), oidcClient = oidcClient)
        credential.revokeToken(RevokeTokenType.DEVICE_SECRET)
        verify(oidcClient).revokeToken(eq("exampleDeviceSecret"))
    }

    @Test fun testRevokeTokenWithNullTokenReturnsError(): Unit = runBlocking {
        val credential = oktaRule.createCredential()
        val result = credential.revokeToken()
        assertThat(result).isInstanceOf(OidcClientResult.Error::class.java)
        val errorResult = result as OidcClientResult.Error<Unit>
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No token.")
    }

    @Test fun testRevokeTokenWithNullRefreshTokenReturnsError(): Unit = runBlocking {
        val credential = oktaRule.createCredential(token = createToken())
        val result = credential.revokeToken(RevokeTokenType.REFRESH_TOKEN)
        assertThat(result).isInstanceOf(OidcClientResult.Error::class.java)
        val errorResult = result as OidcClientResult.Error<Unit>
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No refresh token.")
    }

    @Test fun testRevokeTokenWithNullDeviceSecretReturnsError(): Unit = runBlocking {
        val credential = oktaRule.createCredential(token = createToken())
        val result = credential.revokeToken(RevokeTokenType.DEVICE_SECRET)
        assertThat(result).isInstanceOf(OidcClientResult.Error::class.java)
        val errorResult = result as OidcClientResult.Error<Unit>
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No device secret.")
    }

    @Test fun testScopesReturnDefaultScopes() {
        val credential = oktaRule.createCredential()
        assertThat(credential.scopes()).isEqualTo(setOf("openid", "email", "profile", "offline_access"))
    }

    @Test fun testScopesReturnTokenScopes() {
        val credential = oktaRule.createCredential(token = createToken(scope = "openid bank_account"))
        assertThat(credential.scopes()).isEqualTo(setOf("openid", "bank_account"))
    }

    @Test fun testRefreshWithNoToken(): Unit = runBlocking {
        val tokenStorage = mock<TokenStorage>()
        val credential = oktaRule.createCredential(tokenStorage = tokenStorage)
        val result = credential.refreshToken()
        assertThat(result).isInstanceOf(OidcClientResult.Error::class.java)
        val exception = (result as OidcClientResult.Error<Token>).exception
        assertThat(exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(exception).hasMessageThat().isEqualTo("No Token.")
        verifyNoInteractions(tokenStorage)
    }

    @Test fun testRefreshWithNoRefreshToken(): Unit = runBlocking {
        val tokenStorage = mock<TokenStorage>()
        val credential = oktaRule.createCredential(token = createToken(refreshToken = null), tokenStorage = tokenStorage)
        val result = credential.refreshToken()
        assertThat(result).isInstanceOf(OidcClientResult.Error::class.java)
        val exception = (result as OidcClientResult.Error<Token>).exception
        assertThat(exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(exception).hasMessageThat().isEqualTo("No Refresh Token.")
        verify(tokenStorage, never()).replace(any())
    }

    @Test fun testRefreshTokenForwardsError(): Unit = runBlocking {
        val tokenStorage = mock<TokenStorage>()
        val oidcClient = mock<OidcClient> {
            onBlocking { refreshToken(any(), any()) } doReturn OidcClientResult.Error(Exception("From Test"))
            on { withCredential(any()) } doReturn it
            on { configuration } doReturn oktaRule.configuration
        }
        val credential = oktaRule.createCredential(
            token = createToken(refreshToken = "exampleRefreshToken"),
            oidcClient = oidcClient,
            tokenStorage = tokenStorage
        )
        val result = credential.refreshToken()
        verify(oidcClient).refreshToken(eq("exampleRefreshToken"), eq(setOf("openid", "email", "profile", "offline_access")))
        assertThat(result).isInstanceOf(OidcClientResult.Error::class.java)
        val exception = (result as OidcClientResult.Error<Token>).exception
        assertThat(exception).hasMessageThat().isEqualTo("From Test")
        verify(tokenStorage, never()).replace(any())
    }

    @Test fun testRefreshToken(): Unit = runBlocking {
        val tokenStorage = spy(InMemoryTokenStorage())
        val oidcClient = mock<OidcClient> {
            onBlocking { refreshToken(any(), any()) } doReturn OidcClientResult.Success(createToken(refreshToken = "newRefreshToken"))
            on { withCredential(any()) } doReturn it
            on { configuration } doReturn oktaRule.configuration
        }
        val credential = oktaRule.createCredential(
            token = createToken(refreshToken = "exampleRefreshToken"),
            oidcClient = oidcClient,
            tokenStorage = tokenStorage
        )
        val result = credential.refreshToken()
        verify(oidcClient).refreshToken(eq("exampleRefreshToken"), eq(setOf("openid", "email", "profile", "offline_access")))
        assertThat(result).isInstanceOf(OidcClientResult.Success::class.java)
        val token = (result as OidcClientResult.Success<Token>).result
        assertThat(token.refreshToken).isEqualTo("newRefreshToken")
    }

    @Test fun testRefreshTokenWithRealOidcClient(): Unit = runBlocking {
        val credential = oktaRule.createCredential(
            token = createToken(refreshToken = "exampleRefreshToken"),
        )
        oktaRule.enqueue(path("/oauth2/default/v1/token")) { response ->
            val body = oktaRule.configuration.json.encodeToString(createToken(refreshToken = "newRefreshToken").asSerializableToken())
            response.setBody(body)
        }
        val result = credential.refreshToken()
        assertThat(result).isInstanceOf(OidcClientResult.Success::class.java)
        val token = (result as OidcClientResult.Success<Token>).result
        assertThat(token.refreshToken).isEqualTo("newRefreshToken")
        assertThat(credential.token?.refreshToken).isEqualTo("newRefreshToken")
    }

    @Test fun testRefreshTokenWithRealOidcClientPreservesRefreshToken(): Unit = runBlocking {
        val credential = oktaRule.createCredential(
            token = createToken(refreshToken = "exampleRefreshToken"),
        )
        oktaRule.enqueue(path("/oauth2/default/v1/token")) { response ->
            val body = oktaRule.configuration.json.encodeToString(createToken(refreshToken = null).asSerializableToken())
            response.setBody(body)
        }
        val result = credential.refreshToken()
        assertThat(result).isInstanceOf(OidcClientResult.Success::class.java)
        val token = (result as OidcClientResult.Success<Token>).result
        assertThat(token.refreshToken).isNull()
        assertThat(credential.token?.refreshToken).isEqualTo("exampleRefreshToken")
    }

    @Test fun testRefreshTokenWithRealOidcClientEmitsEvent(): Unit = runBlocking {
        val credential = oktaRule.createCredential(
            token = createToken(refreshToken = "exampleRefreshToken"),
        )
        oktaRule.enqueue(path("/oauth2/default/v1/token")) { response ->
            val body = oktaRule.configuration.json.encodeToString(createToken(refreshToken = "newRefreshToken").asSerializableToken())
            response.setBody(body)
        }
        val result = credential.refreshToken()
        assertThat(result).isInstanceOf(OidcClientResult.Success::class.java)
        val token = (result as OidcClientResult.Success<Token>).result
        assertThat(oktaRule.eventHandler).hasSize(1)
        val event = oktaRule.eventHandler[0]
        assertThat(event).isInstanceOf(TokenCreatedEvent::class.java)
        val tokenCreatedEvent = event as TokenCreatedEvent
        assertThat(tokenCreatedEvent.token).isNotNull()
        assertThat(tokenCreatedEvent.token).isEqualTo(token)
        assertThat(tokenCreatedEvent.credential).isNotNull()
        assertThat(tokenCreatedEvent.credential).isEqualTo(credential)
    }

    @Test fun testRefreshTokenPreservesDeviceSecret(): Unit = runBlocking {
        val oidcClient = mock<OidcClient> {
            onBlocking { refreshToken(any(), any()) } doReturn OidcClientResult.Success(createToken(refreshToken = "newRefreshToken"))
            on { withCredential(any()) } doReturn it
            on { configuration } doReturn oktaRule.configuration
        }
        val credential = oktaRule.createCredential(
            token = createToken(refreshToken = "exampleRefreshToken", deviceSecret = "saved"),
            oidcClient = oidcClient
        )
        credential.refreshToken()
        verify(oidcClient).refreshToken(eq("exampleRefreshToken"), any())
        assertThat(credential.token!!.deviceSecret).isEqualTo("saved")
    }

    @Test fun testParallelRefreshToken(): Unit = runBlocking {
        val countDownLatch = CountDownLatch(2)
        val tokenStorage = spy(InMemoryTokenStorage())
        val oidcClient = mock<OidcClient> {
            onBlocking { refreshToken(any(), any()) } doSuspendableAnswer {
                assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
                // Delay and change threads before returning the result to make sure we enqueue both requests before returning.
                delay(10)
                withContext(Dispatchers.Default) {
                    OidcClientResult.Success(createToken(refreshToken = "newRefreshToken"))
                }
            }
            on { withCredential(any()) } doReturn it
            on { configuration } doReturn oktaRule.configuration
        }
        val credential = oktaRule.createCredential(
            token = createToken(refreshToken = "exampleRefreshToken"),
            oidcClient = oidcClient,
            tokenStorage = tokenStorage
        )
        val deferred1 = async(Dispatchers.IO) {
            countDownLatch.countDown()
            credential.refreshToken()
        }
        val deferred2 = async(Dispatchers.IO) {
            countDownLatch.countDown()
            credential.refreshToken()
        }

        val result1 = deferred1.await()
        val result2 = deferred2.await()

        assertThat(result1).isInstanceOf(OidcClientResult.Success::class.java)
        val token = (result1 as OidcClientResult.Success<Token>).result
        assertThat(token.refreshToken).isEqualTo("newRefreshToken")
        assertThat(result1).isSameInstanceAs(result2)

        verify(oidcClient).refreshToken(eq("exampleRefreshToken"), eq(setOf("openid", "email", "profile", "offline_access")))
    }

    @Test fun testSerialRefreshToken(): Unit = runBlocking {
        val tokenStorage = spy(InMemoryTokenStorage())
        val oidcClient = mock<OidcClient> {
            onBlocking { refreshToken(any(), any()) } doAnswer {
                OidcClientResult.Success(createToken(refreshToken = "newRefreshToken"))
            }
            on { withCredential(any()) } doReturn it
            on { configuration } doReturn oktaRule.configuration
        }
        val credential = oktaRule.createCredential(
            token = createToken(refreshToken = "exampleRefreshToken"),
            oidcClient = oidcClient,
            tokenStorage = tokenStorage
        )

        val result1 = credential.refreshToken()
        assertThat(result1).isInstanceOf(OidcClientResult.Success::class.java)
        val token = (result1 as OidcClientResult.Success<Token>).result
        assertThat(token.refreshToken).isEqualTo("newRefreshToken")
        val result2 = credential.refreshToken()
        assertThat(result1).isNotSameInstanceAs(result2)

        verify(oidcClient, times(2)).refreshToken(any(), any())
    }

    @Test fun testStoreToken(): Unit = runBlocking {
        val tokenStorage = mock<TokenStorage>()
        val credential = oktaRule.createCredential(tokenStorage = tokenStorage)
        val metadata = mutableMapOf<String, String>()
        metadata["from_test"] = "It works"
        val token = createToken(refreshToken = "stored!")
        credential.storeToken(token = token, metadata = metadata)
        verify(tokenStorage).replace(TokenStorage.Entry(CredentialFactory.tokenStorageId, token, metadata))
        assertThat(credential.token).isEqualTo(token)
        assertThat(credential.metadata).isEqualTo(metadata)
    }

    @Test fun testStoreTokenKeepsPreviousDeviceSecret(): Unit = runBlocking {
        val tokenStorage = spy(InMemoryTokenStorage())
        val oidcClient = mock<OidcClient> {
            on { withCredential(any()) } doReturn it
            on { configuration } doReturn oktaRule.configuration
        }
        val credential = oktaRule.createCredential(
            token = createToken(refreshToken = "exampleRefreshToken", deviceSecret = "originalDeviceSecret"),
            oidcClient = oidcClient,
            tokenStorage = tokenStorage
        )
        val token = createToken(refreshToken = "stored!")
        credential.storeToken(token = token)
        val expectedToken = createToken(refreshToken = "stored!", deviceSecret = "originalDeviceSecret")
        verify(tokenStorage).replace(TokenStorage.Entry(CredentialFactory.tokenStorageId, expectedToken, emptyMap()))
        assertThat(credential.token).isEqualTo(expectedToken)
        assertThat(credential.token?.refreshToken).isEqualTo("stored!")
        assertThat(credential.token?.deviceSecret).isEqualTo("originalDeviceSecret")
    }

    @Test fun testStoreTokenKeepsStoresDeviceSecret(): Unit = runBlocking {
        val tokenStorage = spy(InMemoryTokenStorage())
        val oidcClient = mock<OidcClient> {
            on { withCredential(any()) } doReturn it
            on { configuration } doReturn oktaRule.configuration
        }
        val credential = oktaRule.createCredential(
            token = createToken(refreshToken = "exampleRefreshToken", deviceSecret = "originalDeviceSecret"),
            oidcClient = oidcClient,
            tokenStorage = tokenStorage
        )
        val token = createToken(refreshToken = "stored!", deviceSecret = "updatedDeviceSecret")
        credential.storeToken(token = token)
        verify(tokenStorage).replace(TokenStorage.Entry(CredentialFactory.tokenStorageId, token, emptyMap()))
        assertThat(credential.token).isEqualTo(token)
        assertThat(credential.token?.refreshToken).isEqualTo("stored!")
        assertThat(credential.token?.deviceSecret).isEqualTo("updatedDeviceSecret")
    }

    @Test fun testIdTokenWithNullTokenReturnsNullJwt(): Unit = runBlocking {
        val credential = oktaRule.createCredential(token = null)
        assertThat(credential.idToken()).isNull()
    }

    @Test fun testIdTokenWithNullIdTokenReturnsNullJwt(): Unit = runBlocking {
        val credential = oktaRule.createCredential(token = createToken(idToken = null))
        assertThat(credential.idToken()).isNull()
    }

    @Test fun testIdToken(): Unit = runBlocking {
        val credential = oktaRule.createCredential(token = createToken(idToken = "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6InVuaXRfdGVzdF9jbGllbnRfaWQiLCJpYXQiOjE2NDQzNDcwNjksImV4cCI6MTY0NDM1MDY2OSwianRpIjoiSUQuNTVjeEJ0ZFlsOGw2YXJLSVNQQndkMHlPVC05VUNUYVhhUVRYdDJsYVJMcyIsImFtciI6WyJwd2QiXSwiaWRwIjoiMDBvOGZvdTdzUmFHR3dkbjQ2OTYiLCJzaWQiOiJpZHhXeGtscF80a1N4dUNfblUxcFhELW5BIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiamF5bmV3c3Ryb21AZXhhbXBsZS5jb20iLCJhdXRoX3RpbWUiOjE2NDQzNDcwNjgsImF0X2hhc2giOiJnTWNHVGJoR1QxR19sZHNIb0pzUHpRIiwiZHNfaGFzaCI6IkRBZUxPRlJxaWZ5c2Jnc3JiT2dib2cifQ.tT8aKK4r8yFcW9KgVtZxvjXRJVzz-_rve14CVtpUlyvCTE1yj20wmPS0z3-JirI9xXgt5KeNPYqo3Wbv8c9XY_HY3hsPQdILYpPsUkf-sctmzSoKC_dTbs5xe8uKSgmpMrggfUAWrNPiJt9Ek2p7GgP64Wx79Pq5vSHk0yWlonFfXut5ahpSfqWilmYlvLr8gFbqoLnAJfl4ZbTY8pPw_aQgCdcQ-ImHRu-8bCSCtbFRzZB-SMJFLfRF2kmx0H-QF855wUODTuUSydkff-BKb-8wnbqWg0R9NvRdoXhEybv8TXXZY3cQqgolWLAyiPMrz07n0q_UEjAilUiCjn1f4Q"))
        val idToken = credential.idToken()
        assertThat(idToken).isNotNull()
        assertThat(idToken?.keyId).isEqualTo("FJA0HGNtsuuda_Pl45J42kvQqcsu_0C4Fg7pbJLXTHY")
    }

    @Test fun testMalformedIdToken(): Unit = runBlocking {
        val credential = oktaRule.createCredential(token = createToken(idToken = "InvalidJwt"))
        val idToken = credential.idToken()
        assertThat(idToken).isNull()
    }

    @Test fun accessTokenIfValidReturnsNullWithNullToken(): Unit = runBlocking {
        val credential = oktaRule.createCredential(token = null)
        assertThat(credential.getAccessTokenIfValid()).isNull()
    }

    @Test fun accessTokenIfValidReturnsNullWithExpiredToken(): Unit = runBlocking {
        val idTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime - MOCK_TOKEN_DURATION)
        val idToken = oktaRule.createOidcClient().createJwtBuilder().createJwt(claims = idTokenClaims).rawValue
        val credential = oktaRule.createCredential(token = createToken(accessToken = "exampleAccessToken", idToken = idToken))
        assertThat(credential.getAccessTokenIfValid()).isNull()
    }

    @Test fun accessTokenIfValidReturnsAccessToken(): Unit = runBlocking {
        val accessToken = "exampleAccessToken"
        val idTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime)
        val idToken = oktaRule.createOidcClient().createJwtBuilder().createJwt(claims = idTokenClaims).rawValue
        val credential = oktaRule.createCredential(token = createToken(accessToken = accessToken, idToken = idToken))
        assertThat(credential.getAccessTokenIfValid()).isEqualTo(accessToken)
    }

    @Test fun getValidAccessTokenReturnsAccessToken(): Unit = runBlocking {
        val accessToken = "exampleAccessToken"
        val idTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime)
        val idToken = oktaRule.createOidcClient().createJwtBuilder().createJwt(claims = idTokenClaims).rawValue
        val credential = oktaRule.createCredential(token = createToken(accessToken = accessToken, idToken = idToken))
        assertThat(credential.getValidAccessToken()).isEqualTo(accessToken)
    }

    @Test fun getValidAccessTokenReturnsNullWhenAccessTokenExpiredAndNoRefreshToken(): Unit = runBlocking {
        val idTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime - MOCK_TOKEN_DURATION)
        val idToken = oktaRule.createOidcClient().createJwtBuilder().createJwt(claims = idTokenClaims).rawValue
        val credential = oktaRule.createCredential(token = createToken(accessToken = "exampleAccessToken", idToken = idToken, refreshToken = null))
        assertThat(credential.getValidAccessToken()).isNull()
    }

    @Test fun getValidAccessTokenRefreshesWhenAccessTokenExpired(): Unit = runBlocking {
        val expiredAccessToken = "expiredAccessToken"
        val validAccessToken = "exampleAccessToken"
        val expiredIdTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime - MOCK_TOKEN_DURATION)
        val expiredIdToken = oktaRule.createOidcClient().createJwtBuilder().createJwt(claims = expiredIdTokenClaims).rawValue
        val validIdTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime)
        val validIdToken = oktaRule.createOidcClient().createJwtBuilder().createJwt(claims = validIdTokenClaims).rawValue
        oktaRule.enqueue(path("/oauth2/default/v1/token")) { response ->
            val body = oktaRule.configuration.json.encodeToString(createToken(accessToken = validAccessToken, idToken = validIdToken).asSerializableToken())
            response.setBody(body)
        }

        val credential = oktaRule.createCredential(token = createToken(accessToken = expiredAccessToken, refreshToken = "exampleRefreshToken", idToken = expiredIdToken))
        assertThat(credential.getValidAccessToken()).isEqualTo(validAccessToken)
    }

    @Test fun getValidAccessTokenReturnsNullWhenRefreshedAccessTokenExpired(): Unit = runBlocking {
        val expiredIdTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime - MOCK_TOKEN_DURATION)
        val expiredIdToken = oktaRule.createOidcClient().createJwtBuilder().createJwt(claims = expiredIdTokenClaims).rawValue
        val oidcClient = mock<OidcClient> {
            onBlocking { refreshToken(any(), any()) } doAnswer {
                OidcClientResult.Success(createToken(idToken = expiredIdToken))
            }
            on { withCredential(any()) } doReturn it
            on { configuration } doReturn oktaRule.configuration
        }
        val credential = oktaRule.createCredential(oidcClient = oidcClient, token = createToken(accessToken = "exampleAccessToken", refreshToken = "exampleRefreshToken", idToken = expiredIdToken))
        assertThat(credential.getValidAccessToken()).isNull()
        verify(oidcClient).refreshToken(any(), any())
    }

    @Test fun testInterceptor(): Unit = runBlocking {
        val accessToken = "exampleAccessToken"

        oktaRule.enqueue(
            method("GET"),
            header("authorization", "Bearer $accessToken"),
            path("/customers"),
        ) { response ->
            response.setBody("[]")
        }

        val idTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime)
        val idToken = oktaRule.createOidcClient().createJwtBuilder().createJwt(claims = idTokenClaims).rawValue
        val credential = oktaRule.createCredential(token = createToken(accessToken = accessToken, idToken = idToken))
        val interceptor = credential.accessTokenInterceptor()
        val okHttpClient = oktaRule.okHttpClient.newBuilder().addInterceptor(interceptor).build()
        val url = oktaRule.baseUrl.newBuilder().addPathSegment("customers").build()
        val call = okHttpClient.newCall(Request.Builder().url(url).build())
        val response = call.execute()
        assertThat(response.body!!.string()).isEqualTo("[]")
    }

    @Test fun testGetUserInfo(): Unit = runBlocking {
        val accessToken = "exampleAccessToken"
        val idTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime)
        val idToken = oktaRule.createOidcClient().createJwtBuilder().createJwt(claims = idTokenClaims).rawValue
        val credential = oktaRule.createCredential(
            token = createToken(accessToken = accessToken, idToken = idToken),
        )
        oktaRule.enqueue(path("/oauth2/default/v1/userinfo")) { response ->
            val body = """{"sub":"foobar"}"""
            response.setBody(body)
        }
        val result = credential.getUserInfo() as OidcClientResult.Success<OidcUserInfo>

        @Serializable
        class ExampleUserInfo(
            @SerialName("sub") val sub: String
        )
        assertThat(result.result.deserializeClaims(ExampleUserInfo.serializer()).sub).isEqualTo("foobar")
    }

    @Test fun testGetUserInfoNeedsRefresh(): Unit = runBlocking {
        val expiredAccessToken = "expiredAccessToken"
        val validAccessToken = "exampleAccessToken"
        val expiredIdTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime - MOCK_TOKEN_DURATION)
        val expiredIdToken = oktaRule.createOidcClient().createJwtBuilder().createJwt(claims = expiredIdTokenClaims).rawValue
        val validIdTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime)
        val validIdToken = oktaRule.createOidcClient().createJwtBuilder().createJwt(claims = validIdTokenClaims).rawValue
        val credential = oktaRule.createCredential(
            token = createToken(accessToken = expiredAccessToken, refreshToken = "exampleRefreshToken", idToken = expiredIdToken),
        )
        oktaRule.enqueue(path("/oauth2/default/v1/token")) { response ->
            val body = oktaRule.configuration.json.encodeToString(createToken(accessToken = validAccessToken, refreshToken = "newRefreshToken", idToken = validIdToken).asSerializableToken())
            response.setBody(body)
        }
        oktaRule.enqueue(path("/oauth2/default/v1/userinfo")) { response ->
            val body = """{"sub":"foobar"}"""
            response.setBody(body)
        }
        val result = credential.getUserInfo()

        @Serializable
        class ExampleUserInfo(
            @SerialName("sub") val sub: String
        )

        val success = result as OidcClientResult.Success<OidcUserInfo>
        assertThat(success.result.deserializeClaims(ExampleUserInfo.serializer()).sub).isEqualTo("foobar")
    }

    @Test fun testGetUserInfoNoToken(): Unit = runBlocking {
        val credential = oktaRule.createCredential()
        val result = credential.getUserInfo() as OidcClientResult.Error<OidcUserInfo>
        assertThat(result.exception).hasMessageThat().isEqualTo("No Access Token.")
        assertThat(result.exception).isInstanceOf(IllegalStateException::class.java)
    }

    @Test fun testIntrospectNullToken(): Unit = runBlocking {
        val credential = oktaRule.createCredential()
        val result = credential.introspectToken(TokenType.ACCESS_TOKEN)
        val errorResult = result as OidcClientResult.Error<OidcIntrospectInfo>
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No token.")
        assertThat(errorResult.exception).isInstanceOf(IllegalStateException::class.java)
    }

    @Test fun testIntrospectNullRefreshToken(): Unit = runBlocking {
        val credential = oktaRule.createCredential(token = createToken(refreshToken = null))
        val result = credential.introspectToken(TokenType.REFRESH_TOKEN)
        val errorResult = result as OidcClientResult.Error<OidcIntrospectInfo>
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No refresh token.")
        assertThat(errorResult.exception).isInstanceOf(IllegalStateException::class.java)
    }

    @Test fun testIntrospectNullIdToken(): Unit = runBlocking {
        val credential = oktaRule.createCredential(token = createToken(idToken = null))
        val result = credential.introspectToken(TokenType.ID_TOKEN)
        val errorResult = result as OidcClientResult.Error<OidcIntrospectInfo>
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No id token.")
        assertThat(errorResult.exception).isInstanceOf(IllegalStateException::class.java)
    }

    @Test fun testIntrospectNullDeviceSecret(): Unit = runBlocking {
        val credential = oktaRule.createCredential(token = createToken(deviceSecret = null))
        val result = credential.introspectToken(TokenType.DEVICE_SECRET)
        val errorResult = result as OidcClientResult.Error<OidcIntrospectInfo>
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No device secret.")
        assertThat(errorResult.exception).isInstanceOf(IllegalStateException::class.java)
    }

    @Test fun testIntrospectRefreshToken(): Unit = runBlocking {
        val oidcClient = mock<OidcClient> {
            onBlocking { introspectToken(any(), any()) } doAnswer {
                OidcClientResult.Success(OidcIntrospectInfo.Inactive)
            }
            on { withCredential(any()) } doReturn it
            on { configuration } doReturn oktaRule.configuration
        }
        val credential = oktaRule.createCredential(token = createToken(refreshToken = "exampleRefreshToken"), oidcClient = oidcClient)
        val result = credential.introspectToken(TokenType.REFRESH_TOKEN)
        val successResult = result as OidcClientResult.Success<OidcIntrospectInfo>
        assertThat(successResult.result.active).isFalse()
        verify(oidcClient).introspectToken(eq(TokenType.REFRESH_TOKEN), eq("exampleRefreshToken"))
    }

    @Test fun testIntrospectAccessToken(): Unit = runBlocking {
        val oidcClient = mock<OidcClient> {
            onBlocking { introspectToken(any(), any()) } doAnswer {
                OidcClientResult.Success(OidcIntrospectInfo.Inactive)
            }
            on { withCredential(any()) } doReturn it
            on { configuration } doReturn oktaRule.configuration
        }
        val credential = oktaRule.createCredential(token = createToken(accessToken = "exampleAccessToken"), oidcClient = oidcClient)
        val result = credential.introspectToken(TokenType.ACCESS_TOKEN)
        val successResult = result as OidcClientResult.Success<OidcIntrospectInfo>
        assertThat(successResult.result.active).isFalse()
        verify(oidcClient).introspectToken(eq(TokenType.ACCESS_TOKEN), eq("exampleAccessToken"))
    }

    @Test fun testIntrospectIdToken(): Unit = runBlocking {
        val oidcClient = mock<OidcClient> {
            onBlocking { introspectToken(any(), any()) } doAnswer {
                OidcClientResult.Success(OidcIntrospectInfo.Inactive)
            }
            on { withCredential(any()) } doReturn it
            on { configuration } doReturn oktaRule.configuration
        }
        val credential = oktaRule.createCredential(token = createToken(idToken = "exampleIdToken"), oidcClient = oidcClient)
        val result = credential.introspectToken(TokenType.ID_TOKEN)
        val successResult = result as OidcClientResult.Success<OidcIntrospectInfo>
        assertThat(successResult.result.active).isFalse()
        verify(oidcClient).introspectToken(eq(TokenType.ID_TOKEN), eq("exampleIdToken"))
    }

    @Test fun testIntrospectDeviceSecret(): Unit = runBlocking {
        val oidcClient = mock<OidcClient> {
            onBlocking { introspectToken(any(), any()) } doAnswer {
                OidcClientResult.Success(OidcIntrospectInfo.Inactive)
            }
            on { withCredential(any()) } doReturn it
            on { configuration } doReturn oktaRule.configuration
        }
        val credential = oktaRule.createCredential(token = createToken(deviceSecret = "exampleDeviceSecret"), oidcClient = oidcClient)
        val result = credential.introspectToken(TokenType.DEVICE_SECRET)
        val successResult = result as OidcClientResult.Success<OidcIntrospectInfo>
        assertThat(successResult.result.active).isFalse()
        verify(oidcClient).introspectToken(eq(TokenType.DEVICE_SECRET), eq("exampleDeviceSecret"))
    }

    @Test fun testIntrospectAccessTokenNetworkError(): Unit = runBlocking {
        val oidcClient = mock<OidcClient> {
            onBlocking { introspectToken(any(), any()) } doAnswer {
                OidcClientResult.Error(IllegalStateException("Exception from mock!"))
            }
            on { withCredential(any()) } doReturn it
            on { configuration } doReturn oktaRule.configuration
        }
        val credential = oktaRule.createCredential(token = createToken(accessToken = "exampleAccessToken"), oidcClient = oidcClient)
        val result = credential.introspectToken(TokenType.ACCESS_TOKEN)
        val errorResult = result as OidcClientResult.Error<OidcIntrospectInfo>
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("Exception from mock!")
        assertThat(errorResult.exception).isInstanceOf(IllegalStateException::class.java)
    }

    @Test fun testStoreTokenExceptionCausesErrorResponse(): Unit = runBlocking {
        val tokenStorage = object : TokenStorage {
            override suspend fun entries(): List<TokenStorage.Entry> {
                return emptyList()
            }

            override suspend fun add(id: String) {
            }

            override suspend fun remove(id: String) {
            }

            override suspend fun replace(updatedEntry: TokenStorage.Entry) {
                throw IllegalStateException("Expected From Test!")
            }
        }
        val credential = oktaRule.createCredential(
            token = createToken(refreshToken = "exampleRefreshToken"),
            tokenStorage = tokenStorage,
        )
        oktaRule.enqueue(path("/oauth2/default/v1/token")) { response ->
            val body = oktaRule.configuration.json.encodeToString(createToken(refreshToken = "newRefreshToken").asSerializableToken())
            response.setBody(body)
        }
        val result = credential.refreshToken()
        assertThat(result).isInstanceOf(OidcClientResult.Error::class.java)
        val exception = (result as OidcClientResult.Error<Token>).exception
        assertThat(exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(exception).hasMessageThat().isEqualTo("Expected From Test!")
    }
}
