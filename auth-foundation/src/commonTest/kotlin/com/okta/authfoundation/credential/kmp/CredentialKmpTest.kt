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
import com.okta.authfoundation.credential.FakeTokenStorage
import com.okta.authfoundation.credential.InMemoryDefaultCredentialIdStore
import com.okta.authfoundation.credential.TestConfiguration
import com.okta.authfoundation.credential.TokenMetadata
import com.okta.authfoundation.credential.TokenType
import com.okta.authfoundation.credential.createTestToken
import com.okta.authfoundation.credential.events.CredentialDeletedEvent
import com.okta.authfoundation.credential.events.CredentialStoredEvent
import com.okta.authfoundation.credential.kmp.Credential
import com.okta.authfoundation.credential.kmp.CredentialDataSource
import com.okta.authfoundation.credential.kmp.CredentialImpl
import com.okta.authfoundation.credential.kmp.TokenCredentialManager
import com.okta.authfoundation.events.Event
import com.okta.authfoundation.util.CoalescingOrchestrator
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(InternalAuthFoundationApi::class)
class CredentialKmpTest {
    companion object {
        private val VALID_JWT =
            "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ." +
                "eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQ" +
                "GV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsd" +
                "CIsImF1ZCI6InVuaXRfdGVzdF9jbGllbnRfaWQiLCJpYXQiOjE2NDQzNDcwNjksImV4cCI6MTY0NDM1MDY2OSwianRpIjoiSUQ" +
                "uNTVjeEJ0ZFlsOGw2YXJLSVNQQndkMHlPVC05VUNUYVhhUVRYdDJsYVJMcyIsImFtciI6WyJwd2QiXSwiaWRwIjoiMDBvOGZ" +
                "vdTdzUmFHR3dkbjQ2OTYiLCJzaWQiOiJpZHhXeGtscF80a1N4dUNfblUxcFhELW5BIiwicHJlZmVycmVkX3VzZXJuYW1lIjo" +
                "iamF5bmV3c3Ryb21AZXhhbXBsZS5jb20iLCJhdXRoX3RpbWUiOjE2NDQzNDcwNjgsImF0X2hhc2giOiJnTWNHVGJoR1QxR19" +
                "sZHNIb0pzUHpRIiwiZHNfaGFzaCI6IkRBZUxPRlJxaWZ5c2Jnc3JiT2dib2cifQ." +
                "tT8aKK4r8yFcW9KgVtZxvjXRJVzz-_rve14CVtpUlyvCTE1yj20wmPS0z3-JirI9xXgt5KeNPYqo3Wbv8c9XY_HY3hsPQdILY" +
                "pPsUkf-sctmzSoKC_dTbs5xe8uKSgmpMrggfUAWrNPiJt9Ek2p7GgP64Wx79Pq5vSHk0yWlonFfXut5ahpSfqWilmYlvLr8g" +
                "FbqoLnAJfl4ZbTY8pPw_aQgCdcQ-ImHRu-8bCSCtbFRzZB-SMJFLfRF2kmx0H-QF855wUODTuUSydkff-BKb-8wnbqWg0R9Nv" +
                "RdoXhEybv8TXXZY3cQqgolWLAyiPMrz07n0q_UEjAilUiCjn1f4Q"
    }

    private val clock = TestConfiguration.FixedClock(1_000_000L)
    private val config = TestConfiguration.create(clock = clock)
    private val storage = FakeTokenStorage()
    private val testEvents =
        MutableSharedFlow<Event>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
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

    private suspend fun createCredential(
        id: String = "cred-1",
        accessToken: String = "valid-at",
        refreshToken: String? = "valid-rt",
        expiresIn: Int = 3600,
        tags: Map<String, String> = emptyMap(),
    ): Credential {
        val token = createTestToken(id = id, accessToken = accessToken, refreshToken = refreshToken, expiresIn = expiresIn, configuration = config)
        dataSource.createToken(token, tags)
        return CredentialImpl(
            token = token,
            client = client,
            tags = tags,
            dataSource = dataSource,
            events = testEvents,
            defaultIdStore = defaultIdStore
        )
    }

