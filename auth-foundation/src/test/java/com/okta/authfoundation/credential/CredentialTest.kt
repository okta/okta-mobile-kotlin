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

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.AuthFoundation
import com.okta.authfoundation.client.ApplicationContextHolder
import com.okta.authfoundation.client.OAuth2Client
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.dto.OidcIntrospectInfo
import com.okta.authfoundation.client.dto.OidcUserInfo
import com.okta.authfoundation.client.events.TokenCreatedEvent
import com.okta.authfoundation.credential.events.CredentialDeletedEvent
import com.okta.authfoundation.credential.events.CredentialStoredAfterRemovedEvent
import com.okta.authfoundation.credential.events.CredentialStoredEvent
import com.okta.authfoundation.credential.events.DefaultCredentialChangedEvent
import com.okta.authfoundation.jwt.IdTokenClaims
import com.okta.authfoundation.jwt.JwtBuilder.Companion.createJwtBuilder
import com.okta.testhelpers.MockAesEncryptionHandler
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.RequestMatchers.header
import com.okta.testhelpers.RequestMatchers.method
import com.okta.testhelpers.RequestMatchers.path
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.Request
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class CredentialTest {
    @get:Rule val oktaRule = OktaRule()

    private val token = createToken()
    private lateinit var credentialDataSource: CredentialDataSource
    private lateinit var client: OAuth2Client
    private lateinit var defaultCredentialIdDataStore: DefaultCredentialIdDataStore

    @Before
    fun setUp() {
        mockkObject(CredentialDataSource)
        mockkObject(ApplicationContextHolder)
        every { ApplicationContextHolder.appContext } returns ApplicationProvider.getApplicationContext()
        mockkObject(DefaultCredentialIdDataStore)
        defaultCredentialIdDataStore = DefaultCredentialIdDataStore(MockAesEncryptionHandler.getInstance())
        every { DefaultCredentialIdDataStore.instance } returns defaultCredentialIdDataStore

        credentialDataSource = mockk(relaxed = true)
        client = mockk {
            coEvery { revokeToken(any(), any()) } returns OAuth2ClientResult.Success(Unit)
            every { configuration } returns oktaRule.configuration
        }
        mockkObject(AuthFoundation)
        coEvery { AuthFoundation.initializeStorage() } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `default returns null if no default credential is set`() {
        assertThat(Credential.default).isNull()
    }

    @Test
    fun `default returns the default credential`() {
        val credential = oktaRule.createCredential(createToken())
        val credentialDataSource = mockk<CredentialDataSource> {
            coEvery { getCredential(credential.id, any()) } returns credential
        }
        coEvery { CredentialDataSource.getInstance() } returns credentialDataSource
        Credential.default = credential
        assertThat(Credential.default).isEqualTo(credential)
    }

    @Test
    fun `getDefaultAsync returns null if no default credential is set`() = runTest {
        assertThat(Credential.getDefaultAsync()).isNull()
    }

    @Test
    fun `getDefaultAsync returns the default credential`() = runTest {
        val credential = oktaRule.createCredential(createToken())
        val credentialDataSource = mockk<CredentialDataSource> {
            coEvery { getCredential(credential.id, any()) } returns credential
        }
        coEvery { CredentialDataSource.getInstance() } returns credentialDataSource
        Credential.setDefaultAsync(credential)
        assertThat(Credential.getDefaultAsync()).isEqualTo(credential)
    }

    @Test
    fun `allIds returns ids of all available credentials`() = runTest {
        val testIds = listOf("credId1", "credId2")
        val credentialDataSource = mockk<CredentialDataSource> {
            coEvery { allIds() } returns testIds
        }
        coEvery { CredentialDataSource.getInstance() } returns credentialDataSource
        assertThat(Credential.allIds).isEqualTo(testIds)
        assertThat(Credential.allIdsAsync()).isEqualTo(testIds)
        coVerify { credentialDataSource.allIds() }
    }

    @Test
    fun `get metadata of credential`() = runTest {
        val testId = "id1"
        val expectedMetadata = Token.Metadata("id1", emptyMap(), idToken = null)
        val credentialDataSource = mockk<CredentialDataSource> {
            coEvery { metadata(testId) } returns expectedMetadata
        }
        coEvery { CredentialDataSource.getInstance() } returns credentialDataSource
        assertThat(Credential.metadata(testId)).isEqualTo(expectedMetadata)
        assertThat(Credential.metadataAsync(testId)).isEqualTo(expectedMetadata)
        coVerify { credentialDataSource.metadata(testId) }
    }

    @Test
    fun `set metadata sets new metadata for credential`() = runTest {
        val testId = "id1"
        val oldMetadata = Token.Metadata("id1", emptyMap(), idToken = null)
        var storedMetadata: Token.Metadata = oldMetadata
        val newMetadata = Token.Metadata("id1", mapOf("key" to "value"), idToken = null)
        val newerMetadata = Token.Metadata("id1", mapOf("newKey" to "newValue"), idToken = null)
        val credentialDataSource = mockk<CredentialDataSource> {
            coEvery { setMetadata(any()) } answers {
                storedMetadata = it.invocation.args[0] as Token.Metadata
            }
            coEvery { metadata(testId) } answers { storedMetadata }
        }
        coEvery { CredentialDataSource.getInstance() } returns credentialDataSource

        assertThat(Credential.metadata("id1")).isEqualTo(oldMetadata)
        Credential.setMetadata(newMetadata)
        assertThat(Credential.metadata("id1")).isEqualTo(newMetadata)
        Credential.setMetadataAsync(newerMetadata)
        assertThat(Credential.metadata("id1")).isEqualTo(newerMetadata)
    }

    @Test
    fun `with returns token with given id`() = runTest {
        val testId = "testId"
        val credential = mockk<Credential>()
        val credentialDataSource = mockk<CredentialDataSource> {
            coEvery { getCredential(testId) } returns credential
        }
        coEvery { CredentialDataSource.getInstance() } returns credentialDataSource

        assertThat(Credential.with(testId)).isEqualTo(credential)
        assertThat(Credential.withAsync(testId)).isEqualTo(credential)
        coVerify { credentialDataSource.getCredential(testId) }
    }

    @Test
    fun `find returns tokens matching the expression`() = runTest {
        val credential = mockk<Credential>()
        val findExpression = mockk<(Token.Metadata) -> Boolean>()
        val credentialDataSource = mockk<CredentialDataSource> {
            coEvery { findCredential(where = findExpression) } returns listOf(credential)
        }
        coEvery { CredentialDataSource.getInstance() } returns credentialDataSource

        assertThat(Credential.find(where = findExpression)).isEqualTo(listOf(credential))
        assertThat(Credential.findAsync(where = findExpression)).isEqualTo(listOf(credential))
        coVerify { credentialDataSource.findCredential(where = findExpression) }
    }

    @Test
    fun `store creates a new credential`() = runTest {
        val credential = mockk<Credential>()
        val credentialDataSource = mockk<CredentialDataSource> {
            coEvery { createCredential(token) } returns credential
        }
        coEvery { CredentialDataSource.getInstance() } returns credentialDataSource

        assertThat(Credential.store(token)).isEqualTo(credential)
        coVerify { credentialDataSource.createCredential(token) }
    }

    @Test fun `changing tag directly without using setTags throws`() {
        // This behavior exists because changing tags is a suspend function since it accesses TokenStorage
        val map = mutableMapOf<String, String>()
        map["name"] = "Default"
        val credential = oktaRule.createCredential(token, tags = map)
        assertFailsWith<UnsupportedOperationException> {
            (credential.tags as MutableMap<String, String>)["secure"] = "true"
        }
    }

    @Test
    fun `setTags changes credential metadata`() = runTest {
        val oldTags = mapOf("key" to "value")
        val newTags = mapOf("newKey" to "newValue")
        val newerTags = mapOf("newerKey" to "newerValue")
        val credential = oktaRule.createCredential(token, tags = oldTags)

        val credentialDataSource = mockk<CredentialDataSource>(relaxed = true)
        coEvery { CredentialDataSource.getInstance() } returns credentialDataSource

        assertThat(credential.tags).isEqualTo(oldTags)
        credential.setTagsAsync(newTags)
        assertThat(credential.tags).isEqualTo(newTags)
        credential.tags = newerTags
        assertThat(credential.tags).isEqualTo(newerTags)
        coVerify { credentialDataSource.setMetadata(Token.Metadata(credential.id, newTags, idToken = null)) }
        coVerify(exactly = 2) { credentialDataSource.setMetadata(any()) }
        coVerify(exactly = 0) { credentialDataSource.metadata(credential.id) }
    }

    @Test fun testGetTokenFlow(): Unit = runTest {
        val token = createToken()
        val token2 = createToken(accessToken = "token 2")
        val credential = oktaRule.createCredential(token = token, credentialDataSource = credentialDataSource)
        credential.getTokenFlow().test {
            assertThat(awaitItem()).isEqualTo(token)
            credential.replaceToken(token2)
            assertThat(awaitItem()).isEqualTo(token2)
            credential.delete()
            awaitComplete()
        }
    }

    @Test fun testDelete(): Unit = runTest {
        val token = createToken()
        val credential = oktaRule.createCredential(token = token, credentialDataSource = credentialDataSource)
        credential.delete()
        coVerify { credentialDataSource.remove(credential) }
        assertThat(credential.isDeleted).isTrue()
        assertThat(oktaRule.eventHandler).hasSize(1)
        val event = oktaRule.eventHandler[0]
        assertThat(event).isInstanceOf(CredentialDeletedEvent::class.java)
        val deletedEvent = event as CredentialDeletedEvent
        assertThat(deletedEvent.credential).isEqualTo(credential)
    }

    @Test
    fun `delete clears default credential if deleted credential is default`() = runTest {
        val token = createToken()
        val credential = oktaRule.createCredential(token = token, credentialDataSource = credentialDataSource)
        Credential.setDefaultAsync(credential)
        assertThat(defaultCredentialIdDataStore.getDefaultCredentialId()).isEqualTo(credential.id)
        credential.delete()

        coVerify { credentialDataSource.remove(credential) }
        assertThat(credential.isDeleted).isTrue()
        assertThat(oktaRule.eventHandler).hasSize(2)
        val event1 = oktaRule.eventHandler[0]
        assertThat(event1).isInstanceOf(DefaultCredentialChangedEvent::class.java)
        val event2 = oktaRule.eventHandler[1]
        assertThat(event2).isInstanceOf(CredentialDeletedEvent::class.java)
        val deletedEvent = event2 as CredentialDeletedEvent
        assertThat(deletedEvent.credential).isEqualTo(credential)
        assertThat(defaultCredentialIdDataStore.getDefaultCredentialId()).isNull()
    }

    @Test fun testRemoveIsNoOpAfterRemove(): Unit = runTest {
        val token = createToken()
        val credential = oktaRule.createCredential(token = token, credentialDataSource = credentialDataSource)
        credential.delete()
        credential.delete()
        coVerify(exactly = 1) { credentialDataSource.remove(credential) }
        assertThat(credential.isDeleted).isTrue()
    }

    @Test fun testReplaceTokenIsNoOpAfterRemove(): Unit = runTest {
        val token = createToken()
        val credential = oktaRule.createCredential(token = token, credentialDataSource = credentialDataSource)
        credential.delete()
        credential.replaceToken(token = createToken())
        coVerify(exactly = 0) { credentialDataSource.replaceToken(any()) }
    }

    @Test fun testReplaceTokenEmitsEventAfterRemove(): Unit = runTest {
        val token = createToken()
        val credential = oktaRule.createCredential(token = token, credentialDataSource = credentialDataSource)
        credential.delete()
        assertThat(oktaRule.eventHandler).hasSize(1)
        credential.replaceToken(token = createToken())
        assertThat(oktaRule.eventHandler).hasSize(2)
        val event = oktaRule.eventHandler[1]
        assertThat(event).isInstanceOf(CredentialStoredAfterRemovedEvent::class.java)
        val removedEvent = event as CredentialStoredAfterRemovedEvent
        assertThat(removedEvent.credential).isEqualTo(credential)
    }

    @Test fun testGetTokenFlowReturnsEmptyFlowAfterRemove(): Unit = runTest {
        val token = createToken()
        val credential = oktaRule.createCredential(token = token, credentialDataSource = credentialDataSource)
        credential.delete()
        credential.getTokenFlow().test {
            awaitComplete()
        }
    }

    @Test fun testRevokeAccessToken(): Unit = runTest {
        val token = createToken()
        val credential = oktaRule.createCredential(token = token, oauthClient = client)
        credential.revokeToken(RevokeTokenType.ACCESS_TOKEN)
        coVerify { client.revokeToken(RevokeTokenType.ACCESS_TOKEN, token) }
    }

    @Test fun testRevokeRefreshToken(): Unit = runTest {
        val token = createToken(refreshToken = "exampleRefreshToken")
        val credential = oktaRule.createCredential(token = token, oauthClient = client)
        credential.revokeToken(RevokeTokenType.REFRESH_TOKEN)
        coVerify { client.revokeToken(RevokeTokenType.REFRESH_TOKEN, token) }
    }

    @Test fun testRevokeDeviceSecret(): Unit = runTest {
        val token = createToken(deviceSecret = "exampleDeviceSecret")
        val credential = oktaRule.createCredential(token = token, oauthClient = client)
        credential.revokeToken(RevokeTokenType.DEVICE_SECRET)
        coVerify { client.revokeToken(RevokeTokenType.DEVICE_SECRET, token) }
    }

    @Test fun testRevokeTokenWithNullRefreshTokenReturnsError(): Unit = runTest {
        val credential = oktaRule.createCredential(token = createToken())
        val result = credential.revokeToken(RevokeTokenType.REFRESH_TOKEN)
        assertThat(result).isInstanceOf(OAuth2ClientResult.Error::class.java)
        val errorResult = result as OAuth2ClientResult.Error<Unit>
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No refresh token.")
    }

    @Test fun testRevokeTokenWithNullDeviceSecretReturnsError(): Unit = runTest {
        val credential = oktaRule.createCredential(token = createToken())
        val result = credential.revokeToken(RevokeTokenType.DEVICE_SECRET)
        assertThat(result).isInstanceOf(OAuth2ClientResult.Error::class.java)
        val errorResult = result as OAuth2ClientResult.Error<Unit>
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No device secret.")
    }

    @Test fun testRevokeAllTokensWithOnlyAccessToken(): Unit = runTest {
        val token = createToken()
        val credential = oktaRule.createCredential(token = token, oauthClient = client)
        credential.revokeAllTokens()
        coVerify { client.revokeToken(RevokeTokenType.ACCESS_TOKEN, token) }
        coVerify(exactly = 1) { client.revokeToken(any(), any()) }
    }

    @Test fun testRevokeAllTokensWithRefreshToken(): Unit = runTest {
        val token = createToken(refreshToken = "exampleRefreshToken")
        val credential = oktaRule.createCredential(token = token, oauthClient = client)
        credential.revokeAllTokens()
        coVerify { client.revokeToken(RevokeTokenType.ACCESS_TOKEN, token) }
        coVerify { client.revokeToken(RevokeTokenType.REFRESH_TOKEN, token) }
        coVerify(exactly = 2) { client.revokeToken(any(), any()) }
    }

    @Test fun testRevokeAllTokensWithDeviceSecret(): Unit = runTest {
        val token = createToken(deviceSecret = "exampleDeviceSecret")
        val credential = oktaRule.createCredential(token = token, oauthClient = client)
        credential.revokeAllTokens()
        coVerify { client.revokeToken(RevokeTokenType.ACCESS_TOKEN, token) }
        coVerify { client.revokeToken(RevokeTokenType.DEVICE_SECRET, token) }
        coVerify(exactly = 2) { client.revokeToken(any(), any()) }
    }

    @Test fun testRevokeAllTokensWithRefreshTokenAndDeviceSecret(): Unit = runTest {
        val token = createToken(refreshToken = "exampleRefreshToken", deviceSecret = "exampleDeviceSecret")
        val credential = oktaRule.createCredential(token = token, oauthClient = client)
        credential.revokeAllTokens()
        coVerify { client.revokeToken(RevokeTokenType.ACCESS_TOKEN, token) }
        coVerify { client.revokeToken(RevokeTokenType.REFRESH_TOKEN, token) }
        coVerify { client.revokeToken(RevokeTokenType.DEVICE_SECRET, token) }
        coVerify(exactly = 3) { client.revokeToken(any(), any()) }
    }

    @Test fun testRevokeAllTokensWithFailedRefreshTokenReturnsError(): Unit = runTest {
        coEvery { client.revokeToken(any(), any()) } answers {
            val revokeTokenType = it.invocation.args[0] as RevokeTokenType
            val tokenArg = it.invocation.args[1] as Token
            if (revokeTokenType == RevokeTokenType.REFRESH_TOKEN && tokenArg.refreshToken == "exampleRefreshToken") {
                OAuth2ClientResult.Error(Exception("Expected Error."))
            } else {
                OAuth2ClientResult.Success(Unit)
            }
        }
        val token = createToken(refreshToken = "exampleRefreshToken")
        val credential = oktaRule.createCredential(token = token, oauthClient = client)

        val result = credential.revokeAllTokens()

        assertThat(result).isInstanceOf(OAuth2ClientResult.Error::class.java)
        val errorResult = result as OAuth2ClientResult.Error<Unit>
        val exception = errorResult.exception as RevokeAllException
        assertThat(exception).hasMessageThat().isEqualTo("Failed to revoke all tokens.")
        assertThat(exception.failures).hasSize(1)
        assertThat(exception.failures[RevokeTokenType.REFRESH_TOKEN]).hasMessageThat().isEqualTo("Expected Error.")

        coVerify { client.revokeToken(RevokeTokenType.ACCESS_TOKEN, token) }
        coVerify { client.revokeToken(RevokeTokenType.REFRESH_TOKEN, token) }
        coVerify(exactly = 2) { client.revokeToken(any(), any()) }
    }

    @Test fun testScopesReturnDefaultScopes() {
        val credential = oktaRule.createCredential(token)
        assertThat(credential.scope()).isEqualTo("openid email profile offline_access")
    }

    @Test fun testScopesReturnTokenScopes() {
        val credential = oktaRule.createCredential(token = createToken(scope = "openid bank_account"))
        assertThat(credential.scope()).isEqualTo("openid bank_account")
    }

    @Test fun testRefreshWithNoRefreshToken(): Unit = runTest {
        val credentialDataSource = mockk<CredentialDataSource>(relaxed = true)
        val credential = oktaRule.createCredential(token = createToken(refreshToken = null), credentialDataSource = credentialDataSource)
        val result = credential.refreshToken()
        assertThat(result).isInstanceOf(OAuth2ClientResult.Error::class.java)
        val exception = (result as OAuth2ClientResult.Error<Token>).exception
        assertThat(exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(exception).hasMessageThat().isEqualTo("No refresh token.")
        coVerify(exactly = 0) { credentialDataSource.replaceToken(any()) }
    }

    @Test fun testRefreshTokenForwardsError(): Unit = runTest {
        val credentialDataSource = mockk<CredentialDataSource>()
        val OAuth2Client = mockk<OAuth2Client> {
            coEvery { refreshToken(any()) } returns OAuth2ClientResult.Error(Exception("From Test"))
            every { configuration } returns oktaRule.configuration
        }
        val token = createToken(refreshToken = "exampleRefreshToken")
        val credential = oktaRule.createCredential(
            token = token,
            oauthClient = OAuth2Client,
            credentialDataSource = credentialDataSource
        )
        val result = credential.refreshToken()
        coVerify { OAuth2Client.refreshToken(token) }
        assertThat(result).isInstanceOf(OAuth2ClientResult.Error::class.java)
        val exception = (result as OAuth2ClientResult.Error<Token>).exception
        assertThat(exception).hasMessageThat().isEqualTo("From Test")
        coVerify(exactly = 0) { credentialDataSource.replaceToken(any()) }
    }

    @Test fun testRefreshToken(): Unit = runTest {
        coEvery { client.refreshToken(any()) } returns OAuth2ClientResult.Success(createToken(refreshToken = "newRefreshToken"))
        val tokenToRefresh = createToken(refreshToken = "exampleRefreshToken")
        val credential = oktaRule.createCredential(
            token = tokenToRefresh,
            oauthClient = client
        )
        val result = credential.refreshToken()
        coVerify { client.refreshToken(tokenToRefresh) }
        assertThat(result).isInstanceOf(OAuth2ClientResult.Success::class.java)
        val token = (result as OAuth2ClientResult.Success<Token>).result
        assertThat(token.refreshToken).isEqualTo("newRefreshToken")
    }

    @Test fun testRefreshTokenWithRealOAuth2Client(): Unit = runTest {
        val credential = oktaRule.createCredential(
            token = createToken(id = "id1", refreshToken = "exampleRefreshToken"),
            credentialDataSource = credentialDataSource
        )
        coEvery { CredentialDataSource.getInstance() } returns credentialDataSource
        coEvery { credentialDataSource.replaceToken(match { it.id == "id1" }) } coAnswers {
            credential.replaceToken(it.invocation.args[0] as Token)
        }

        oktaRule.enqueue(path("/oauth2/default/v1/token")) { response ->
            val body = oktaRule.configuration.json.encodeToString(createToken(refreshToken = "newRefreshToken").asSerializableToken())
            response.setBody(body)
        }
        val result = credential.refreshToken()
        assertThat(result).isInstanceOf(OAuth2ClientResult.Success::class.java)
        val token = (result as OAuth2ClientResult.Success<Token>).result
        assertThat(token.refreshToken).isEqualTo("newRefreshToken")
        assertThat(credential.token.refreshToken).isEqualTo("newRefreshToken")
    }

    @Test fun testRefreshTokenWithRealOAuth2ClientPreservesRefreshToken(): Unit = runTest {
        val credential = oktaRule.createCredential(
            token = createToken(refreshToken = "exampleRefreshToken"),
        )
        oktaRule.enqueue(path("/oauth2/default/v1/token")) { response ->
            val body = oktaRule.configuration.json.encodeToString(createToken(refreshToken = null).asSerializableToken())
            response.setBody(body)
        }
        val result = credential.refreshToken()
        assertThat(result).isInstanceOf(OAuth2ClientResult.Success::class.java)
        val token = (result as OAuth2ClientResult.Success<Token>).result
        assertThat(token.refreshToken).isNull()
        assertThat(credential.token.refreshToken).isEqualTo("exampleRefreshToken")
    }

    @Test fun testRefreshTokenWithRealOAuth2ClientEmitsEvent(): Unit = runTest {
        val credential = oktaRule.createCredential(
            token = createToken(refreshToken = "exampleRefreshToken"),
        )
        coEvery { CredentialDataSource.getInstance() } returns credentialDataSource
        coEvery { credentialDataSource.replaceToken(match { it.id == credential.id }) } coAnswers {
            credential.replaceToken(it.invocation.args[0] as Token)
        }
        oktaRule.enqueue(path("/oauth2/default/v1/token")) { response ->
            val body = oktaRule.configuration.json.encodeToString(createToken(refreshToken = "newRefreshToken").asSerializableToken())
            response.setBody(body)
        }
        val result = credential.refreshToken()
        assertThat(result).isInstanceOf(OAuth2ClientResult.Success::class.java)
        val token = (result as OAuth2ClientResult.Success<Token>).result
        assertThat(oktaRule.eventHandler).hasSize(2)

        val event1 = oktaRule.eventHandler[0]
        assertThat(event1).isInstanceOf(TokenCreatedEvent::class.java)
        val tokenCreatedEvent = event1 as TokenCreatedEvent
        assertThat(tokenCreatedEvent.token).isNotNull()
        assertThat(tokenCreatedEvent.token).isEqualTo(token)

        val event2 = oktaRule.eventHandler[1]
        assertThat(event2).isInstanceOf(CredentialStoredEvent::class.java)
        val credentialStoredEvent = event2 as CredentialStoredEvent
        assertThat(credentialStoredEvent.token).isNotNull()
        assertThat(credentialStoredEvent.token).isEqualTo(token)
        assertThat(credentialStoredEvent.credential).isNotNull()
        assertThat(credentialStoredEvent.credential).isEqualTo(credential)
        assertThat(credentialStoredEvent.tags).isEqualTo(emptyMap<String, String>())
    }

    @Test fun testRefreshTokenPreservesRefreshToken(): Unit = runTest {
        coEvery { client.refreshToken(any()) } returns OAuth2ClientResult.Success(createToken())
        val token = createToken(refreshToken = "exampleRefreshToken")
        val credential = oktaRule.createCredential(
            token = token,
            oauthClient = client
        )
        credential.refreshToken()
        coVerify { client.refreshToken(token) }
        assertThat(credential.token.refreshToken).isEqualTo("exampleRefreshToken")
    }

    @Test fun testRefreshTokenPreservesDeviceSecret(): Unit = runTest {
        coEvery { client.refreshToken(any()) } returns OAuth2ClientResult.Success(createToken(refreshToken = "newRefreshToken"))
        val token = createToken(refreshToken = "exampleRefreshToken", deviceSecret = "saved")
        val credential = oktaRule.createCredential(
            token = token,
            oauthClient = client
        )
        credential.refreshToken()
        coVerify { client.refreshToken(token) }
        assertThat(credential.token.deviceSecret).isEqualTo("saved")
    }

    @Test fun testParallelRefreshToken(): Unit = runTest {
        val countDownLatch = CountDownLatch(2)
        coEvery { client.refreshToken(any()) } coAnswers {
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            // Delay and change threads before returning the result to make sure we enqueue both requests before returning.
            delay(10)
            withContext(Dispatchers.Default) {
                OAuth2ClientResult.Success(createToken(refreshToken = "newRefreshToken"))
            }
        }
        val tokenToRefresh = createToken(refreshToken = "exampleRefreshToken")
        val credential = oktaRule.createCredential(
            token = tokenToRefresh,
            oauthClient = client
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

        assertThat(result1).isInstanceOf(OAuth2ClientResult.Success::class.java)
        val token = (result1 as OAuth2ClientResult.Success<Token>).result
        assertThat(token.refreshToken).isEqualTo("newRefreshToken")
        assertThat(result1).isSameInstanceAs(result2)

        coVerify { client.refreshToken(tokenToRefresh) }
    }

    @Test fun testSerialRefreshToken(): Unit = runTest {
        coEvery { client.refreshToken(any()) } answers {
            OAuth2ClientResult.Success(createToken(refreshToken = "newRefreshToken"))
        }
        val credential = oktaRule.createCredential(
            token = createToken(refreshToken = "exampleRefreshToken"),
            oauthClient = client
        )

        val result1 = credential.refreshToken()
        assertThat(result1).isInstanceOf(OAuth2ClientResult.Success::class.java)
        val token = (result1 as OAuth2ClientResult.Success<Token>).result
        assertThat(token.refreshToken).isEqualTo("newRefreshToken")
        val result2 = credential.refreshToken()
        assertThat(result1).isNotSameInstanceAs(result2)

        coVerify(exactly = 2) { client.refreshToken(any()) }
    }

    @Test fun testReplaceToken(): Unit = runTest {
        val credential = oktaRule.createCredential(token = token, credentialDataSource = credentialDataSource)
        val newToken = createToken(refreshToken = "stored!")
        credential.replaceToken(token = newToken)
        assertThat(credential.token).isEqualTo(newToken)
    }

    @Test fun testReplaceTokenKeepsPreviousDeviceSecret(): Unit = runTest {
        val credentialDataSource = mockk<CredentialDataSource>(relaxed = true)
        val credential = oktaRule.createCredential(
            token = createToken(refreshToken = "exampleRefreshToken", deviceSecret = "originalDeviceSecret"),
            oauthClient = client, credentialDataSource = credentialDataSource
        )
        val newToken = createToken(refreshToken = "stored!")
        credential.replaceToken(token = newToken)
        val expectedToken = createToken(refreshToken = "stored!", deviceSecret = "originalDeviceSecret")
        assertThat(credential.token).isEqualTo(expectedToken)
        assertThat(credential.token.refreshToken).isEqualTo("stored!")
        assertThat(credential.token.deviceSecret).isEqualTo("originalDeviceSecret")
    }

    @Test fun testReplaceTokenKeepsStoresDeviceSecret(): Unit = runTest {
        val credentialDataSource = mockk<CredentialDataSource>(relaxed = true)
        val credential = oktaRule.createCredential(
            token = createToken(refreshToken = "exampleRefreshToken", deviceSecret = "originalDeviceSecret"),
            oauthClient = client,
            credentialDataSource = credentialDataSource
        )
        val newToken = createToken(refreshToken = "stored!", deviceSecret = "updatedDeviceSecret")
        credential.replaceToken(token = newToken)
        assertThat(credential.token).isEqualTo(newToken)
        assertThat(credential.token.refreshToken).isEqualTo("stored!")
        assertThat(credential.token.deviceSecret).isEqualTo("updatedDeviceSecret")
    }

    @Test fun testIdTokenWithNullIdTokenReturnsNullJwt(): Unit = runTest {
        val credential = oktaRule.createCredential(token = createToken(idToken = null))
        assertThat(credential.idToken()).isNull()
    }

    @Test fun testIdToken(): Unit = runTest {
        val credential = oktaRule.createCredential(token = createToken(idToken = "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6InVuaXRfdGVzdF9jbGllbnRfaWQiLCJpYXQiOjE2NDQzNDcwNjksImV4cCI6MTY0NDM1MDY2OSwianRpIjoiSUQuNTVjeEJ0ZFlsOGw2YXJLSVNQQndkMHlPVC05VUNUYVhhUVRYdDJsYVJMcyIsImFtciI6WyJwd2QiXSwiaWRwIjoiMDBvOGZvdTdzUmFHR3dkbjQ2OTYiLCJzaWQiOiJpZHhXeGtscF80a1N4dUNfblUxcFhELW5BIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiamF5bmV3c3Ryb21AZXhhbXBsZS5jb20iLCJhdXRoX3RpbWUiOjE2NDQzNDcwNjgsImF0X2hhc2giOiJnTWNHVGJoR1QxR19sZHNIb0pzUHpRIiwiZHNfaGFzaCI6IkRBZUxPRlJxaWZ5c2Jnc3JiT2dib2cifQ.tT8aKK4r8yFcW9KgVtZxvjXRJVzz-_rve14CVtpUlyvCTE1yj20wmPS0z3-JirI9xXgt5KeNPYqo3Wbv8c9XY_HY3hsPQdILYpPsUkf-sctmzSoKC_dTbs5xe8uKSgmpMrggfUAWrNPiJt9Ek2p7GgP64Wx79Pq5vSHk0yWlonFfXut5ahpSfqWilmYlvLr8gFbqoLnAJfl4ZbTY8pPw_aQgCdcQ-ImHRu-8bCSCtbFRzZB-SMJFLfRF2kmx0H-QF855wUODTuUSydkff-BKb-8wnbqWg0R9NvRdoXhEybv8TXXZY3cQqgolWLAyiPMrz07n0q_UEjAilUiCjn1f4Q"))
        val idToken = credential.idToken()
        assertThat(idToken).isNotNull()
        assertThat(idToken?.keyId).isEqualTo("FJA0HGNtsuuda_Pl45J42kvQqcsu_0C4Fg7pbJLXTHY")
    }

    @Test fun testMalformedIdToken(): Unit = runTest {
        val credential = oktaRule.createCredential(token = createToken(idToken = "InvalidJwt"))
        val idToken = credential.idToken()
        assertThat(idToken).isNull()
    }

    @Test fun accessTokenIfValidReturnsNullWithExpiredToken(): Unit = runTest {
        val idTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime - MOCK_TOKEN_DURATION)
        val idToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = idTokenClaims).rawValue
        val credential = oktaRule.createCredential(token = createToken(accessToken = "exampleAccessToken", idToken = idToken))
        assertThat(credential.getAccessTokenIfValid()).isNull()
    }

    @Test fun accessTokenIfValidReturnsAccessToken(): Unit = runTest {
        val accessToken = "exampleAccessToken"
        val idTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime)
        val idToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = idTokenClaims).rawValue
        val credential = oktaRule.createCredential(token = createToken(accessToken = accessToken, idToken = idToken))
        assertThat(credential.getAccessTokenIfValid()).isEqualTo(accessToken)
    }

    @Test
    fun `accessTokenIfValid returns null if idToken is null and expiry time is passed`() = runTest {
        val accessToken = "exampleAccessToken"
        val credential = oktaRule.createCredential(token = createToken(accessToken = accessToken))
        expireToken()
        assertThat(credential.getAccessTokenIfValid()).isEqualTo(null)
    }

    @Test
    fun `accessTokenIfValid returns access token even if idToken is null`() = runTest {
        val accessToken = "exampleAccessToken"
        val credential = oktaRule.createCredential(token = createToken(accessToken = accessToken))
        assertThat(credential.getAccessTokenIfValid()).isEqualTo(accessToken)
    }

    @Test
    fun `accessTokenIfValid returns null if idToken does not contain required payload and issuedAt is past expiry time`(): Unit = runTest {
        @Serializable
        class EmptyClaims

        val accessToken = "exampleAccessToken"
        val idToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = EmptyClaims()).rawValue
        val credential = oktaRule.createCredential(token = createToken(accessToken = accessToken, idToken = idToken))
        expireToken()
        assertThat(credential.getAccessTokenIfValid()).isNull()
    }

    @Test
    fun `accessTokenIfValid returns accessToken if idToken does not contain required payload but issuedAt is not expired`(): Unit = runTest {
        @Serializable
        class EmptyClaims

        val accessToken = "exampleAccessToken"
        val idToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = EmptyClaims()).rawValue
        val credential = oktaRule.createCredential(token = createToken(accessToken = accessToken, idToken = idToken))
        assertThat(credential.getAccessTokenIfValid()).isEqualTo(accessToken)
    }

    @Test fun getValidAccessTokenReturnsAccessToken(): Unit = runTest {
        val accessToken = "exampleAccessToken"
        val idTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime)
        val idToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = idTokenClaims).rawValue
        val credential = oktaRule.createCredential(token = createToken(accessToken = accessToken, idToken = idToken))
        assertThat(credential.getValidAccessToken()).isEqualTo(accessToken)
    }

    @Test fun getValidAccessTokenReturnsNullWhenAccessTokenExpiredAndNoRefreshToken(): Unit = runTest {
        val idTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime - MOCK_TOKEN_DURATION)
        val idToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = idTokenClaims).rawValue
        val credential = oktaRule.createCredential(token = createToken(accessToken = "exampleAccessToken", idToken = idToken, refreshToken = null))
        assertThat(credential.getValidAccessToken()).isNull()
    }

    @Test fun getValidAccessTokenRefreshesWhenAccessTokenExpired(): Unit = runTest {
        val expiredAccessToken = "expiredAccessToken"
        val validAccessToken = "exampleAccessToken"
        val expiredIdTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime - MOCK_TOKEN_DURATION)
        val expiredIdToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = expiredIdTokenClaims).rawValue
        val validIdTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime)
        val validIdToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = validIdTokenClaims).rawValue
        oktaRule.enqueue(path("/oauth2/default/v1/token")) { response ->
            val body = oktaRule.configuration.json.encodeToString(createToken(accessToken = validAccessToken, idToken = validIdToken).asSerializableToken())
            response.setBody(body)
        }
        val credential = oktaRule.createCredential(token = createToken(accessToken = expiredAccessToken, refreshToken = "exampleRefreshToken", idToken = expiredIdToken))
        coEvery { CredentialDataSource.getInstance() } returns credentialDataSource
        coEvery { credentialDataSource.replaceToken(match { it.id == credential.id }) } coAnswers {
            credential.replaceToken(it.invocation.args[0] as Token)
        }

        assertThat(credential.getValidAccessToken()).isEqualTo(validAccessToken)
    }

    @Test fun getValidAccessTokenReturnsNullWhenRefreshedAccessTokenExpired(): Unit = runTest {
        val expiredIdTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime - MOCK_TOKEN_DURATION)
        val expiredIdToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = expiredIdTokenClaims).rawValue
        coEvery { client.refreshToken(any()) } returns OAuth2ClientResult.Success(createToken(idToken = expiredIdToken))
        val credential = oktaRule.createCredential(oauthClient = client, token = createToken(accessToken = "exampleAccessToken", refreshToken = "exampleRefreshToken", idToken = expiredIdToken))
        assertThat(credential.getValidAccessToken()).isNull()
        coVerify { client.refreshToken(any()) }
    }

    @Test fun testInterceptor(): Unit = runTest {
        val accessToken = "exampleAccessToken"

        oktaRule.enqueue(
            method("GET"),
            header("authorization", "Bearer $accessToken"),
            path("/customers"),
        ) { response ->
            response.setBody("[]")
        }

        val idTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime)
        val idToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = idTokenClaims).rawValue
        val credential = oktaRule.createCredential(token = createToken(accessToken = accessToken, idToken = idToken))
        val interceptor = credential.accessTokenInterceptor()
        val okHttpClient = oktaRule.okHttpClient.newBuilder().addInterceptor(interceptor).build()
        val url = oktaRule.baseUrl.newBuilder().addPathSegment("customers").build()
        val call = okHttpClient.newCall(Request.Builder().url(url).build())
        val response = call.execute()
        assertThat(response.body!!.string()).isEqualTo("[]")
    }

    @Test fun testGetUserInfo(): Unit = runTest {
        val accessToken = "exampleAccessToken"
        val idTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime)
        val idToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = idTokenClaims).rawValue
        val credential = oktaRule.createCredential(
            token = createToken(accessToken = accessToken, idToken = idToken),
        )
        oktaRule.enqueue(path("/oauth2/default/v1/userinfo")) { response ->
            val body = """{"sub":"foobar"}"""
            response.setBody(body)
        }
        val result = credential.getUserInfo() as OAuth2ClientResult.Success<OidcUserInfo>

        @Serializable
        class ExampleUserInfo(
            @SerialName("sub") val sub: String
        )
        assertThat(result.result.deserializeClaims(ExampleUserInfo.serializer()).sub).isEqualTo("foobar")
    }

    @Test fun testGetUserInfoNeedsRefresh(): Unit = runTest {
        val expiredAccessToken = "expiredAccessToken"
        val validAccessToken = "exampleAccessToken"
        val expiredIdTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime - MOCK_TOKEN_DURATION)
        val expiredIdToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = expiredIdTokenClaims).rawValue
        val validIdTokenClaims = IdTokenClaims(issuedAt = oktaRule.clock.currentTime)
        val validIdToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = validIdTokenClaims).rawValue
        val credential = oktaRule.createCredential(
            token = createToken(accessToken = expiredAccessToken, refreshToken = "exampleRefreshToken", idToken = expiredIdToken),
        )
        coEvery { credentialDataSource.replaceToken(match { it.id == credential.id }) } coAnswers {
            credential.replaceToken(it.invocation.args[0] as Token)
        }
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

        val success = result as OAuth2ClientResult.Success<OidcUserInfo>
        assertThat(success.result.deserializeClaims(ExampleUserInfo.serializer()).sub).isEqualTo("foobar")
    }

    @Test fun testGetUserInfoNoToken(): Unit = runTest {
        val credential = oktaRule.createCredential(token)
        expireToken()
        val result = credential.getUserInfo() as OAuth2ClientResult.Error<OidcUserInfo>
        assertThat(result.exception).hasMessageThat().isEqualTo("No Access Token.")
        assertThat(result.exception).isInstanceOf(IllegalStateException::class.java)
    }

    @Test fun testIntrospectNullRefreshToken(): Unit = runTest {
        val credential = oktaRule.createCredential(token = createToken(refreshToken = null))
        val result = credential.introspectToken(TokenType.REFRESH_TOKEN)
        val errorResult = result as OAuth2ClientResult.Error<OidcIntrospectInfo>
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No refresh token.")
        assertThat(errorResult.exception).isInstanceOf(IllegalStateException::class.java)
    }

    @Test fun testIntrospectNullIdToken(): Unit = runTest {
        val credential = oktaRule.createCredential(token = createToken(idToken = null))
        val result = credential.introspectToken(TokenType.ID_TOKEN)
        val errorResult = result as OAuth2ClientResult.Error<OidcIntrospectInfo>
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No id token.")
        assertThat(errorResult.exception).isInstanceOf(IllegalStateException::class.java)
    }

    @Test fun testIntrospectNullDeviceSecret(): Unit = runTest {
        val credential = oktaRule.createCredential(token = createToken(deviceSecret = null))
        val result = credential.introspectToken(TokenType.DEVICE_SECRET)
        val errorResult = result as OAuth2ClientResult.Error<OidcIntrospectInfo>
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No device secret.")
        assertThat(errorResult.exception).isInstanceOf(IllegalStateException::class.java)
    }

    @Test fun testIntrospectRefreshToken(): Unit = runTest {
        coEvery { client.introspectToken(any(), any()) } returns OAuth2ClientResult.Success(OidcIntrospectInfo.Inactive)
        val token = createToken(refreshToken = "exampleRefreshToken")
        val credential = oktaRule.createCredential(token = token, oauthClient = client)
        val result = credential.introspectToken(TokenType.REFRESH_TOKEN)
        val successResult = result as OAuth2ClientResult.Success<OidcIntrospectInfo>
        assertThat(successResult.result.active).isFalse()
        coVerify { client.introspectToken(TokenType.REFRESH_TOKEN, token) }
    }

    @Test fun testIntrospectAccessToken(): Unit = runTest {
        coEvery { client.introspectToken(any(), any()) } returns OAuth2ClientResult.Success(OidcIntrospectInfo.Inactive)
        val token = createToken(accessToken = "exampleAccessToken")
        val credential = oktaRule.createCredential(token = token, oauthClient = client)
        val result = credential.introspectToken(TokenType.ACCESS_TOKEN)
        val successResult = result as OAuth2ClientResult.Success<OidcIntrospectInfo>
        assertThat(successResult.result.active).isFalse()
        coVerify { client.introspectToken(TokenType.ACCESS_TOKEN, token) }
    }

    @Test fun testIntrospectIdToken(): Unit = runTest {
        coEvery { client.introspectToken(any(), any()) } returns OAuth2ClientResult.Success(OidcIntrospectInfo.Inactive)
        val token = createToken(idToken = "exampleIdToken")
        val credential = oktaRule.createCredential(token = token, oauthClient = client)
        val result = credential.introspectToken(TokenType.ID_TOKEN)
        val successResult = result as OAuth2ClientResult.Success<OidcIntrospectInfo>
        assertThat(successResult.result.active).isFalse()
        coVerify { client.introspectToken(TokenType.ID_TOKEN, token) }
    }

    @Test fun testIntrospectDeviceSecret(): Unit = runTest {
        coEvery { client.introspectToken(any(), any()) } returns OAuth2ClientResult.Success(OidcIntrospectInfo.Inactive)
        val token = createToken(deviceSecret = "exampleDeviceSecret")
        val credential = oktaRule.createCredential(token = token, oauthClient = client)
        val result = credential.introspectToken(TokenType.DEVICE_SECRET)
        val successResult = result as OAuth2ClientResult.Success<OidcIntrospectInfo>
        assertThat(successResult.result.active).isFalse()
        coVerify { client.introspectToken(TokenType.DEVICE_SECRET, token) }
    }

    @Test fun testIntrospectAccessTokenNetworkError(): Unit = runTest {
        coEvery { client.introspectToken(any(), any()) } returns OAuth2ClientResult.Error(IllegalStateException("Exception from mock!"))
        val credential = oktaRule.createCredential(token = createToken(accessToken = "exampleAccessToken"), oauthClient = client)
        val result = credential.introspectToken(TokenType.ACCESS_TOKEN)
        val errorResult = result as OAuth2ClientResult.Error<OidcIntrospectInfo>
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("Exception from mock!")
        assertThat(errorResult.exception).isInstanceOf(IllegalStateException::class.java)
    }

    @Test fun testReplaceTokenExceptionCausesErrorResponse(): Unit = runTest {
        val credentialDataSource = mockk<CredentialDataSource>()
        coEvery {
            credentialDataSource.replaceToken(any())
        } throws IllegalStateException("Expected From Test!")

        val credential = oktaRule.createCredential(
            token = createToken(refreshToken = "exampleRefreshToken"),
            credentialDataSource = credentialDataSource,
        )
        oktaRule.enqueue(path("/oauth2/default/v1/token")) { response ->
            val body = oktaRule.configuration.json.encodeToString(createToken(refreshToken = "newRefreshToken").asSerializableToken())
            response.setBody(body)
        }
        val result = credential.refreshToken()
        assertThat(result).isInstanceOf(OAuth2ClientResult.Error::class.java)
        val exception = (result as OAuth2ClientResult.Error<Token>).exception
        assertThat(exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(exception).hasMessageThat().isEqualTo("Expected From Test!")
    }

    @Test fun equalsReturnsTrueWhenExactInstance() {
        val credential = oktaRule.createCredential(token = createToken())
        assertThat(credential).isEqualTo(credential)
    }

    @Test fun equalsReturnsTrueWhenSame() {
        val credential1 = oktaRule.createCredential(token = createToken())
        val credential2 = oktaRule.createCredential(token = createToken())
        assertThat(credential1).isEqualTo(credential2)
    }

    @Test fun equalsReturnsFalseWhenNotSame() {
        val credential1 = oktaRule.createCredential(token = createToken())
        val credential2 = oktaRule.createCredential(token = createToken(accessToken = "Different!"))
        assertThat(credential1).isNotEqualTo(credential2)
    }

    @Test fun equalsReturnsFalseWhenDifferentType() {
        val credential = oktaRule.createCredential(token = createToken())
        assertThat(credential).isNotEqualTo("Nope!")
    }

    private fun expireToken() {
        oktaRule.clock.currentTime += MOCK_TOKEN_DURATION
    }

    private val defaultCredentialDataSource: CredentialDataSource
        get() = credentialDataSource

    private fun OktaRule.createCredential(
        token: Token,
        tags: Map<String, String> = emptyMap(),
        oauthClient: OAuth2Client = createOAuth2Client(),
        credentialDataSource: CredentialDataSource = defaultCredentialDataSource
    ): Credential {
        coEvery { CredentialDataSource.getInstance() } returns credentialDataSource
        return Credential(token, oauthClient, tags)
    }
}
