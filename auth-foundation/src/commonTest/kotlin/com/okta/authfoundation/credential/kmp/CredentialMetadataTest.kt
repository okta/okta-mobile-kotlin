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
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.internal.OAuth2Endpoints
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.authfoundation.credential.FakeCommonTokenStorage
import com.okta.authfoundation.credential.TestConfiguration
import com.okta.authfoundation.credential.TokenMetadata
import com.okta.authfoundation.credential.createTestToken
import com.okta.authfoundation.credential.events.DefaultCredentialChangedEvent
import com.okta.authfoundation.events.Event
import com.okta.authfoundation.util.CoalescingOrchestrator
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(InternalAuthFoundationApi::class)
class CredentialMetadataTest {
    private val clock = TestConfiguration.FixedClock(1_000_000L)
    private val config = TestConfiguration.create(clock = clock)
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
        CredentialManager(
            client = client,
            storage = FakeCommonTokenStorage(),
            defaultIdStore = defaultIdStore
        )

    private suspend fun storeCredential(
        id: String = "cred-1",
        tags: Map<String, String> = emptyMap(),
    ): Credential =
        manager
            .store(
                token = createTestToken(id = id, configuration = config),
                tags = tags
            ).getOrThrow()

    @Test
    fun storeAsync_CreatesCredentialWithTags() =
        runTest {
            val credential = storeCredential(id = "m1", tags = mapOf("env" to "test"))

            assertEquals("m1", credential.id)
            assertEquals("test", credential.tags["env"])
        }

    @Test
    fun allIdsAsync_ReturnsStoredIds() =
        runTest {
            storeCredential(id = "a")
            storeCredential(id = "b")

            val ids = manager.allIds().getOrThrow()
            assertEquals(2, ids.size)
            assertTrue(ids.contains("a"))
            assertTrue(ids.contains("b"))
        }

    @Test
    fun metadataAsync_ReturnsTagsForStoredCredential() =
        runTest {
            storeCredential(id = "meta-1", tags = mapOf("label" to "primary"))

            val metadata = manager.metadata("meta-1").getOrThrow()
            assertNotNull(metadata)
            assertEquals("primary", metadata.tags["label"])
        }

    @Test
    fun metadataAsync_NonexistentId_ReturnsNull() =
        runTest {
            assertNull(manager.metadata("nonexistent").getOrThrow())
        }

    @Test
    fun setMetadataAsync_UpdatesTags() =
        runTest {
            storeCredential(id = "meta-2", tags = mapOf("old" to "value"))

            manager
                .setMetadata(
                    TokenMetadata(id = "meta-2", tags = mapOf("new" to "updated"), payloadData = null)
                ).getOrThrow()

            val metadata = manager.metadata("meta-2").getOrThrow()
            assertNotNull(metadata)
            assertEquals("updated", metadata.tags["new"])
            assertNull(metadata.tags["old"])
        }

    @Test
    fun setTagsAsync_PersistsAndEmitsEvent() =
        runTest {
            val credential = storeCredential(id = "tag-1")

            val updated = credential.setTagsAsync(mapOf("updated" to "yes")).getOrThrow()

            assertEquals("yes", updated.tags["updated"])
            val metadata = manager.metadata("tag-1").getOrThrow()
            assertNotNull(metadata)
            assertEquals("yes", metadata.tags["updated"])
        }

    @Test
    fun setDefaultAsync_SetsDefault() =
        runTest {
            val events = mutableListOf<Event>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { manager.events.collect { events.add(it) } }

            val credential = storeCredential(id = "def-1")
            events.clear()

            manager.setDefault(credential).getOrThrow()

            assertEquals("def-1", defaultIdStore.getDefaultCredentialId())
            val defaultChangedEvents = events.filterIsInstance<DefaultCredentialChangedEvent>()
            assertEquals(1, defaultChangedEvents.size)
            assertEquals("def-1", defaultChangedEvents[0].credential!!.id)
            job.cancel()
        }

    @Test
    fun setDefaultAsync_Null_ClearsDefault() =
        runTest {
            val events = mutableListOf<Event>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { manager.events.collect { events.add(it) } }

            val credential = storeCredential(id = "def-2")
            manager.setDefault(credential).getOrThrow()
            events.clear()

            manager.setDefault(null).getOrThrow()

            assertNull(defaultIdStore.getDefaultCredentialId())
            val defaultChangedEvents = events.filterIsInstance<DefaultCredentialChangedEvent>()
            assertEquals(1, defaultChangedEvents.size)
            assertNull(defaultChangedEvents[0].credential)
            job.cancel()
        }

    @Test
    fun getDefaultAsync_ReturnsDefaultCredential() =
        runTest {
            val credential = storeCredential(id = "def-3")
            manager.setDefault(credential).getOrThrow()

            val defaultCred = manager.getDefault().getOrThrow()
            assertNotNull(defaultCred)
            assertEquals("def-3", defaultCred.id)
        }

    @Test
    fun getDefaultAsync_NoDefault_ReturnsNull() =
        runTest {
            assertNull(manager.getDefault().getOrThrow())
        }

    @Test
    fun deleteAsync_ClearsDefaultIfMatching() =
        runTest {
            val credential = storeCredential(id = "def-del")
            manager.setDefault(credential).getOrThrow()

            credential.deleteAsync().getOrThrow()

            assertNull(defaultIdStore.getDefaultCredentialId())
        }

    @Test
    fun setTagsAsync_ReturnsNewSnapshotWithUpdatedTags() =
        runTest {
            val credential = storeCredential(id = "snap-tag", tags = mapOf("old" to "val"))
            val result = credential.setTagsAsync(mapOf("new" to "val"))

            assertTrue(result.isSuccess)
            val snapshot = result.getOrThrow()
            assertEquals("val", credential.tags["old"])
            assertNull(credential.tags["new"])
            assertEquals("val", snapshot.tags["new"])
            assertNull(snapshot.tags["old"])
        }

    @Test
    fun deleteAsync_OnDeletedCredential_ReturnsFailure() =
        runTest {
            val credential = storeCredential(id = "del-dup")
            credential.deleteAsync().getOrThrow()

            val secondResult = credential.deleteAsync()
            assertTrue(secondResult.isFailure)
        }

    @Test
    fun findTokens_ByTag() =
        runTest {
            storeCredential(id = "f1", tags = mapOf("role" to "admin"))
            storeCredential(id = "f2", tags = mapOf("role" to "user"))
            storeCredential(id = "f3", tags = mapOf("role" to "admin"))

            val admins = manager.dataSource.findTokens { it.tags["role"] == "admin" }
            assertEquals(2, admins.size)
            assertTrue(admins.any { it.id == "f1" })
            assertTrue(admins.any { it.id == "f3" })
        }
}