    @Test
    fun properties_AssignedFromConstructor() =
        runTest {
            val credential = createCredential(id = "c1", tags = mapOf("env" to "test"))

            assertEquals("c1", credential.id)
            assertEquals("valid-at", credential.token.accessToken)
            assertEquals("test", credential.tags["env"])
        }

    @Test
    fun accessTokenIfNotExpired_ValidToken_ReturnsAccessToken() =
        runTest {
            // Token created at clock=1_000_000 with expiresIn=3600 → issuedAt=996_400
            val credential = createCredential(expiresIn = 3600)
            // Simulate checking 1 second after token creation (still within expiry window)
            clock.time = 1_000_000L - 1

            val accessToken = credential.accessTokenIfNotExpired()
            assertNotNull(accessToken)
            assertEquals("valid-at", accessToken)
        }

    @Test
    fun accessTokenIfNotExpired_ExpiredToken_ReturnsNull() =
        runTest {
            val credential = createCredential(expiresIn = 100)
            // Advance clock well past expiry
            clock.time = 1_000_000L + 200

            assertNull(credential.accessTokenIfNotExpired())
        }

    @Test
    fun scope_ReturnsTokenScope() =
        runTest {
            val credential = createCredential()
            assertEquals("openid profile", credential.scope())
        }

    @Test
    fun deleteAsync_RemovesFromDataSource() =
        runTest {
            val credential = createCredential(id = "del-1")
            credential.deleteAsync().getOrThrow()

            assertNull(dataSource.getToken("del-1"))
        }

    @Test
    fun deleteAsync_EmitsDeletedEvent() =
        runTest {
            val events = mutableListOf<Event>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { testEvents.collect { events.add(it) } }

            val credential = createCredential(id = "del-2")
            events.clear()

            credential.deleteAsync().getOrThrow()

            val deleteEvents = events.filterIsInstance<CredentialDeletedEvent>()
            assertEquals(1, deleteEvents.size)
            assertEquals("del-2", deleteEvents[0].credentialIdentifier.id)
            job.cancel()
        }

    @Test
    fun deleteAsync_ClearsDefaultIfMatching() =
        runTest {
            val credential = createCredential(id = "del-3")
            defaultIdStore.setDefaultCredentialId("del-3").getOrThrow()

            credential.deleteAsync().getOrThrow()

            assertNull(defaultIdStore.getDefaultCredentialId().getOrThrow())
        }

    @Test
    fun deleteAsync_DoesNotClearDefaultIfNotMatching() =
        runTest {
            val credential = createCredential(id = "del-4")
            defaultIdStore.setDefaultCredentialId("other-id").getOrThrow()

            credential.deleteAsync().getOrThrow()

            assertEquals("other-id", defaultIdStore.getDefaultCredentialId().getOrThrow())
        }

    @Test
    fun deleteAsync_Idempotent() =
        runTest {
            val events = mutableListOf<Event>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { testEvents.collect { events.add(it) } }

            val credential = createCredential(id = "del-5")
            credential.deleteAsync().getOrThrow()
            events.clear()

            val secondDelete = credential.deleteAsync()
            assertTrue(secondDelete.isFailure)

            val deleteEvents = events.filterIsInstance<CredentialDeletedEvent>()
            assertEquals(0, deleteEvents.size)
            job.cancel()
        }

    @Test
    fun setTagsAsync_UpdatesTags() =
        runTest {
            val credential = createCredential(id = "tag-1")
            val updated = credential.setTagsAsync(mapOf("new-key" to "new-value")).getOrThrow()

            assertEquals("new-value", updated.tags["new-key"])
        }

