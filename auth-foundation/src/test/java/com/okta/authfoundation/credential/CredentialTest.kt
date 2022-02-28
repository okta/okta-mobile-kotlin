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
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.RequestMatchers.header
import com.okta.testhelpers.RequestMatchers.method
import com.okta.testhelpers.RequestMatchers.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
    companion object {
        private const val tokenStorageId: String = "test_storage_id"
    }

    @get:Rule val oktaRule = OktaRule()

    @Test fun testOidcClient() {
        val credential = createCredential()
        assertThat(credential.oidcClient.credential).isEqualTo(credential)
    }

    @Test fun testMetadataChangeThrows() {
        val map = mutableMapOf<String, String>()
        map["name"] = "Default"
        val credential = createCredential(metadata = map)
        assertFailsWith<UnsupportedOperationException> {
            (credential.metadata as MutableMap<String, String>)["secure"] = "true"
        }
    }

    @Test fun testRemove(): Unit = runBlocking {
        val storage = mock<TokenStorage>()
        val credentialDataSource = mock<CredentialDataSource>()
        val token = createToken()
        val credential = createCredential(token = token, tokenStorage = storage, credentialDataSource = credentialDataSource)
        credential.remove()
        verify(credentialDataSource).remove(credential)
        verify(storage).remove(eq(tokenStorageId))
        assertThat(credential.token).isNull()
    }

    @Test fun testRemoveIsNoOpAfterRemove(): Unit = runBlocking {
        val storage = mock<TokenStorage>()
        val credentialDataSource = mock<CredentialDataSource>()
        val token = createToken()
        val credential = createCredential(token = token, tokenStorage = storage, credentialDataSource = credentialDataSource)
        credential.remove()
        credential.remove()
        verify(credentialDataSource).remove(credential)
        verify(storage).remove(eq(tokenStorageId))
        assertThat(credential.token).isNull()
    }

    @Test fun testStoreTokenIsNoOpAfterRemove(): Unit = runBlocking {
        val storage = mock<TokenStorage>()
        val credentialDataSource = mock<CredentialDataSource>()
        val token = createToken()
        val credential = createCredential(token = token, tokenStorage = storage, credentialDataSource = credentialDataSource)
        credential.remove()
        credential.storeToken(token = createToken())
        verify(storage, never()).replace(any())
    }

    @Test fun testRevokeAccessToken(): Unit = runBlocking {
        val oidcClient = mock<OidcClient> {
            onBlocking { revokeToken(any()) } doReturn OidcClientResult.Success(Unit)
            on { withCredential(any()) } doReturn it
        }
        val credential = createCredential(token = createToken(), oidcClient = oidcClient)
        credential.revokeToken(RevokeTokenType.ACCESS_TOKEN)
        verify(oidcClient).revokeToken(eq("exampleAccessToken"))
    }

    @Test fun testRevokeRefreshToken(): Unit = runBlocking {
        val oidcClient = mock<OidcClient> {
            onBlocking { revokeToken(any()) } doReturn OidcClientResult.Success(Unit)
            on { withCredential(any()) } doReturn it
        }
        val credential = createCredential(token = createToken(refreshToken = "exampleRefreshToken"), oidcClient = oidcClient)
        credential.revokeToken(RevokeTokenType.REFRESH_TOKEN)
        verify(oidcClient).revokeToken(eq("exampleRefreshToken"))
    }

    @Test fun testRevokeDeviceSecret(): Unit = runBlocking {
        val oidcClient = mock<OidcClient> {
            onBlocking { revokeToken(any()) } doReturn OidcClientResult.Success(Unit)
            on { withCredential(any()) } doReturn it
        }
        val credential = createCredential(token = createToken(deviceSecret = "exampleDeviceSecret"), oidcClient = oidcClient)
        credential.revokeToken(RevokeTokenType.DEVICE_SECRET)
        verify(oidcClient).revokeToken(eq("exampleDeviceSecret"))
    }

    @Test fun testRevokeTokenWithNullTokenReturnsError(): Unit = runBlocking {
        val credential = createCredential()
        val result = credential.revokeToken()
        assertThat(result).isInstanceOf(OidcClientResult.Error::class.java)
        val errorResult = result as OidcClientResult.Error<Unit>
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No token.")
    }

    @Test fun testRevokeTokenWithNullRefreshTokenReturnsError(): Unit = runBlocking {
        val credential = createCredential(token = createToken())
        val result = credential.revokeToken(RevokeTokenType.REFRESH_TOKEN)
        assertThat(result).isInstanceOf(OidcClientResult.Error::class.java)
        val errorResult = result as OidcClientResult.Error<Unit>
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No refresh token.")
    }

    @Test fun testRevokeTokenWithNullDeviceSecretReturnsError(): Unit = runBlocking {
        val credential = createCredential(token = createToken())
        val result = credential.revokeToken(RevokeTokenType.DEVICE_SECRET)
        assertThat(result).isInstanceOf(OidcClientResult.Error::class.java)
        val errorResult = result as OidcClientResult.Error<Unit>
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No device secret.")
    }

    @Test fun testScopesReturnDefaultScopes() {
        val credential = createCredential()
        assertThat(credential.scopes()).isEqualTo(setOf("openid", "email", "profile", "offline_access"))
    }

    @Test fun testScopesReturnTokenScopes() {
        val credential = createCredential(token = createToken(scope = "openid bank_account"))
        assertThat(credential.scopes()).isEqualTo(setOf("openid", "bank_account"))
    }

    @Test fun testRefreshWithNoToken(): Unit = runBlocking {
        val tokenStorage = mock<TokenStorage>()
        val credential = createCredential(tokenStorage = tokenStorage)
        val result = credential.refreshToken()
        assertThat(result).isInstanceOf(OidcClientResult.Error::class.java)
        val exception = (result as OidcClientResult.Error<Token>).exception
        assertThat(exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(exception).hasMessageThat().isEqualTo("No Token.")
        verifyNoInteractions(tokenStorage)
    }

    @Test fun testRefreshWithNoRefreshToken(): Unit = runBlocking {
        val tokenStorage = mock<TokenStorage>()
        val credential = createCredential(token = createToken(refreshToken = null), tokenStorage = tokenStorage)
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
        val credential = createCredential(
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
        val credential = createCredential(
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
        val credential = createCredential(
            token = createToken(refreshToken = "exampleRefreshToken"),
        )
        oktaRule.enqueue(path("/oauth2/default/v1/token")) { response ->
            val body = oktaRule.configuration.json.encodeToString(createToken(refreshToken = "newRefreshToken"))
            response.setBody(body)
        }
        val result = credential.refreshToken()
        assertThat(result).isInstanceOf(OidcClientResult.Success::class.java)
        val token = (result as OidcClientResult.Success<Token>).result
        assertThat(token.refreshToken).isEqualTo("newRefreshToken")
        assertThat(credential.token?.refreshToken).isEqualTo("newRefreshToken")
    }

    @Test fun testRefreshTokenPreservesDeviceSecret(): Unit = runBlocking {
        val oidcClient = mock<OidcClient> {
            onBlocking { refreshToken(any(), any()) } doReturn OidcClientResult.Success(createToken(refreshToken = "newRefreshToken"))
            on { withCredential(any()) } doReturn it
            on { configuration } doReturn oktaRule.configuration
        }
        val credential = createCredential(
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
        val credential = createCredential(
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
        val credential = createCredential(
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
        val credential = createCredential(tokenStorage = tokenStorage)
        val metadata = mutableMapOf<String, String>()
        metadata["from_test"] = "It works"
        val token = createToken(refreshToken = "stored!")
        credential.storeToken(token = token, metadata = metadata)
        verify(tokenStorage).replace(TokenStorage.Entry(tokenStorageId, token, metadata))
        assertThat(credential.token).isEqualTo(token)
        assertThat(credential.metadata).isEqualTo(metadata)
    }

    @Test fun testStoreTokenKeepsPreviousDeviceSecret(): Unit = runBlocking {
        val tokenStorage = spy(InMemoryTokenStorage())
        val oidcClient = mock<OidcClient> {
            on { withCredential(any()) } doReturn it
            on { configuration } doReturn oktaRule.configuration
        }
        val credential = createCredential(
            token = createToken(refreshToken = "exampleRefreshToken", deviceSecret = "originalDeviceSecret"),
            oidcClient = oidcClient,
            tokenStorage = tokenStorage
        )
        val token = createToken(refreshToken = "stored!")
        credential.storeToken(token = token)
        val expectedToken = createToken(refreshToken = "stored!", deviceSecret = "originalDeviceSecret")
        verify(tokenStorage).replace(TokenStorage.Entry(tokenStorageId, expectedToken, emptyMap()))
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
        val credential = createCredential(
            token = createToken(refreshToken = "exampleRefreshToken", deviceSecret = "originalDeviceSecret"),
            oidcClient = oidcClient,
            tokenStorage = tokenStorage
        )
        val token = createToken(refreshToken = "stored!", deviceSecret = "updatedDeviceSecret")
        credential.storeToken(token = token)
        verify(tokenStorage).replace(TokenStorage.Entry(tokenStorageId, token, emptyMap()))
        assertThat(credential.token).isEqualTo(token)
        assertThat(credential.token?.refreshToken).isEqualTo("stored!")
        assertThat(credential.token?.deviceSecret).isEqualTo("updatedDeviceSecret")
    }

    @Test fun testIdTokenWithNullTokenReturnsNullJwt(): Unit = runBlocking {
        val credential = createCredential(token = null)
        assertThat(credential.idToken()).isNull()
    }

    @Test fun testIdTokenWithNullIdTokenReturnsNullJwt(): Unit = runBlocking {
        val credential = createCredential(token = createToken(idToken = null))
        assertThat(credential.idToken()).isNull()
    }

    @Test fun testIdToken(): Unit = runBlocking {
        val credential = createCredential(token = createToken(idToken = "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6InVuaXRfdGVzdF9jbGllbnRfaWQiLCJpYXQiOjE2NDQzNDcwNjksImV4cCI6MTY0NDM1MDY2OSwianRpIjoiSUQuNTVjeEJ0ZFlsOGw2YXJLSVNQQndkMHlPVC05VUNUYVhhUVRYdDJsYVJMcyIsImFtciI6WyJwd2QiXSwiaWRwIjoiMDBvOGZvdTdzUmFHR3dkbjQ2OTYiLCJzaWQiOiJpZHhXeGtscF80a1N4dUNfblUxcFhELW5BIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiamF5bmV3c3Ryb21AZXhhbXBsZS5jb20iLCJhdXRoX3RpbWUiOjE2NDQzNDcwNjgsImF0X2hhc2giOiJnTWNHVGJoR1QxR19sZHNIb0pzUHpRIiwiZHNfaGFzaCI6IkRBZUxPRlJxaWZ5c2Jnc3JiT2dib2cifQ.tT8aKK4r8yFcW9KgVtZxvjXRJVzz-_rve14CVtpUlyvCTE1yj20wmPS0z3-JirI9xXgt5KeNPYqo3Wbv8c9XY_HY3hsPQdILYpPsUkf-sctmzSoKC_dTbs5xe8uKSgmpMrggfUAWrNPiJt9Ek2p7GgP64Wx79Pq5vSHk0yWlonFfXut5ahpSfqWilmYlvLr8gFbqoLnAJfl4ZbTY8pPw_aQgCdcQ-ImHRu-8bCSCtbFRzZB-SMJFLfRF2kmx0H-QF855wUODTuUSydkff-BKb-8wnbqWg0R9NvRdoXhEybv8TXXZY3cQqgolWLAyiPMrz07n0q_UEjAilUiCjn1f4Q"))
        val idToken = credential.idToken()
        assertThat(idToken).isNotNull()
        assertThat(idToken?.keyId).isEqualTo("FJA0HGNtsuuda_Pl45J42kvQqcsu_0C4Fg7pbJLXTHY")
    }

    @Test fun accessTokenIfValidReturnsNullWithNullToken(): Unit = runBlocking {
        val credential = createCredential(token = null)
        assertThat(credential.accessTokenIfValid()).isNull()
    }

    @Test fun accessTokenIfValidReturnsNullWithExpiredToken(): Unit = runBlocking {
        val credential = createCredential(token = createToken(accessToken = "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJ2ZXIiOjEsImp0aSI6IkFULmFacVZaaTlRd0oyLTR2TFFLTjUyNDJiMFRZLVlzU201b3hybVRQQjRLalUub2FyMXB3M28zbzNadW9icGo2OTYiLCJpc3MiOiJodHRwczovL2V4YW1wbGUub2t0YS5jb20vb2F1dGgyL2RlZmF1bHQiLCJhdWQiOiJhcGk6Ly9kZWZhdWx0IiwiaWF0IjoxNjQ0MzI3MDY5LCJleHAiOjE2NDQzMzcwNjksImNpZCI6IjBvYThmdXAwbEFQWUZDNEkyNjk2IiwidWlkIjoiMDB1YjQxejdtZ3pOcXJ5TXY2OTYiLCJzY3AiOlsib3BlbmlkIiwicHJvZmlsZSIsIm9mZmxpbmVfYWNjZXNzIiwiZW1haWwiLCJkZXZpY2Vfc3NvIl0sInN1YiI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIn0.0Pmlxd94xHkszXa_ira1olljQF8sgrf6zcA08qTzAq7AIwfanQW6dOng2Nd-JJpgO2KAKcBPOJ9qVO--A0wHWbPDuJ2BmasdCyQeYbdwTL-I1TnlXVp_zZUy15tE2Q5nCVQVUzcGsI36d9PD8WhkM8dzzvmVsar7KpGTFstb8N_Fwjo-lsCRPBEhvp1dVXEfbtE5xlNyG2l-HkTpAZqgLQzBJCCd6CmodD-SjKB3ikqblaL7sE7FUrCM7Mxs5YWF8S5TBzOo6SGC95JDomRqHk5Jhq6xmfwPmVywM5jJ8jte5mzGb6cJAj1NWIxawE7nkoeKmKwmIu5mG26an9u2bQ"))
        assertThat(credential.accessTokenIfValid()).isNull()
    }

    @Test fun accessTokenIfValidReturnsAccessToken(): Unit = runBlocking {
        val accessToken = "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJ2ZXIiOjEsImp0aSI6IkFULmFacVZaaTlRd0oyLTR2TFFLTjUyNDJiMFRZLVlzU201b3hybVRQQjRLalUub2FyMXB3M28zbzNadW9icGo2OTYiLCJpc3MiOiJodHRwczovL2V4YW1wbGUub2t0YS5jb20vb2F1dGgyL2RlZmF1bHQiLCJhdWQiOiJhcGk6Ly9kZWZhdWx0IiwiaWF0IjoxNjQ0MzQ3MDY5LCJleHAiOjE2NDQzNTcwNjksImNpZCI6IjBvYThmdXAwbEFQWUZDNEkyNjk2IiwidWlkIjoiMDB1YjQxejdtZ3pOcXJ5TXY2OTYiLCJzY3AiOlsib3BlbmlkIiwicHJvZmlsZSIsIm9mZmxpbmVfYWNjZXNzIiwiZW1haWwiLCJkZXZpY2Vfc3NvIl0sInN1YiI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIn0.IG50UcAHDlHe7_iNq25lqi6DuQ-t1acPlCkLH0KspC_ySwFZECT1S7Z6_KlogxWzqSp5J2h7BNsjBYvQS-nkxExJMp_-YCoTdzSeLJ7rm3h6dKFMDHdIyOXcjg4B1Eo3PiO2SNvpz_u-FRrqiJdY86uWVn5SFAG0RwxdnNxE4uzcH826LJjPZmbVGqdKE0cssdFmrerdoqVY29YBR-kvT7Nj3QeYAAvkhbc01VA1Dnrp4yBTyFwbkFwxLOUKoDA4OSdM2mIiZaJ8W4reDplFTqGzBvk4uuB9BxKFGMWL6IeoRubMiZe-0x7q9k9WlsS58Cf7aE9hAW2rpQs0FvFU0Q"
        val credential = createCredential(token = createToken(accessToken = accessToken))
        assertThat(credential.accessTokenIfValid()).isEqualTo(accessToken)
    }

    @Test fun getValidAccessTokenReturnsAccessToken(): Unit = runBlocking {
        val accessToken = "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJ2ZXIiOjEsImp0aSI6IkFULmFacVZaaTlRd0oyLTR2TFFLTjUyNDJiMFRZLVlzU201b3hybVRQQjRLalUub2FyMXB3M28zbzNadW9icGo2OTYiLCJpc3MiOiJodHRwczovL2V4YW1wbGUub2t0YS5jb20vb2F1dGgyL2RlZmF1bHQiLCJhdWQiOiJhcGk6Ly9kZWZhdWx0IiwiaWF0IjoxNjQ0MzQ3MDY5LCJleHAiOjE2NDQzNTcwNjksImNpZCI6IjBvYThmdXAwbEFQWUZDNEkyNjk2IiwidWlkIjoiMDB1YjQxejdtZ3pOcXJ5TXY2OTYiLCJzY3AiOlsib3BlbmlkIiwicHJvZmlsZSIsIm9mZmxpbmVfYWNjZXNzIiwiZW1haWwiLCJkZXZpY2Vfc3NvIl0sInN1YiI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIn0.IG50UcAHDlHe7_iNq25lqi6DuQ-t1acPlCkLH0KspC_ySwFZECT1S7Z6_KlogxWzqSp5J2h7BNsjBYvQS-nkxExJMp_-YCoTdzSeLJ7rm3h6dKFMDHdIyOXcjg4B1Eo3PiO2SNvpz_u-FRrqiJdY86uWVn5SFAG0RwxdnNxE4uzcH826LJjPZmbVGqdKE0cssdFmrerdoqVY29YBR-kvT7Nj3QeYAAvkhbc01VA1Dnrp4yBTyFwbkFwxLOUKoDA4OSdM2mIiZaJ8W4reDplFTqGzBvk4uuB9BxKFGMWL6IeoRubMiZe-0x7q9k9WlsS58Cf7aE9hAW2rpQs0FvFU0Q"
        val credential = createCredential(token = createToken(accessToken = accessToken))
        assertThat(credential.getValidAccessToken()).isEqualTo(accessToken)
    }

    @Test fun getValidAccessTokenReturnsNullWhenAccessTokenExpiredAndNoRefreshToken(): Unit = runBlocking {
        val accessToken = "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJ2ZXIiOjEsImp0aSI6IkFULmFacVZaaTlRd0oyLTR2TFFLTjUyNDJiMFRZLVlzU201b3hybVRQQjRLalUub2FyMXB3M28zbzNadW9icGo2OTYiLCJpc3MiOiJodHRwczovL2V4YW1wbGUub2t0YS5jb20vb2F1dGgyL2RlZmF1bHQiLCJhdWQiOiJhcGk6Ly9kZWZhdWx0IiwiaWF0IjoxNjQ0MzI3MDY5LCJleHAiOjE2NDQzMzcwNjksImNpZCI6IjBvYThmdXAwbEFQWUZDNEkyNjk2IiwidWlkIjoiMDB1YjQxejdtZ3pOcXJ5TXY2OTYiLCJzY3AiOlsib3BlbmlkIiwicHJvZmlsZSIsIm9mZmxpbmVfYWNjZXNzIiwiZW1haWwiLCJkZXZpY2Vfc3NvIl0sInN1YiI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIn0.0Pmlxd94xHkszXa_ira1olljQF8sgrf6zcA08qTzAq7AIwfanQW6dOng2Nd-JJpgO2KAKcBPOJ9qVO--A0wHWbPDuJ2BmasdCyQeYbdwTL-I1TnlXVp_zZUy15tE2Q5nCVQVUzcGsI36d9PD8WhkM8dzzvmVsar7KpGTFstb8N_Fwjo-lsCRPBEhvp1dVXEfbtE5xlNyG2l-HkTpAZqgLQzBJCCd6CmodD-SjKB3ikqblaL7sE7FUrCM7Mxs5YWF8S5TBzOo6SGC95JDomRqHk5Jhq6xmfwPmVywM5jJ8jte5mzGb6cJAj1NWIxawE7nkoeKmKwmIu5mG26an9u2bQ"
        val credential = createCredential(token = createToken(accessToken = accessToken, refreshToken = null))
        assertThat(credential.getValidAccessToken()).isNull()
    }

    @Test fun getValidAccessTokenRefreshesWhenAccessTokenExpired(): Unit = runBlocking {
        val expiredAccessToken = "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJ2ZXIiOjEsImp0aSI6IkFULmFacVZaaTlRd0oyLTR2TFFLTjUyNDJiMFRZLVlzU201b3hybVRQQjRLalUub2FyMXB3M28zbzNadW9icGo2OTYiLCJpc3MiOiJodHRwczovL2V4YW1wbGUub2t0YS5jb20vb2F1dGgyL2RlZmF1bHQiLCJhdWQiOiJhcGk6Ly9kZWZhdWx0IiwiaWF0IjoxNjQ0MzI3MDY5LCJleHAiOjE2NDQzMzcwNjksImNpZCI6IjBvYThmdXAwbEFQWUZDNEkyNjk2IiwidWlkIjoiMDB1YjQxejdtZ3pOcXJ5TXY2OTYiLCJzY3AiOlsib3BlbmlkIiwicHJvZmlsZSIsIm9mZmxpbmVfYWNjZXNzIiwiZW1haWwiLCJkZXZpY2Vfc3NvIl0sInN1YiI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIn0.0Pmlxd94xHkszXa_ira1olljQF8sgrf6zcA08qTzAq7AIwfanQW6dOng2Nd-JJpgO2KAKcBPOJ9qVO--A0wHWbPDuJ2BmasdCyQeYbdwTL-I1TnlXVp_zZUy15tE2Q5nCVQVUzcGsI36d9PD8WhkM8dzzvmVsar7KpGTFstb8N_Fwjo-lsCRPBEhvp1dVXEfbtE5xlNyG2l-HkTpAZqgLQzBJCCd6CmodD-SjKB3ikqblaL7sE7FUrCM7Mxs5YWF8S5TBzOo6SGC95JDomRqHk5Jhq6xmfwPmVywM5jJ8jte5mzGb6cJAj1NWIxawE7nkoeKmKwmIu5mG26an9u2bQ"
        val validAccessToken = "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJ2ZXIiOjEsImp0aSI6IkFULmFacVZaaTlRd0oyLTR2TFFLTjUyNDJiMFRZLVlzU201b3hybVRQQjRLalUub2FyMXB3M28zbzNadW9icGo2OTYiLCJpc3MiOiJodHRwczovL2V4YW1wbGUub2t0YS5jb20vb2F1dGgyL2RlZmF1bHQiLCJhdWQiOiJhcGk6Ly9kZWZhdWx0IiwiaWF0IjoxNjQ0MzQ3MDY5LCJleHAiOjE2NDQzNTcwNjksImNpZCI6IjBvYThmdXAwbEFQWUZDNEkyNjk2IiwidWlkIjoiMDB1YjQxejdtZ3pOcXJ5TXY2OTYiLCJzY3AiOlsib3BlbmlkIiwicHJvZmlsZSIsIm9mZmxpbmVfYWNjZXNzIiwiZW1haWwiLCJkZXZpY2Vfc3NvIl0sInN1YiI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIn0.IG50UcAHDlHe7_iNq25lqi6DuQ-t1acPlCkLH0KspC_ySwFZECT1S7Z6_KlogxWzqSp5J2h7BNsjBYvQS-nkxExJMp_-YCoTdzSeLJ7rm3h6dKFMDHdIyOXcjg4B1Eo3PiO2SNvpz_u-FRrqiJdY86uWVn5SFAG0RwxdnNxE4uzcH826LJjPZmbVGqdKE0cssdFmrerdoqVY29YBR-kvT7Nj3QeYAAvkhbc01VA1Dnrp4yBTyFwbkFwxLOUKoDA4OSdM2mIiZaJ8W4reDplFTqGzBvk4uuB9BxKFGMWL6IeoRubMiZe-0x7q9k9WlsS58Cf7aE9hAW2rpQs0FvFU0Q"
        oktaRule.enqueue(path("/oauth2/default/v1/token")) { response ->
            val body = oktaRule.configuration.json.encodeToString(createToken(accessToken = validAccessToken))
            response.setBody(body)
        }

        val credential = createCredential(token = createToken(accessToken = expiredAccessToken, refreshToken = "exampleRefreshToken"))
        assertThat(credential.getValidAccessToken()).isEqualTo(validAccessToken)
    }

    @Test fun getValidAccessTokenReturnsNullWhenRefreshedAccessTokenExpired(): Unit = runBlocking {
        val expiredAccessToken = "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJ2ZXIiOjEsImp0aSI6IkFULmFacVZaaTlRd0oyLTR2TFFLTjUyNDJiMFRZLVlzU201b3hybVRQQjRLalUub2FyMXB3M28zbzNadW9icGo2OTYiLCJpc3MiOiJodHRwczovL2V4YW1wbGUub2t0YS5jb20vb2F1dGgyL2RlZmF1bHQiLCJhdWQiOiJhcGk6Ly9kZWZhdWx0IiwiaWF0IjoxNjQ0MzI3MDY5LCJleHAiOjE2NDQzMzcwNjksImNpZCI6IjBvYThmdXAwbEFQWUZDNEkyNjk2IiwidWlkIjoiMDB1YjQxejdtZ3pOcXJ5TXY2OTYiLCJzY3AiOlsib3BlbmlkIiwicHJvZmlsZSIsIm9mZmxpbmVfYWNjZXNzIiwiZW1haWwiLCJkZXZpY2Vfc3NvIl0sInN1YiI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIn0.0Pmlxd94xHkszXa_ira1olljQF8sgrf6zcA08qTzAq7AIwfanQW6dOng2Nd-JJpgO2KAKcBPOJ9qVO--A0wHWbPDuJ2BmasdCyQeYbdwTL-I1TnlXVp_zZUy15tE2Q5nCVQVUzcGsI36d9PD8WhkM8dzzvmVsar7KpGTFstb8N_Fwjo-lsCRPBEhvp1dVXEfbtE5xlNyG2l-HkTpAZqgLQzBJCCd6CmodD-SjKB3ikqblaL7sE7FUrCM7Mxs5YWF8S5TBzOo6SGC95JDomRqHk5Jhq6xmfwPmVywM5jJ8jte5mzGb6cJAj1NWIxawE7nkoeKmKwmIu5mG26an9u2bQ"
        val oidcClient = mock<OidcClient> {
            onBlocking { refreshToken(any(), any()) } doAnswer {
                OidcClientResult.Success(createToken(accessToken = expiredAccessToken))
            }
            on { withCredential(any()) } doReturn it
            on { configuration } doReturn oktaRule.configuration
        }
        val credential = createCredential(oidcClient = oidcClient, token = createToken(accessToken = expiredAccessToken, refreshToken = "exampleRefreshToken"))
        assertThat(credential.getValidAccessToken()).isNull()
        verify(oidcClient).refreshToken(any(), any())
    }

    @Test fun testInterceptor() {
        val accessToken = "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJ2ZXIiOjEsImp0aSI6IkFULmFacVZaaTlRd0oyLTR2TFFLTjUyNDJiMFRZLVlzU201b3hybVRQQjRLalUub2FyMXB3M28zbzNadW9icGo2OTYiLCJpc3MiOiJodHRwczovL2V4YW1wbGUub2t0YS5jb20vb2F1dGgyL2RlZmF1bHQiLCJhdWQiOiJhcGk6Ly9kZWZhdWx0IiwiaWF0IjoxNjQ0MzQ3MDY5LCJleHAiOjE2NDQzNTcwNjksImNpZCI6IjBvYThmdXAwbEFQWUZDNEkyNjk2IiwidWlkIjoiMDB1YjQxejdtZ3pOcXJ5TXY2OTYiLCJzY3AiOlsib3BlbmlkIiwicHJvZmlsZSIsIm9mZmxpbmVfYWNjZXNzIiwiZW1haWwiLCJkZXZpY2Vfc3NvIl0sInN1YiI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIn0.IG50UcAHDlHe7_iNq25lqi6DuQ-t1acPlCkLH0KspC_ySwFZECT1S7Z6_KlogxWzqSp5J2h7BNsjBYvQS-nkxExJMp_-YCoTdzSeLJ7rm3h6dKFMDHdIyOXcjg4B1Eo3PiO2SNvpz_u-FRrqiJdY86uWVn5SFAG0RwxdnNxE4uzcH826LJjPZmbVGqdKE0cssdFmrerdoqVY29YBR-kvT7Nj3QeYAAvkhbc01VA1Dnrp4yBTyFwbkFwxLOUKoDA4OSdM2mIiZaJ8W4reDplFTqGzBvk4uuB9BxKFGMWL6IeoRubMiZe-0x7q9k9WlsS58Cf7aE9hAW2rpQs0FvFU0Q"

        oktaRule.enqueue(
            method("GET"),
            header("authorization", "Bearer $accessToken"),
            path("/customers"),
        ) { response ->
            response.setBody("[]")
        }

        val credential = createCredential(token = createToken(accessToken = accessToken))
        val interceptor = credential.accessTokenInterceptor()
        val okHttpClient = oktaRule.okHttpClient.newBuilder().addInterceptor(interceptor).build()
        val url = oktaRule.baseUrl.newBuilder().addPathSegment("customers").build()
        val call = okHttpClient.newCall(Request.Builder().url(url).build())
        val response = call.execute()
        assertThat(response.body!!.string()).isEqualTo("[]")
    }

    private fun createCredential(
        token: Token? = null,
        metadata: Map<String, String> = emptyMap(),
        oidcClient: OidcClient = oktaRule.createOidcClient(),
        tokenStorage: TokenStorage = InMemoryTokenStorage(),
        credentialDataSource: CredentialDataSource = mock(),
    ): Credential {
        if (token != null) {
            runBlocking {
                tokenStorage.add(tokenStorageId)
            }
        }
        return Credential(oidcClient, tokenStorage, credentialDataSource, tokenStorageId, token, metadata)
    }

    private fun createToken(
        scope: String = "openid email profile offline_access",
        accessToken: String = "exampleAccessToken",
        idToken: String? = null,
        refreshToken: String? = null,
        deviceSecret: String? = null,
    ): Token {
        return Token(
            tokenType = "Bearer",
            expiresIn = 3600,
            accessToken = accessToken,
            scope = scope,
            refreshToken = refreshToken,
            deviceSecret = deviceSecret,
            idToken = idToken,
        )
    }
}
