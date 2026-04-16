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
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.credential.events.TokenStorageAccessErrorEvent
import com.okta.authfoundation.credential.kmp.CredentialDataSource
import com.okta.authfoundation.credential.kmp.TokenData
import com.okta.authfoundation.credential.kmp.TokenStorage
import com.okta.authfoundation.events.Event
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(InternalAuthFoundationApi::class)
class DatabaseRecoveryTest {
    /**
     * Fault-injecting [TokenStorage] that fails on demand.
     */
    private class FaultyTokenStorage(
        private val innerDelegate: TokenStorage = FakeTokenStorage(),
    ) : TokenStorage {
        private var shouldFailNext = false

        fun injectFault() {
            shouldFailNext = true
        }

        private fun checkFault() {
            if (shouldFailNext) {
                shouldFailNext = false
                throw RuntimeException("Injected storage fault")
            }
        }

        override suspend fun allIds(): Result<List<String>> {
            checkFault()
            return innerDelegate.allIds()
        }

        override suspend fun metadata(id: String): Result<TokenMetadata?> {
            checkFault()
            return innerDelegate.metadata(id)
        }

        override suspend fun setMetadata(metadata: TokenMetadata): Result<Unit> {
            checkFault()
            return innerDelegate.setMetadata(metadata)
        }

        override suspend fun add(
            token: TokenInfo,
            metadata: TokenMetadata,
        ): Result<Unit> {
            checkFault()
            return innerDelegate.add(token, metadata)
        }

        override suspend fun remove(id: String): Result<Unit> {
            checkFault()
            return innerDelegate.remove(id)
        }

        override suspend fun replace(token: TokenInfo): Result<Unit> {
            checkFault()
            return innerDelegate.replace(token)
        }

        override suspend fun getToken(id: String): Result<TokenInfo> {
            checkFault()
            return innerDelegate.getToken(id)
        }

        fun getInnerDelegate(): TokenStorage = innerDelegate
    }

    @Test
    fun getToken_StorageFails_EmitsErrorEvent() =
        runTest {
            val events = mutableListOf<Event>()
            val eventsFlow =
                MutableSharedFlow<Event>(
                    replay = 64, // Replay buffer to retain events
                    extraBufferCapacity = 64,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                )
            val collectorJob = launch { eventsFlow.collect { events.add(it) } }

            val faultyStorage = FaultyTokenStorage()
            val dataSource = CredentialDataSource(faultyStorage, eventsFlow)

            // Store a token first
            val token = createTestToken(id = "test-1", configuration = TestConfiguration.create())
            val metadata = createTestMetadata(id = "test-1")
            faultyStorage.getInnerDelegate().add(token, metadata).getOrThrow()

            // Inject fault and try to read
            faultyStorage.injectFault()
            assertFailsWith<RuntimeException> {
                dataSource.getToken("test-1")
            }

            // Give collector a chance to run
            delay(50)
            // Check that error event was emitted
            val errorEvents = events.filterIsInstance<TokenStorageAccessErrorEvent>()
            assertEquals(1, errorEvents.size)
            assertIs<RuntimeException>(errorEvents[0].exception)

            collectorJob.cancel()
        }

    @Test
    fun createToken_StorageFails_EmitsErrorEvent() =
        runTest {
            val events = mutableListOf<Event>()
            val eventsFlow =
                MutableSharedFlow<Event>(
                    replay = 64,
                    extraBufferCapacity = 64,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                )
            val collectorJob = launch { eventsFlow.collect { events.add(it) } }

            val faultyStorage = FaultyTokenStorage()
            val dataSource = CredentialDataSource(faultyStorage, eventsFlow)

            val token = createTestToken(id = "new-1", configuration = TestConfiguration.create())
            faultyStorage.injectFault()

            assertFailsWith<RuntimeException> {
                dataSource.createToken(token)
            }

            delay(50)
            val errorEvents = events.filterIsInstance<TokenStorageAccessErrorEvent>()
            assertEquals(1, errorEvents.size)
            collectorJob.cancel()
        }