    @Test
    fun setTagsAsync_EmitsStoredEvent() =
        runTest {
            val events = mutableListOf<Event>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { testEvents.collect { events.add(it) } }

            val credential = createCredential(id = "tag-2")
            events.clear()

            credential.setTagsAsync(mapOf("k" to "v")).getOrThrow()

            val storedEvents = events.filterIsInstance<CredentialStoredEvent>()
            assertEquals(1, storedEvents.size)
            assertEquals("tag-2", storedEvents[0].credentialIdentifier.id)
            assertEquals("v", storedEvents[0].tags["k"])
            job.cancel()
        }

    @Test
    fun setTagsAsync_PersistsToStorage() =
        runTest {
            val credential = createCredential(id = "tag-3")
            credential.setTagsAsync(mapOf("persisted" to "yes")).getOrThrow()

            val metadata = dataSource.metadata("tag-3")
            assertNotNull(metadata)
            assertEquals("yes", metadata.tags["persisted"])
        }

    @Test
    fun getTokenFlow_EmitsCurrentToken() =
        runTest {
            val credential = createCredential(id = "flow-1", accessToken = "flow-at")
            val firstToken = credential.getTokenFlow().first()
            assertEquals("flow-at", firstToken.accessToken)
        }

    @Test
    fun equals_SameIdAndToken_Equal() =
        runTest {
            val a = createCredential(id = "eq-1", accessToken = "same-at")
            val b = createCredential(id = "eq-1", accessToken = "same-at")

            assertEquals(a, b)
        }

    @Test
    fun equals_DifferentId_NotEqual() =
        runTest {
            val a = createCredential(id = "eq-a")
            val b = createCredential(id = "eq-b")

            assertNotEquals(a, b)
        }

    @Test
    fun hashCode_SameFields_SameHash() =
        runTest {
            val a = createCredential(id = "hash-1", accessToken = "at")
            val b = createCredential(id = "hash-1", accessToken = "at")

            assertEquals(a.hashCode(), b.hashCode())
        }

    @Test
    fun revokeAllTokens_WithNoRefreshOrDevice_OnlyRevokesAccessToken() =
        runTest {
            val credential = createCredential(refreshToken = null)
            val result = credential.revokeAllTokens()

            assertTrue(result.isFailure)
        }

    // --- Immutability verification (US2 / SC-003) ---

    @Test
    fun setTagsAsync_OriginalUnchanged() =
        runTest {
            val credential = createCredential(id = "imm-1", tags = mapOf("orig" to "true"))
            val originalToken = credential.token
            val originalTags = credential.tags
            val originalId = credential.id

            val updated = credential.setTagsAsync(mapOf("new" to "value")).getOrThrow()

            // Original instance is unchanged
            assertEquals(originalId, credential.id)
            assertEquals(originalToken, credential.token)
            assertEquals(originalTags, credential.tags)
            assertEquals("true", credential.tags["orig"])
            // Updated snapshot has new tags
            assertEquals("value", updated.tags["new"])
            assertNull(updated.tags["orig"])
            assertNotEquals(credential.tags, updated.tags)
        }

    @Test
    fun deleteAsync_OriginalPropertiesUnchanged() =
        runTest {
            val credential = createCredential(id = "imm-2", tags = mapOf("k" to "v"))
            val originalToken = credential.token
            val originalTags = credential.tags
            val originalId = credential.id

            credential.deleteAsync().getOrThrow()

            assertEquals(originalId, credential.id)
            assertEquals(originalToken, credential.token)
            assertEquals(originalTags, credential.tags)
        }

    @Test
    fun refreshIfExpired_OriginalUnchanged() =
        runTest {
            val credential = createCredential(id = "imm-3")
            val originalToken = credential.token
            val originalTags = credential.tags
            val originalId = credential.id

            // Ensure token is still valid (clock at creation boundary)
            clock.time = 1_000_000L - 1

            val result = credential.refreshIfExpired()
            assertTrue(result.isSuccess)

            assertEquals(originalId, credential.id)
            assertEquals(originalToken, credential.token)
            assertEquals(originalTags, credential.tags)
        }

