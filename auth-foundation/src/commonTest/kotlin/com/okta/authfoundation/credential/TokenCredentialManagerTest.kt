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

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.internal.OAuth2Endpoints
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.authfoundation.credential.kmp.Credential
import com.okta.authfoundation.credential.kmp.CredentialDataSource
import com.okta.authfoundation.credential.kmp.TokenCredentialManager
import com.okta.authfoundation.util.CoalescingOrchestrator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalAuthFoundationApi::class)
class TokenCredentialManagerTest {
    private val clock = TestConfiguration.FixedClock(1_000_000L)
    private val config = TestConfiguration.create(clock = clock)
    private val storage = FakeTokenStorage()
    private val dataSource = CredentialDataSource(storage)
    private val defaultIdStore = InMemoryDefaultCredentialIdStore()

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

    private val client =
        OAuth2Client(
            configuration = config,
            endpointsOrchestrator =
                CoalescingOrchestrator(
                    factory = { OAuth2ClientResult.Success(testEndpoints) },
                    keepDataInMemory = { true }
                )
        )

    private val manager =
        TokenCredentialManager(
            client = client,
            storage = storage,
            defaultIdStore = defaultIdStore
        )

    private suspend fun createAndStoreCredential(
        id: String = "cred-1",
        accessToken: String = "valid-at",
        refreshToken: String? = "valid-rt",
        expiresIn: Int = 3600,
        tags: Map<String, String> = emptyMap(),
    ) {
        val token =
            createTestToken(
                id = id,
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresIn = expiresIn,
                configuration = config
            )
        dataSource.createToken(token, tags)
    }

    @Test
    fun find_Predicate_MatchesSome() =
        runTest {
            createAndStoreCredential(id = "find-1", tags = mapOf("env" to "prod"))
            createAndStoreCredential(id = "find-2", tags = mapOf("env" to "dev"))
            createAndStoreCredential(id = "find-3", tags = mapOf("env" to "prod"))

            val results = manager.find { metadata: TokenMetadata -> metadata.tags["env"] == "prod" }.getOrThrow()

            assertEquals(2, results.size)
            assertTrue(results.any { it.id == "find-1" })
            assertTrue(results.any { it.id == "find-3" })
        }

    @Test
    fun find_Predicate_MatchesNone() =
        runTest {
            createAndStoreCredential(id = "find-empty-1", tags = mapOf("env" to "dev"))
            createAndStoreCredential(id = "find-empty-2", tags = mapOf("env" to "staging"))

            val results = manager.find { metadata: TokenMetadata -> metadata.tags["env"] == "prod" }.getOrThrow()

            assertTrue(results.isEmpty())
        }

    @Test
    fun find_Predicate_MatchesAll() =
        runTest {
            createAndStoreCredential(id = "find-all-1")
            createAndStoreCredential(id = "find-all-2")
            createAndStoreCredential(id = "find-all-3")

            val results = manager.find { _: TokenMetadata -> true }.getOrThrow()

            assertEquals(3, results.size)
        }

    @Test
    fun find_Predicate_OnTags() =
        runTest {
            createAndStoreCredential(id = "tag-test-1", tags = mapOf("role" to "admin", "tenant" to "acme"))
            createAndStoreCredential(id = "tag-test-2", tags = mapOf("role" to "user", "tenant" to "acme"))
            createAndStoreCredential(id = "tag-test-3", tags = mapOf("role" to "admin", "tenant" to "widgets"))

            val results = manager.find { metadata: TokenMetadata -> metadata.tags["role"] == "admin" && metadata.tags["tenant"] == "acme" }.getOrThrow()

            assertEquals(1, results.size)
            assertEquals("tag-test-1", results[0].id)
        }

    @Test
    fun find_Predicate_PreservesCredentialProperties() =
        runTest {
            createAndStoreCredential(id = "prop-test", accessToken = "specific-at", tags = mapOf("key" to "value"))

            val results = manager.find { credential: Credential -> credential.id == "prop-test" }.getOrThrow()

            assertEquals(1, results.size)
            val credential = results[0]
            assertEquals("prop-test", credential.id)
            assertEquals("specific-at", credential.token.accessToken)
            assertEquals("value", credential.tags["key"])
        }

    @Test
    fun find_Predicate_OnCredentialId() =
        runTest {
            createAndStoreCredential(id = "find-by-id-1")
            createAndStoreCredential(id = "find-by-id-2")

            val results = manager.find { credential: Credential -> credential.id.startsWith("find-by-id") }.getOrThrow()

            assertEquals(2, results.size)
        }

    @Test
    fun find_Predicate_Empty() =
        runTest {
            val results = manager.find { _: Credential -> true }.getOrThrow()

            assertTrue(results.isEmpty())
        }
}
