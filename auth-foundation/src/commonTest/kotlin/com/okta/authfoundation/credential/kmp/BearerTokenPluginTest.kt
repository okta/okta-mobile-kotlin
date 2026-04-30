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

import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.dto.IntrospectInfo
import com.okta.authfoundation.client.dto.OidcUserInfo
import com.okta.authfoundation.credential.RevokeTokenType
import com.okta.authfoundation.credential.TestConfiguration
import com.okta.authfoundation.credential.TokenType
import com.okta.authfoundation.credential.createTestToken
import com.okta.authfoundation.credential.events.NoAccessTokenAvailableEvent
import com.okta.authfoundation.events.Event
import com.okta.authfoundation.jwt.Jwt
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.headersOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private class FakeCredential(
    private val tokenData: TokenData,
) : Credential {
    override val id: String = tokenData.id
    override val token: TokenInfo = tokenData
    override val tags: Map<String, String> = emptyMap()

    override suspend fun deleteAsync(): Result<Unit> = error("not implemented")

    override fun getTokenFlow(): Flow<TokenInfo> = emptyFlow()

    override suspend fun getUserInfo(): Result<OidcUserInfo> = error("not implemented")

    override suspend fun revokeToken(tokenType: RevokeTokenType): Result<Unit> = error("not implemented")

    override suspend fun revokeAllTokens(): Result<Unit> = error("not implemented")

    override suspend fun refreshIfExpired(): Result<Credential> = error("not implemented")

    override fun accessTokenIfNotExpired(): String? = tokenData.accessTokenIfNotExpired()

    override suspend fun setTagsAsync(tags: Map<String, String>): Result<Credential> = error("not implemented")

    override suspend fun introspectToken(tokenType: TokenType): Result<IntrospectInfo> = error("not implemented")

    override suspend fun refreshToken(): Result<Credential> = error("not implemented")

    override fun idToken(): Result<Jwt> = error("not implemented")

    override fun scope(): String = tokenData.scope ?: ""
}

class BearerTokenPluginTest {
    @Test
    fun bearerTokenPlugin_AttachesToken() =
        runTest {
            var authHeader: String? = null
            val mockEngine =
                MockEngine { request ->
                    authHeader = request.headers["Authorization"]
                    respond("", headers = headersOf("Content-Type" to listOf("application/json")))
                }

            val token = createTestToken(accessToken = "test-access-token", expiresIn = 3600)
            val client =
                HttpClient(mockEngine) {
                    install(BearerTokenPlugin) {
                        accessTokenProvider = { token.accessToken }
                    }
                }

            client.get("https://example.com/api")

            assertEquals("Bearer test-access-token", authHeader)
        }

    @Test
    fun bearerTokenPlugin_NullProvider_NoHeader() =
        runTest {
            var authHeader: String? = null
            val mockEngine =
                MockEngine { request ->
                    authHeader = request.headers["Authorization"]
                    respond("", headers = headersOf("Content-Type" to listOf("application/json")))
                }

            val client =
                HttpClient(mockEngine) {
                    install(BearerTokenPlugin) {
                        accessTokenProvider = { null }
                    }
                }

            client.get("https://example.com/api")

            assertNull(authHeader)
        }

    @Test
    fun bearerTokenPlugin_EmptyToken_NoHeader() =
        runTest {
            var authHeader: String? = null
            val mockEngine =
                MockEngine { request ->
                    authHeader = request.headers["Authorization"]
                    respond("", headers = headersOf("Content-Type" to listOf("application/json")))
                }

            val client =
                HttpClient(mockEngine) {
                    install(BearerTokenPlugin) {
                        accessTokenProvider = { "" }
                    }
                }

            client.get("https://example.com/api")

            assertNull(authHeader)
        }