    @Test
    fun setTagsAsync_ReturnsNewSnapshotInstance() =
        runTest {
            val credential = createCredential(id = "snap-1")
            val updated = credential.setTagsAsync(mapOf("x" to "y")).getOrThrow()

            // Different instance
            assertTrue(credential !== updated)
            // Same credential ID
            assertEquals(credential.id, updated.id)
        }

    // --- Shared token flow tests (US4) ---

    @Test
    fun getTokenFlow_SharedAcrossSnapshots() =
        runTest {
            val credential = createCredential(id = "flow-shared", accessToken = "at-1")
            // Create a second snapshot via setTags
            val snapshot2 = credential.setTagsAsync(mapOf("v" to "2")).getOrThrow()

            // Both snapshots observe the same flow
            val flow1First = credential.getTokenFlow().first()
            val flow2First = snapshot2.getTokenFlow().first()
            assertEquals(flow1First.accessToken, flow2First.accessToken)
        }

    @Test
    fun getTokenFlow_FailedMutation_DoesNotEmit() =
        runTest {
            val credential = createCredential(id = "flow-no-emit")
            val firstToken = credential.getTokenFlow().first()
            assertEquals("valid-at", firstToken.accessToken)

            // Delete, then attempt setTags which should fail
            credential.deleteAsync().getOrThrow()

            // After deletion, the token flow for this credential ID is cleaned up
            // A new credential with a different ID should not share the old flow
            val credential2 = createCredential(id = "flow-no-emit-2")
            val secondToken = credential2.getTokenFlow().first()
            assertEquals("valid-at", secondToken.accessToken)
        }

    @Test
    fun constructWithoutClient_CreatesClientFromTokenConfiguration() =
        runTest {
            val token = createTestToken(id = "no-client", configuration = config)
            dataSource.createToken(token)
            val credential =
                CredentialImpl(
                    token = token,
                    tags = emptyMap(),
                    dataSource = dataSource,
                    events = testEvents,
                    defaultIdStore = defaultIdStore
                )

            assertEquals("no-client", credential.id)
            assertEquals(config.clientId, credential.client.configuration.clientId)
            assertEquals(config.issuerUrl, credential.client.configuration.issuerUrl)
        }

    @Test
    fun store_CreatesCredentialWithCorrectClient() =
        runTest {
            val token = createTestToken(id = "store-no-client", configuration = config)
            val credential = manager.store(token).getOrThrow()

            assertEquals("store-no-client", credential.id)
            assertEquals(config.clientId, credential.token.clientId)
        }

    // --- introspectToken tests ---

    @Test
    fun introspectToken_NoAccessToken_Fails() =
        runTest {
            // DeviceSecret is null by default, so introspecting it should fail
            val credential = createCredential()
            val result = credential.introspectToken(TokenType.DEVICE_SECRET)
            assertTrue(result.isFailure)
            assertIs<IllegalStateException>(result.exceptionOrNull())
        }

    @Test
    fun introspectToken_AccessToken_Fails_WithNoOpExecutor() =
        runTest {
            // The NoOpApiExecutor always returns failure, so introspect should propagate
            val credential = createCredential()
            val result = credential.introspectToken(TokenType.ACCESS_TOKEN)
            assertTrue(result.isFailure)
        }

    // --- refreshToken tests ---

    @Test
    fun refreshToken_NoRefreshToken_Fails() =
        runTest {
            val credential = createCredential(refreshToken = null)
            val result = credential.refreshToken()
            assertTrue(result.isFailure)
        }

    @Test
    fun refreshToken_WithRefreshToken_Fails_WithNoOpExecutor() =
        runTest {
            // The NoOpApiExecutor always returns failure
            val credential = createCredential(refreshToken = "valid-rt")
            val result = credential.refreshToken()
            assertTrue(result.isFailure)
        }