    @Test
    fun allIds_StorageFails_EmitsErrorEvent() =
        runTest {
            val events = mutableListOf<Event>()
            val eventsFlow =
                MutableSharedFlow<Event>(
                    replay = 64,
                    extraBufferCapacity = 64,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                )
            val collectorJob = launch { eventsFlow.collect { events.add(it) } }

            val faultyStorage = FaultyTokenStorage()
            val dataSource = CredentialDataSource(faultyStorage, eventsFlow)

            faultyStorage.injectFault()
            assertFailsWith<RuntimeException> {
                dataSource.allIds()
            }

            delay(50)
            val errorEvents = events.filterIsInstance<TokenStorageAccessErrorEvent>()
            assertEquals(1, errorEvents.size)
            collectorJob.cancel()
        }

    @Test
    fun metadata_StorageFails_EmitsErrorEvent() =
        runTest {
            val events = mutableListOf<Event>()
            val eventsFlow =
                MutableSharedFlow<Event>(
                    replay = 64,
                    extraBufferCapacity = 64,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                )
            val collectorJob = launch { eventsFlow.collect { events.add(it) } }

            val faultyStorage = FaultyTokenStorage()
            val dataSource = CredentialDataSource(faultyStorage, eventsFlow)

            faultyStorage.injectFault()
            assertFailsWith<RuntimeException> {
                dataSource.metadata("any-id")
            }

            delay(50)
            val errorEvents = events.filterIsInstance<TokenStorageAccessErrorEvent>()
            assertEquals(1, errorEvents.size)
            collectorJob.cancel()
        }

    @Test
    fun remove_StorageFails_EmitsErrorEvent() =
        runTest {
            val events = mutableListOf<Event>()
            val eventsFlow =
                MutableSharedFlow<Event>(
                    replay = 64,
                    extraBufferCapacity = 64,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                )
            val collectorJob = launch { eventsFlow.collect { events.add(it) } }

            val faultyStorage = FaultyTokenStorage()
            val dataSource = CredentialDataSource(faultyStorage, eventsFlow)

            // Add a token first
            val token = createTestToken(id = "to-remove", configuration = TestConfiguration.create())
            val metadata = createTestMetadata(id = "to-remove")
            faultyStorage.getInnerDelegate().add(token, metadata).getOrThrow()

            faultyStorage.injectFault()
            assertFailsWith<RuntimeException> {
                dataSource.remove("to-remove")
            }

            delay(50)
            val errorEvents = events.filterIsInstance<TokenStorageAccessErrorEvent>()
            assertEquals(1, errorEvents.size)
            collectorJob.cancel()
        }

    @Test
    fun setMetadata_StorageFails_EmitsErrorEvent() =
        runTest {
            val events = mutableListOf<Event>()
            val eventsFlow =
                MutableSharedFlow<Event>(
                    replay = 64,
                    extraBufferCapacity = 64,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                )
            val collectorJob = launch { eventsFlow.collect { events.add(it) } }

            val faultyStorage = FaultyTokenStorage()
            val dataSource = CredentialDataSource(faultyStorage, eventsFlow)

            // Add a token first
            val token = createTestToken(id = "meta-test", configuration = TestConfiguration.create())
            val metadata = createTestMetadata(id = "meta-test")
            faultyStorage.getInnerDelegate().add(token, metadata).getOrThrow()

            faultyStorage.injectFault()
            val newMetadata = TokenMetadata(id = "meta-test", tags = mapOf("updated" to "true"), payloadData = null)
            assertFailsWith<RuntimeException> {
                dataSource.setMetadata(newMetadata)
            }

            delay(50)
            val errorEvents = events.filterIsInstance<TokenStorageAccessErrorEvent>()
            assertEquals(1, errorEvents.size)
            collectorJob.cancel()
        }

    @Test
    fun sharedFlowBasicTest() =
        runTest {
            val flow =
                MutableSharedFlow<Int>(
                    replay = 64,
                    extraBufferCapacity = 64,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                )
            flow.tryEmit(42)
            val value = flow.timeout(1000.milliseconds).first()
            assertEquals(42, value)
        }

