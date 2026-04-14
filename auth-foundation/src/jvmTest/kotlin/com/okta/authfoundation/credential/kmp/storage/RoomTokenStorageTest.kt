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
package com.okta.authfoundation.credential.kmp.storage

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.okta.authfoundation.credential.TestOAuth2ClientConfiguration
import com.okta.authfoundation.credential.TokenMetadata
import com.okta.authfoundation.credential.kmp.EncryptionResult
import com.okta.authfoundation.credential.kmp.TokenData
import com.okta.authfoundation.credential.kmp.TokenEncryptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomTokenStorageTest {
    private val configuration = TestOAuth2ClientConfiguration.create()
    private val encryptionHandler = PassthroughEncryptionHandler()

    private val database: TokenDatabase =
        Room
            .inMemoryDatabaseBuilder<TokenDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    private val storage = RoomTokenStorage(database, encryptionHandler, configuration)

    @AfterTest
    fun tearDown() {
        database.close()
    }

    private fun createTestToken(id: String = "test-id"): TokenData =
        TokenData(
            id = id,
            tokenType = "Bearer",
            expiresIn = 3600,
            accessToken = "access-$id",
            scope = "openid profile",
            refreshToken = "refresh-$id",
            idToken = null,
            deviceSecret = null,
            issuedTokenType = null,
            configuration = configuration
        )

    private fun createTestMetadata(
        id: String = "test-id",
        tags: Map<String, String> = emptyMap(),
    ): TokenMetadata =
        TokenMetadata(
            id = id,
            tags = tags,
            payloadData = null
        )

    @Test
    fun allIds_EmptyStorage_ReturnsEmpty() =
        runTest {
            assertTrue(storage.allIds().getOrThrow().isEmpty())
        }

    @Test
    fun add_StoresTokenAndMetadata() =
        runTest {
            val token = createTestToken()
            val metadata = createTestMetadata()
            storage.add(token, metadata).getOrThrow()

            assertEquals(listOf("test-id"), storage.allIds().getOrThrow())
            assertNotNull(storage.metadata("test-id").getOrThrow())
        }

    @Test
    fun getToken_ReturnsStoredToken() =
        runTest {
            val token = createTestToken()
            storage.add(token, createTestMetadata()).getOrThrow()

            val retrieved = storage.getToken("test-id").getOrThrow()
            assertEquals("access-test-id", retrieved.accessToken)
            assertEquals("Bearer", retrieved.tokenType)
            assertEquals(3600, retrieved.expiresIn)
            assertEquals("openid profile", retrieved.scope)
            assertEquals("refresh-test-id", retrieved.refreshToken)
        }

    @Test
    fun getToken_NonExistentId_ThrowsNoSuchElement() =
        runTest {
            assertTrue(storage.getToken("nonexistent").isFailure)
        }

    @Test
    fun remove_DeletesTokenAndMetadata() =
        runTest {
            storage.add(createTestToken(), createTestMetadata()).getOrThrow()
            storage.remove("test-id").getOrThrow()

            assertTrue(storage.allIds().getOrThrow().isEmpty())
            assertNull(storage.metadata("test-id").getOrThrow())
        }

    @Test
    fun replace_UpdatesExistingToken() =
        runTest {
            val original = createTestToken()
            storage.add(original, createTestMetadata()).getOrThrow()

            val updated =
                TokenData(
                    id = "test-id",
                    tokenType = "Bearer",
                    expiresIn = 7200,
                    accessToken = "new-access-token",
                    scope = "openid",
                    refreshToken = "new-refresh",
                    idToken = null,
                    deviceSecret = null,
                    issuedTokenType = null,
                    configuration = configuration
                )
            storage.replace(updated).getOrThrow()

            val retrieved = storage.getToken("test-id").getOrThrow()
            assertEquals("new-access-token", retrieved.accessToken)
            assertEquals(7200, retrieved.expiresIn)
            assertEquals("new-refresh", retrieved.refreshToken)
        }

    @Test
    fun replace_NonExistentId_ThrowsNoSuchElement() =
        runTest {
            assertTrue(storage.replace(createTestToken("nonexistent")).isFailure)
        }

    @Test
    fun setMetadata_UpdatesTags() =
        runTest {
            storage.add(createTestToken(), createTestMetadata()).getOrThrow()
            val updatedMetadata = createTestMetadata(tags = mapOf("key" to "value"))
            storage.setMetadata(updatedMetadata).getOrThrow()

            val metadata = storage.metadata("test-id").getOrThrow()
            assertNotNull(metadata)
            assertEquals("value", metadata.tags["key"])
        }

    @Test
    fun metadata_NonExistentId_ReturnsNull() =
        runTest {
            assertNull(storage.metadata("nonexistent").getOrThrow())
        }

    @Test
    fun multipleTokens_IndependentCrud() =
        runTest {
            storage.add(createTestToken("id1"), createTestMetadata("id1")).getOrThrow()
            storage.add(createTestToken("id2"), createTestMetadata("id2")).getOrThrow()

            assertEquals(2, storage.allIds().getOrThrow().size)

            storage.remove("id1").getOrThrow()
            assertEquals(listOf("id2"), storage.allIds().getOrThrow())

            val token2 = storage.getToken("id2").getOrThrow()
            assertEquals("access-id2", token2.accessToken)
        }

    @Test
    fun encryptionHandler_EncryptsAccessToken() =
        runTest {
            val xorHandler = XorEncryptionHandler()
            val encryptedStorage = RoomTokenStorage(database, xorHandler, configuration)

            val token = createTestToken()
            encryptedStorage.add(token, createTestMetadata()).getOrThrow()

            val retrieved = encryptedStorage.getToken("test-id").getOrThrow()
            assertEquals("access-test-id", retrieved.accessToken)
        }

    @Test
    fun remove_NonExistentId_DoesNothing() =
        runTest {
            storage.remove("nonexistent").getOrThrow()
            assertTrue(storage.allIds().getOrThrow().isEmpty())
        }

    private class PassthroughEncryptionHandler : TokenEncryptionHandler {
        override suspend fun encrypt(plaintext: ByteArray): EncryptionResult = EncryptionResult(ciphertext = plaintext)

        override suspend fun decrypt(
            ciphertext: ByteArray,
            encryptionExtras: Map<String, String>,
        ): ByteArray = ciphertext
    }

    private class XorEncryptionHandler : TokenEncryptionHandler {
        private val key: Byte = 0x42

        override suspend fun encrypt(plaintext: ByteArray): EncryptionResult = EncryptionResult(ciphertext = plaintext.map { (it.toInt() xor key.toInt()).toByte() }.toByteArray())

        override suspend fun decrypt(
            ciphertext: ByteArray,
            encryptionExtras: Map<String, String>,
        ): ByteArray = ciphertext.map { (it.toInt() xor key.toInt()).toByte() }.toByteArray()
    }
}
