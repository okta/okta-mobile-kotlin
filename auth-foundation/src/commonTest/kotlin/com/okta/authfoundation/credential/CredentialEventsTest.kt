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
import com.okta.authfoundation.credential.events.CredentialCreatedEvent
import com.okta.authfoundation.credential.events.CredentialDeletedEvent
import com.okta.authfoundation.credential.events.CredentialStoredEvent
import com.okta.authfoundation.credential.events.DefaultCredentialChangedEvent
import com.okta.authfoundation.credential.kmp.Credential
import com.okta.authfoundation.credential.kmp.CredentialManager
import com.okta.authfoundation.credential.kmp.InMemoryDefaultCredentialIdStore
import com.okta.authfoundation.events.Event
import com.okta.authfoundation.util.CoalescingOrchestrator
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(InternalAuthFoundationApi::class)
class CredentialEventsTest {
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

    private fun createManager(): CredentialManager =
        CredentialManager(
            client = client,
            storage = FakeTokenStorage(),
            defaultIdStore = InMemoryDefaultCredentialIdStore()
        )

    private suspend fun CredentialManager.storeCredential(
        id: String,
        tags: Map<String, String> = emptyMap(),
    ): Credential =
        store(
            token = createTestToken(id = id, configuration = config),
            tags = tags
        ).getOrThrow()

    @Test
    fun storeAsync_EmitsCreatedEvent() =
        runTest {
            val manager = createManager()
            val events = mutableListOf<Event>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { manager.events.collect { events.add(it) } }

            manager.storeCredential("ev-1")

            val created = events.filterIsInstance<CredentialCreatedEvent>()
            assertEquals(1, created.size)
            assertEquals("ev-1", created[0].credential.id)
            job.cancel()
        }

    @Test
    fun deleteAsync_EmitsDeletedEvent() =
        runTest {
            val manager = createManager()
            val events = mutableListOf<Event>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { manager.events.collect { events.add(it) } }

            val credential = manager.storeCredential("ev-2")
            events.clear()

            credential.deleteAsync().getOrThrow()

            val deleted = events.filterIsInstance<CredentialDeletedEvent>()
            assertEquals(1, deleted.size)
            assertEquals("ev-2", deleted[0].credential.id)
            job.cancel()
        }

    @Test
    fun setTagsAsync_EmitsStoredEvent() =
        runTest {
            val manager = createManager()
            val events = mutableListOf<Event>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { manager.events.collect { events.add(it) } }

            val credential = manager.storeCredential("ev-3")
            events.clear()

            credential.setTagsAsync(mapOf("k" to "v")).getOrThrow()

            val stored = events.filterIsInstance<CredentialStoredEvent>()
            assertEquals(1, stored.size)
            assertEquals("ev-3", stored[0].credential.id)
            assertEquals("v", stored[0].tags["k"])
            job.cancel()
        }

    @Test
    fun setDefaultAsync_EmitsDefaultChangedEvent() =
        runTest {
            val manager = createManager()
            val events = mutableListOf<Event>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { manager.events.collect { events.add(it) } }

            val credential = manager.storeCredential("ev-4")
            events.clear()

            manager.setDefault(credential).getOrThrow()

            val changed = events.filterIsInstance<DefaultCredentialChangedEvent>()
            assertEquals(1, changed.size)
            assertEquals("ev-4", changed[0].credential?.id)
            job.cancel()
        }

    @Test
    fun setDefaultAsync_Null_EmitsDefaultChangedWithNullId() =
        runTest {
            val manager = createManager()
            val events = mutableListOf<Event>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { manager.events.collect { events.add(it) } }

            val credential = manager.storeCredential("ev-5")
            manager.setDefault(credential).getOrThrow()
            events.clear()

            manager.setDefault(null).getOrThrow()

            val changed = events.filterIsInstance<DefaultCredentialChangedEvent>()
            assertEquals(1, changed.size)
            assertEquals(null, changed[0].credential)
            job.cancel()
        }

    @Test
    fun noCollectors_OperationsCompleteWithoutError() =
        runTest {
            val manager = createManager()
            // No collectors attached — events are silently dropped
            val cred = manager.storeCredential("ev-6")
            cred.setTagsAsync(mapOf("a" to "b")).getOrThrow()
            cred.deleteAsync().getOrThrow()
        }

    @Test
    fun deleteAsync_Failure_NoEventEmitted() =
        runTest {
            val manager = createManager()
            val events = mutableListOf<Event>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { manager.events.collect { events.add(it) } }

            val credential = manager.storeCredential("ev-no-del")
            credential.deleteAsync().getOrThrow()
            events.clear()

            // Second delete should fail
            val result = credential.deleteAsync()
            assertTrue(result.isFailure)

            // No events emitted on failure
            val deleted = events.filterIsInstance<CredentialDeletedEvent>()
            assertEquals(0, deleted.size)
            job.cancel()
        }

    @Test
    fun setTagsAsync_Failure_NoEventEmitted() =
        runTest {
            val manager = createManager()
            val events = mutableListOf<Event>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { manager.events.collect { events.add(it) } }

            val credential = manager.storeCredential("ev-no-tag")
            credential.deleteAsync().getOrThrow()
            events.clear()

            // setTags on deleted credential should fail
            val result = credential.setTagsAsync(mapOf("k" to "v"))
            assertTrue(result.isFailure)

            // No events emitted on failure
            val stored = events.filterIsInstance<CredentialStoredEvent>()
            assertEquals(0, stored.size)
            job.cancel()
        }

    @Test
    fun multipleOperations_EventsEmittedInOrder() =
        runTest {
            val manager = createManager()
            val events = mutableListOf<Event>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { manager.events.collect { events.add(it) } }

            val credential = manager.storeCredential("ev-7")
            credential.setTagsAsync(mapOf("x" to "y")).getOrThrow()
            credential.deleteAsync().getOrThrow()

            assertEquals(3, events.size)
            assertIs<CredentialCreatedEvent>(events[0])
            assertIs<CredentialStoredEvent>(events[1])
            assertIs<CredentialDeletedEvent>(events[2])
            job.cancel()
        }
}