    @Test
    fun eventFlowReceivedEvent() =
        runTest {
            val eventsFlow =
                MutableSharedFlow<Event>(
                    replay = 64,
                    extraBufferCapacity = 64,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                )

            // Test that we can emit and receive an event directly
            eventsFlow.emit(TokenStorageAccessErrorEvent(exception = Exception("test"), shouldClearStorageAndTryAgain = false))
            val event = eventsFlow.timeout(1000.milliseconds).first()
            assertIs<TokenStorageAccessErrorEvent>(event)
        }

    @Test
    fun replaceToken_StorageFails_EmitsErrorEvent() =
        runTest {
            val events = mutableListOf<Event>()
            val eventsFlow =
                MutableSharedFlow<Event>(
                    replay = 64,
                    extraBufferCapacity = 64,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                )
            val collectorJob = launch { eventsFlow.collect { events.add(it) } }

            val faultyStorage = FaultyTokenStorage()
            val dataSource = CredentialDataSource(faultyStorage, eventsFlow)

            // Add a token first through dataSource to populate cache
            val token = createTestToken(id = "replace-test", configuration = TestConfiguration.create())
            dataSource.createToken(token)

            faultyStorage.injectFault()
            val newToken =
                createTestToken(
                    id = "replace-test",
                    accessToken = "new-at",
                    configuration = TestConfiguration.create()
                )
            assertFailsWith<RuntimeException> {
                dataSource.replaceToken(newToken)
            }

            // Give collector a chance to run - use multiple delays to ensure event is processed
            delay(50)
            delay(50)
            // Check that error event was emitted
            val errorEvents = events.filterIsInstance<TokenStorageAccessErrorEvent>()
            assertEquals(1, errorEvents.size, "Expected 1 error event but got ${errorEvents.size} total events: ${events.map { it::class.simpleName }}")
            assertIs<RuntimeException>(errorEvents[0].exception)

            collectorJob.cancel()
        }

    @Test
    fun errorEvent_ShouldClearStorageAndTryAgain_DefaultsFalse() =
        runTest {
            val events = mutableListOf<Event>()
            val eventsFlow =
                MutableSharedFlow<Event>(
                    replay = 64,
                    extraBufferCapacity = 64,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                )
            val collectorJob = launch { eventsFlow.collect { events.add(it) } }

            val faultyStorage = FaultyTokenStorage()
            val dataSource = CredentialDataSource(faultyStorage, eventsFlow)

            faultyStorage.injectFault()
            assertFailsWith<RuntimeException> {
                dataSource.allIds()
            }

            delay(50)
            val errorEvents = events.filterIsInstance<TokenStorageAccessErrorEvent>()
            assertEquals(1, errorEvents.size)
            assertEquals(false, errorEvents[0].shouldClearStorageAndTryAgain)
            collectorJob.cancel()
        }

    @Test
    fun errorEvent_WithRecoveryEnabled_RetriesOperation() =
        runTest {
            val eventsFlow =
                MutableSharedFlow<Event>(
                    replay = 64,
                    extraBufferCapacity = 64,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                )

            // Collect events but intercept to enable recovery
            val collectorJob =
                launch {
                    eventsFlow.collect { event ->
                        if (event is TokenStorageAccessErrorEvent) {
                            event.shouldClearStorageAndTryAgain = true
                        }
                    }
                }

            val faultyStorage = FaultyTokenStorage()
            val dataSource = CredentialDataSource(faultyStorage, eventsFlow)

            // Add a token first
            val token = createTestToken(id = "retry-test", configuration = TestConfiguration.create())
            val metadata = createTestMetadata(id = "retry-test")
            faultyStorage.getInnerDelegate().add(token, metadata).getOrThrow()

            // Inject a fault - it will be emitted but recovery will retry
            faultyStorage.injectFault()
            val result = dataSource.getToken("retry-test")

            // With recovery enabled, the retry should succeed
            assertTrue(result != null, "Result should not be null")
            assertEquals("retry-test", result!!.id)
            collectorJob.cancel()
        }

    @Test
    fun noErrorsFlow_StorageFails_StillThrowsException() =
        runTest {
            val faultyStorage = FaultyTokenStorage()
            val dataSource = CredentialDataSource(faultyStorage) // No eventsFlow

            faultyStorage.injectFault()
            assertFailsWith<RuntimeException> {
                dataSource.allIds()
            }
        }
}
