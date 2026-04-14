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
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.internal.OAuth2Endpoints
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.authfoundation.credential.kmp.Credential
import com.okta.authfoundation.credential.kmp.CredentialDataSource
import com.okta.authfoundation.credential.kmp.CredentialManager
import com.okta.authfoundation.credential.kmp.InMemoryDefaultCredentialIdStore
import com.okta.authfoundation.credential.kmp.TokenStorage
import com.okta.authfoundation.util.CoalescingOrchestrator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(InternalAuthFoundationApi::class)
class CustomTokenStorageTest {
    private val config = TestConfiguration.create()

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

    @Test
    fun customStorage_AllOperationsWorkThroughIt() =
        runTest {
            val customStorage = FakeTokenStorage()
            val manager =
                CredentialManager(
                    client = client,
                    storage = customStorage,
                    defaultIdStore = InMemoryDefaultCredentialIdStore()
                )

            val cred =
                manager
                    .store(
                        token = createTestToken(id = "custom-1", configuration = config),
                        tags = mapOf("custom" to "true")
                    ).getOrThrow()

            assertEquals("custom-1", cred.id)
            assertEquals(listOf("custom-1"), manager.dataSource.allIds())

            val metadata = manager.dataSource.metadata("custom-1")
            assertNotNull(metadata)
            assertEquals("true", metadata.tags["custom"])

            val updated = cred.setTagsAsync(mapOf("updated" to "yes")).getOrThrow()
            assertEquals("yes", updated.tags["updated"])
            // Original credential tags unchanged (immutable snapshot)
            assertEquals("true", cred.tags["custom"])
            assertNull(cred.tags["updated"])

            cred.deleteAsync().getOrThrow()
            assertTrue(manager.dataSource.allIds().isEmpty())
        }

    @Test
    fun customStorage_FailingWrite_PropagatesError() =
        runTest {
            val failingStorage =
                object : TokenStorage {
                    override suspend fun allIds(): Result<List<String>> = Result.success(emptyList())

                    override suspend fun metadata(id: String): Result<TokenMetadata?> = Result.success(null)

                    override suspend fun setMetadata(metadata: TokenMetadata): Result<Unit> = Result.success(Unit)

                    override suspend fun add(
                        token: TokenInfo,
                        metadata: TokenMetadata,
                    ): Result<Unit> = Result.failure(RuntimeException("Storage write failed"))

                    override suspend fun remove(id: String): Result<Unit> = Result.success(Unit)

                    override suspend fun replace(token: TokenInfo): Result<Unit> = Result.success(Unit)

                    override suspend fun getToken(id: String): Result<TokenInfo> = Result.failure(NoSuchElementException())
                }

            val dataSource = CredentialDataSource(failingStorage)

            val result = runCatching { dataSource.createToken(createTestToken(id = "fail-1", configuration = config)) }
            assertTrue(result.isFailure)
        }

    @Test
    fun customStorage_FailingRead_PropagatesError() =
        runTest {
            val failingStorage =
                object : TokenStorage {
                    override suspend fun allIds(): Result<List<String>> = Result.failure(RuntimeException("Storage read failed"))

                    override suspend fun metadata(id: String): Result<TokenMetadata?> = Result.success(null)

                    override suspend fun setMetadata(metadata: TokenMetadata): Result<Unit> = Result.success(Unit)

                    override suspend fun add(
                        token: TokenInfo,
                        metadata: TokenMetadata,
                    ): Result<Unit> = Result.success(Unit)

                    override suspend fun remove(id: String): Result<Unit> = Result.success(Unit)

                    override suspend fun replace(token: TokenInfo): Result<Unit> = Result.success(Unit)

                    override suspend fun getToken(id: String): Result<TokenInfo> = Result.failure(NoSuchElementException())
                }

            val dataSource = CredentialDataSource(failingStorage)

            val result = runCatching { dataSource.allIds() }
            assertTrue(result.isFailure)
        }

    @Test
    fun customStorage_MultipleCredentials_IndependentLifecycles() =
        runTest {
            val customStorage = FakeTokenStorage()
            val manager =
                CredentialManager(
                    client = client,
                    storage = customStorage,
                    defaultIdStore = InMemoryDefaultCredentialIdStore()
                )

            val c1 =
                manager
                    .store(
                        token = createTestToken(id = "cs-1", configuration = config),
                        tags = mapOf("type" to "primary")
                    ).getOrThrow()
            manager
                .store(
                    token = createTestToken(id = "cs-2", configuration = config),
                    tags = mapOf("type" to "secondary")
                ).getOrThrow()

            assertEquals(2, manager.dataSource.allIds().size)

            c1.deleteAsync().getOrThrow()

            assertEquals(1, manager.dataSource.allIds().size)
            assertNull(manager.dataSource.getToken("cs-1"))
            assertNotNull(manager.dataSource.getToken("cs-2"))
        }

    @Test
    fun customStorage_DefaultCredential_WorksThroughCustomStorage() =
        runTest {
            val customStorage = FakeTokenStorage()
            val manager =
                CredentialManager(
                    client = client,
                    storage = customStorage,
                    defaultIdStore = InMemoryDefaultCredentialIdStore()
                )

            val credential =
                manager
                    .store(
                        token = createTestToken(id = "cs-def", configuration = config)
                    ).getOrThrow()

            manager.setDefault(credential).getOrThrow()

            val defaultCred = manager.getDefault().getOrThrow()
            assertNotNull(defaultCred)
            assertEquals("cs-def", defaultCred.id)
        }
}
