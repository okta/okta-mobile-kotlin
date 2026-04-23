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
package com.okta.authfoundation.credential.kmp

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.api.http.ApiResponse
import com.okta.authfoundation.client.OAuth2ClientConfiguration
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.internal.OAuth2Endpoints
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.authfoundation.credential.FakeTokenStorage
import com.okta.authfoundation.credential.InMemoryDefaultCredentialIdStore
import com.okta.authfoundation.credential.RevokeAllException
import com.okta.authfoundation.credential.RevokeTokenType
import com.okta.authfoundation.credential.TestConfiguration
import com.okta.authfoundation.credential.TokenType
import com.okta.authfoundation.credential.createTestToken
import com.okta.authfoundation.events.Event
import com.okta.authfoundation.util.CoalescingOrchestrator
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(InternalAuthFoundationApi::class)
class CredentialTokenLifecycleTest {
    private val noOpEvents =
        MutableSharedFlow<Event>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    private val testEndpoints =
        OAuth2Endpoints(
            issuer = "https://test.okta.com",
            authorizationEndpoint = "https://test.okta.com/v1/authorize",
            tokenEndpoint = "https://test.okta.com/v1/token",
            userInfoEndpoint = "https://test.okta.com/v1/userinfo",
            jwksUri = "https://test.okta.com/v1/keys",
            introspectionEndpoint = "https://test.okta.com/v1/introspect",
            revocationEndpoint = "https://test.okta.com/v1/revoke",
            endSessionEndpoint = "https://test.okta.com/v1/logout",
            deviceAuthorizationEndpoint = null
        )

    private fun mockResponse(
        body: String = "",
        contentType: String = "application/json",
    ): ApiResponse =
        object : ApiResponse {
            override val statusCode: Int = 200
            override val body: ByteArray = body.toByteArray()
            override val headers: Map<String, List<String>> =
                if (contentType.isNotEmpty()) mapOf("Content-Type" to listOf(contentType)) else emptyMap()
            override val contentLength: Long = body.length.toLong()
            override val contentType: String = contentType
        }

    private fun executor(handler: suspend (ApiRequest) -> Result<ApiResponse>): ApiExecutor =
        object : ApiExecutor {
            override suspend fun execute(request: ApiRequest): Result<ApiResponse> = handler(request)
        }

    private fun failingExecutor(exception: Throwable = RuntimeException("network error")): ApiExecutor =
        object : ApiExecutor {
            override suspend fun execute(request: ApiRequest): Result<ApiResponse> = Result.failure(exception)
        }

    private fun successExecutor(body: String = ""): ApiExecutor =
        object : ApiExecutor {
            override suspend fun execute(request: ApiRequest): Result<ApiResponse> = Result.success(mockResponse(body = body))
        }

    private fun createClient(apiExecutor: ApiExecutor): OAuth2Client {
        val config =
            OAuth2ClientConfiguration(
                clientId = "test-client-id",
                defaultScope = "openid",
                issuerUrl = "https://test.okta.com",
                apiExecutor = apiExecutor,
                clock = TestConfiguration.FixedClock(1_000_000L),
                json = Json { ignoreUnknownKeys = true },
                cache = TestConfiguration.create().cache,
                authorizationServerId = null,
                clientSecret = null,
                acrValues = null
            )
        return OAuth2Client(
            configuration = config,
            endpointsOrchestrator =
                CoalescingOrchestrator(
                    factory = { OAuth2ClientResult.Success(testEndpoints) },
                    keepDataInMemory = { true }
                )
        )
    }

    private suspend fun createCredential(
        client: OAuth2Client,
        id: String = "test-cred",
        refreshToken: String? = "valid-refresh",
        deviceSecret: String? = null,
    ): CredentialImpl {
        val storage = FakeTokenStorage()
        val dataSource = CredentialDataSource(storage)
        val token =
            TokenData(
                id = id,
                tokenType = "Bearer",
                expiresIn = 3600,
                accessToken = "original-at",
                scope = "openid",
                refreshToken = refreshToken,
                idToken = null,
                deviceSecret = deviceSecret,
                issuedTokenType = null,
                configuration = client.configuration
            )
        dataSource.createToken(token)
        return CredentialImpl(
            token = token,
            client = client,
            tags = emptyMap(),
            dataSource = dataSource,
            events = noOpEvents,
            defaultIdStore = InMemoryDefaultCredentialIdStore()
        )
    }