    @Test
    fun bearerTokenPlugin_MultipleRequests_AttachesEachTime() =
        runTest {
            var requestCount = 0
            val mockEngine =
                MockEngine { request ->
                    requestCount++
                    val auth = request.headers["Authorization"]
                    assertEquals("Bearer test-access-token", auth)
                    respond("", headers = headersOf("Content-Type" to listOf("application/json")))
                }

            val token = createTestToken(accessToken = "test-access-token", expiresIn = 3600)
            val client =
                HttpClient(mockEngine) {
                    install(BearerTokenPlugin) {
                        accessTokenProvider = { token.accessToken }
                    }
                }

            client.get("https://example.com/api")
            client.get("https://example.com/api")
            client.get("https://example.com/api")

            assertEquals(3, requestCount)
        }

    @Test
    fun bearerTokenPlugin_ExpiredToken_NoHeader() =
        runTest {
            var authHeader: String? = null
            val mockEngine =
                MockEngine { request ->
                    authHeader = request.headers["Authorization"]
                    respond("", headers = headersOf("Content-Type" to listOf("application/json")))
                }

            val config = TestConfiguration.create(clockTimeSeconds = 5000L)
            val token = createTestToken(accessToken = "test-access-token", expiresIn = 3600, configuration = config)
            val client =
                HttpClient(mockEngine) {
                    install(BearerTokenPlugin) {
                        accessTokenProvider = { token.accessTokenIfNotExpired() }
                    }
                }

            client.get("https://example.com/api")

            assertNull(authHeader)
        }

    @Test
    fun bearerTokenPlugin_ValidToken_AttachesToken() =
        runTest {
            var authHeader: String? = null
            val mockEngine =
                MockEngine { request ->
                    authHeader = request.headers["Authorization"]
                    respond("", headers = headersOf("Content-Type" to listOf("application/json")))
                }

            // Clock at 2000, token issued at 1000, expires in 3600 → valid until 4600
            val config = TestConfiguration.create(clockTimeSeconds = 2000L)
            val token =
                TokenData(
                    id = "test-id",
                    tokenType = "Bearer",
                    expiresIn = 3600,
                    accessToken = "test-access-token",
                    scope = "openid",
                    refreshToken = null,
                    idToken = null,
                    deviceSecret = null,
                    issuedTokenType = null,
                    configuration = config,
                    issuedAt = 1000L
                )
            val client =
                HttpClient(mockEngine) {
                    install(BearerTokenPlugin) {
                        accessTokenProvider = { token.accessTokenIfNotExpired() }
                    }
                }

            client.get("https://example.com/api")

            assertEquals("Bearer test-access-token", authHeader)
        }

    @Test
    fun bearerTokenPlugin_NullToken_EmitsNoAccessTokenEvent() =
        runTest {
            val eventsFlow =
                MutableSharedFlow<Event>(
                    replay = 0,
                    extraBufferCapacity = 64,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                )
            val events = mutableListOf<Event>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { eventsFlow.collect { events.add(it) } }

            val mockEngine =
                MockEngine {
                    respond("", headers = headersOf("Content-Type" to listOf("application/json")))
                }

            val token = createTestToken(accessToken = "test-access-token", expiresIn = 3600)
            val fakeCredential = FakeCredential(token)

            val client =
                HttpClient(mockEngine) {
                    install(BearerTokenPlugin) {
                        accessTokenProvider = { null }
                        credential = fakeCredential
                        this.eventsFlow = eventsFlow
                    }
                }

            client.get("https://example.com/api")

            assertEquals(1, events.size)
            assertIs<NoAccessTokenAvailableEvent>(events[0])
            job.cancel()
        }

    @Test
    fun bearerTokenPlugin_NoCredential_NoEventOnNull() =
        runTest {
            val eventsFlow =
                MutableSharedFlow<Event>(
                    replay = 0,
                    extraBufferCapacity = 64,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                )
            val events = mutableListOf<Event>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { eventsFlow.collect { events.add(it) } }

            val mockEngine =
                MockEngine {
                    respond("", headers = headersOf("Content-Type" to listOf("application/json")))
                }

            val client =
                HttpClient(mockEngine) {
                    install(BearerTokenPlugin) {
                        accessTokenProvider = { null }
                        this.eventsFlow = eventsFlow
                        // No credential set — event should not be emitted
                    }
                }

            client.get("https://example.com/api")

            assertEquals(0, events.size)
            job.cancel()
        }
}