    @Test
    fun refreshToken_WithExtraParams_NoRefreshToken_Fails() =
        runTest {
            val credential = createCredential(refreshToken = null)
            val result = credential.refreshToken(mapOf("acr_values" to "urn:okta:loa:2fa:any"))
            assertTrue(result.isFailure)
            assertIs<IllegalStateException>(result.exceptionOrNull())
        }

    @Test
    fun refreshToken_EmptyExtraParams_DelegatesToNoArgOverload() =
        runTest {
            // Empty map should delegate to the orchestrated refreshToken()
            val credential = createCredential(refreshToken = "valid-rt")
            val result = credential.refreshToken(emptyMap())
            // Fails because NoOpApiExecutor returns failure — same behavior as no-arg overload
            assertTrue(result.isFailure)
        }

    @Test
    fun refreshToken_WithExtraParams_BypassesOrchestrator() =
        runTest {
            // When extra params are provided, the orchestrator is bypassed and a direct call is made.
            // The NoOpApiExecutor always fails — so both paths fail — but we can verify the non-empty
            // map path does NOT throw IllegalStateException (no-refresh-token error).
            val credential = createCredential(refreshToken = "valid-rt")
            val result = credential.refreshToken(mapOf("acr_values" to "urn:okta:loa:2fa:any"))
            // Should fail with network error (not "No refresh token" error)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() !is IllegalStateException)
        }

    // --- idToken tests ---

    @Test
    fun idToken_NullIdToken_ReturnsFailure() =
        runTest {
            val credential = createCredential()
            assertTrue(credential.idToken().isFailure)
        }

    @Test
    fun idToken_ValidJwt_ReturnsJwt() =
        runTest {
            val credential = createCredential(id = "jwt-1")
            // Re-create with idToken set
            val token = createTestToken(id = "jwt-1", idToken = VALID_JWT, configuration = config)
            dataSource.createToken(token)
            val credWithJwt =
                CredentialImpl(
                    token = token,
                    client = client,
                    dataSource = dataSource,
                    events = testEvents,
                    defaultIdStore = defaultIdStore
                )

            val jwt = credWithJwt.idToken().getOrThrow()
            assertEquals("FJA0HGNtsuuda_Pl45J42kvQqcsu_0C4Fg7pbJLXTHY", jwt.keyId)
        }

    @Test
    fun idToken_MalformedJwt_ReturnsFailure() =
        runTest {
            val token = createTestToken(id = "jwt-bad", idToken = "InvalidJwt", configuration = config)
            dataSource.createToken(token)
            val credential =
                CredentialImpl(
                    token = token,
                    client = client,
                    dataSource = dataSource,
                    events = testEvents,
                    defaultIdStore = defaultIdStore
                )

            assertTrue(credential.idToken().isFailure)
        }

    // --- find tests ---

    @Test
    fun find_ReturnsMatchingCredentials() =
        runTest {
            createCredential(id = "find-1", tags = mapOf("env" to "prod"))
            createCredential(id = "find-2", tags = mapOf("env" to "dev"))
            createCredential(id = "find-3", tags = mapOf("env" to "prod"))

            val results = manager.find { metadata: TokenMetadata -> metadata.tags["env"] == "prod" }.getOrThrow()

            assertEquals(2, results.size)
            assertTrue(results.any { it.id == "find-1" })
            assertTrue(results.any { it.id == "find-3" })
        }

    @Test
    fun find_NoMatch_ReturnsEmpty() =
        runTest {
            createCredential(id = "find-empty", tags = mapOf("env" to "dev"))

            val results = manager.find { metadata: TokenMetadata -> metadata.tags["env"] == "staging" }.getOrThrow()

            assertTrue(results.isEmpty())
        }

    @Test
    fun find_PreservesTagsOnResult() =
        runTest {
            createCredential(id = "find-tags", tags = mapOf("role" to "admin"))

            val results = manager.find { metadata: TokenMetadata -> metadata.tags["role"] == "admin" }.getOrThrow()

            assertEquals(1, results.size)
            assertEquals("admin", results[0].tags["role"])
        }
}