    @Test
    fun refreshToken_NoRefreshToken_ReturnsError() =
        runTest {
            val credential = createCredential(createClient(failingExecutor()), refreshToken = null)
            assertTrue(credential.refreshToken().isFailure)
        }

    @Test
    fun refreshToken_NetworkFailure_ReturnsError() =
        runTest {
            val credential = createCredential(createClient(failingExecutor()))
            assertTrue(credential.refreshToken().isFailure)
        }

    @Test
    fun refreshToken_SuccessfulResponse_ReturnsSuccess() =
        runTest {
            val body = """{"token_type":"Bearer","expires_in":7200,"access_token":"new-at","scope":"openid","refresh_token":"new-rt"}"""
            val credential = createCredential(createClient(successExecutor(body)))
            val result = credential.refreshToken()
            assertTrue(result.isSuccess)
            assertNotNull(result.getOrNull())
        }

    @Test
    fun revokeToken_AccessToken_CallsRevocationEndpoint() =
        runTest {
            var capturedUrl: String? = null
            val exec =
                executor { request ->
                    capturedUrl = request.url()
                    Result.success(mockResponse())
                }
            val credential = createCredential(createClient(exec))
            val result = credential.revokeToken(RevokeTokenType.ACCESS_TOKEN)
            assertTrue(result.isSuccess)
            assertNotNull(capturedUrl)
            assertTrue(capturedUrl!!.contains("revoke"))
        }

    @Test
    fun revokeToken_NullRefreshToken_ReturnsError() =
        runTest {
            val credential = createCredential(createClient(failingExecutor()), refreshToken = null)
            assertTrue(credential.revokeToken(RevokeTokenType.REFRESH_TOKEN).isFailure)
        }

    @Test
    fun revokeAllTokens_AllSucceed_ReturnsSuccess() =
        runTest {
            val credential = createCredential(createClient(successExecutor()), refreshToken = "rt", deviceSecret = "ds")
            assertTrue(credential.revokeAllTokens().isSuccess)
        }

    @Test
    fun revokeAllTokens_PartialFailure_ReturnsRevokeAllException() =
        runTest {
            var callCount = 0
            val exec =
                executor {
                    callCount++
                    if (callCount == 1) Result.failure(RuntimeException("fail")) else Result.success(mockResponse())
                }
            val credential = createCredential(createClient(exec), refreshToken = "rt")
            val result = credential.revokeAllTokens()
            assertTrue(result.isFailure)
            assertIs<RevokeAllException>(result.exceptionOrNull())
        }

    @Test
    fun refreshIfExpired_ValidToken_ReturnsWithoutRefresh() =
        runTest {
            var apiCalled = false
            val exec =
                executor {
                    apiCalled = true
                    Result.failure(IllegalStateException("should not refresh"))
                }
            val clock = TestConfiguration.FixedClock(1_000_000L)
            val config =
                OAuth2ClientConfiguration(
                    clientId = "test",
                    defaultScope = "openid",
                    issuerUrl = "https://test.okta.com",
                    apiExecutor = exec,
                    clock = clock,
                    json = Json { ignoreUnknownKeys = true },
                    cache = TestConfiguration.create().cache,
                    authorizationServerId = null,
                    clientSecret = null,
                    acrValues = null
                )
            val client =
                OAuth2Client(
                    configuration = config,
                    endpointsOrchestrator = CoalescingOrchestrator(factory = { OAuth2ClientResult.Success(testEndpoints) }, keepDataInMemory = { true })
                )
            val storage = FakeTokenStorage()
            val ds = CredentialDataSource(storage)
            val token =
                TokenData(
                    id = "fresh",
                    tokenType = "Bearer",
                    expiresIn = 3600,
                    accessToken = "fresh-at",
                    scope = "openid",
                    refreshToken = null,
                    idToken = null,
                    deviceSecret = null,
                    issuedTokenType = null,
                    configuration = config
                )
            ds.createToken(token)
            clock.time = 1_000_000L - 100
            val credential =
                CredentialImpl(token = token, client = client, dataSource = ds, events = noOpEvents, defaultIdStore = InMemoryDefaultCredentialIdStore())
            assertEquals(
                "fresh-at",
                credential
                    .refreshIfExpired()
                    .getOrThrow()
                    .token.accessToken
            )
            assertTrue(!apiCalled)
        }

