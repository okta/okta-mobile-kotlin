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

import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.api.http.ApiResponse
import com.okta.authfoundation.client.Cache
import com.okta.authfoundation.client.OAuth2ClientConfiguration
import com.okta.authfoundation.client.OidcClock
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.credential.kmp.TokenData
import com.okta.authfoundation.credential.kmp.TokenStorage
import com.okta.authfoundation.events.Event
import com.okta.authfoundation.events.EventHandler
import kotlinx.serialization.json.Json

internal object TestConfiguration {
    fun create(
        clientId: String = "test-client-id",
        issuerUrl: String = "https://test.okta.com",
        clockTimeSeconds: Long = 1_000_000L,
        clock: OidcClock = FixedClock(clockTimeSeconds),
    ): OAuth2ClientConfiguration =
        OAuth2ClientConfiguration(
            clientId = clientId,
            defaultScope = "openid profile",
            issuerUrl = issuerUrl,
            apiExecutor = NoOpApiExecutor,
            clock = clock,
            json = Json { ignoreUnknownKeys = true },
            cache = NoOpCache,
            authorizationServerId = null,
            clientSecret = null,
            acrValues = null
        )

    private object NoOpApiExecutor : ApiExecutor {
        override suspend fun execute(request: ApiRequest): Result<ApiResponse> = Result.failure(UnsupportedOperationException("Test executor"))
    }

    private object NoOpCache : Cache {
        override fun get(key: String): String? = null

        override fun set(
            key: String,
            value: String,
        ) {}
    }

    class FixedClock(
        var time: Long,
    ) : OidcClock {
        override fun currentTimeEpochSecond(): Long = time
    }
}

internal fun createTestToken(
    id: String = "test-id",
    accessToken: String = "access-$id",
    refreshToken: String? = "refresh-$id",
    idToken: String? = null,
    deviceSecret: String? = null,
    expiresIn: Int = 3600,
    configuration: OAuth2ClientConfiguration = TestConfiguration.create(),
): TokenData =
    TokenData(
        id = id,
        tokenType = "Bearer",
        expiresIn = expiresIn,
        accessToken = accessToken,
        scope = "openid profile",
        refreshToken = refreshToken,
        idToken = idToken,
        deviceSecret = deviceSecret,
        issuedTokenType = null,
        configuration = configuration
    )

internal fun createTestMetadata(
    id: String = "test-id",
    tags: Map<String, String> = emptyMap(),
): TokenMetadata =
    TokenMetadata(
        id = id,
        tags = tags,
        payloadData = null
    )

internal class RecordingEventHandler : EventHandler {
    val events = mutableListOf<Event>()

    override fun onEvent(event: Event) {
        events.add(event)
    }
}

internal class FakeCommonTokenStorage : TokenStorage {
    private val tokens = mutableMapOf<String, TokenInfo>()
    private val metadata = mutableMapOf<String, TokenMetadata>()

    override suspend fun allIds(): List<String> = tokens.keys.toList()

    override suspend fun metadata(id: String): TokenMetadata? = metadata[id]

    override suspend fun setMetadata(metadata: TokenMetadata) {
        this.metadata[metadata.id] = metadata
    }

    override suspend fun add(
        token: TokenInfo,
        metadata: TokenMetadata,
    ) {
        tokens[token.id] = token
        this.metadata[token.id] = metadata
    }

    override suspend fun remove(id: String) {
        tokens.remove(id)
        metadata.remove(id)
    }

    override suspend fun replace(token: TokenInfo) {
        if (!tokens.containsKey(token.id)) {
            throw NoSuchElementException("No token with id ${token.id}")
        }
        tokens[token.id] = token
    }

    override suspend fun getToken(id: String): TokenInfo = tokens[id] ?: throw NoSuchElementException("No token with id $id")
}