    @Test
    fun refreshIfExpired_ExpiredTokenNoRefresh_ReturnsNull() =
        runTest {
            val clock = TestConfiguration.FixedClock(1_000_000L)
            val config =
                OAuth2ClientConfiguration(
                    clientId = "test",
                    defaultScope = "openid",
                    issuerUrl = "https://test.okta.com",
                    apiExecutor = failingExecutor(),
                    clock = clock,
                    json = Json { ignoreUnknownKeys = true },
                    cache = TestConfiguration.create().cache,
                    authorizationServerId = null,
                    clientSecret = null,
                    acrValues = null
                )
            val client =
                OAuth2Client(
                    configuration = config,
                    endpointsOrchestrator = CoalescingOrchestrator(factory = { OAuth2ClientResult.Success(testEndpoints) }, keepDataInMemory = { true })
                )
            val ds = CredentialDataSource(FakeTokenStorage())
            val token = createTestToken(expiresIn = 100, refreshToken = "rt", configuration = config)
            ds.createToken(token)
            clock.time = 1_000_000L + 200
            val credential =
                CredentialImpl(token = token, client = client, dataSource = ds, events = noOpEvents, defaultIdStore = InMemoryDefaultCredentialIdStore())
            assertTrue(credential.refreshIfExpired().isFailure)
        }

    @Test
    fun refreshToken_SuccessfulResponse_ReturnsNewSnapshot() =
        runTest {
            val body = """{"token_type":"Bearer","expires_in":7200,"access_token":"new-at","scope":"openid","refresh_token":"new-rt"}"""
            val credential = createCredential(createClient(successExecutor(body)))
            val originalToken = credential.token

            val result = credential.refreshToken()
            assertTrue(result.isSuccess)

            val snapshot = result.getOrThrow()
            // Original unchanged
            assertEquals("original-at", credential.token.accessToken)
            assertEquals(originalToken, credential.token)
            // New snapshot has refreshed token
            assertNotEquals(credential.token.accessToken, snapshot.token.accessToken)
        }

    @Test
    fun refreshIfExpired_ValidToken_ReturnsSameInstance() =
        runTest {
            var apiCalled = false
            val exec =
                executor {
                    apiCalled = true
                    Result.failure(IllegalStateException("should not refresh"))
                }
            val clock = TestConfiguration.FixedClock(1_000_000L)
            val config =
                OAuth2ClientConfiguration(
                    clientId = "test",
                    defaultScope = "openid",
                    issuerUrl = "https://test.okta.com",
                    apiExecutor = exec,
                    clock = clock,
                    json = Json { ignoreUnknownKeys = true },
                    cache = TestConfiguration.create().cache,
                    authorizationServerId = null,
                    clientSecret = null,
                    acrValues = null
                )
            val client =
                OAuth2Client(
                    configuration = config,
                    endpointsOrchestrator = CoalescingOrchestrator(factory = { OAuth2ClientResult.Success(testEndpoints) }, keepDataInMemory = { true })
                )
            val storage = FakeTokenStorage()
            val ds = CredentialDataSource(storage)
            val token =
                TokenData(
                    id = "same-inst",
                    tokenType = "Bearer",
                    expiresIn = 3600,
                    accessToken = "fresh-at",
                    scope = "openid",
                    refreshToken = null,
                    idToken = null,
                    deviceSecret = null,
                    issuedTokenType = null,
                    configuration = config
                )
            ds.createToken(token)
            clock.time = 1_000_000L - 100
            val credential =
                CredentialImpl(token = token, client = client, dataSource = ds, events = noOpEvents, defaultIdStore = InMemoryDefaultCredentialIdStore())
            val result = credential.refreshIfExpired().getOrThrow()
            // When token is valid, returns this (same instance)
            assertTrue(result === credential)
            assertTrue(!apiCalled)
        }

    @Test
    fun introspectToken_AccessToken_ReturnsSuccess() =
        runTest {
            val exec = successExecutor(body = """{"active":true}""")
            val credential = createCredential(createClient(exec))
            val result = credential.introspectToken(TokenType.ACCESS_TOKEN)
            assertTrue(result.isSuccess)
        }

    @Test
    fun introspectToken_NullIdToken_ReturnsError() =
        runTest {
            val credential = createCredential(createClient(failingExecutor()))
            val result = credential.introspectToken(TokenType.ID_TOKEN)
            assertTrue(result.isFailure)
        }
}
