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

import com.okta.authfoundation.credential.TestOAuth2ClientConfiguration
import com.okta.authfoundation.credential.TokenMetadata
import com.okta.authfoundation.credential.kmp.EncryptionResult
import com.okta.authfoundation.credential.kmp.TokenData
import com.okta.authfoundation.credential.kmp.TokenEncryptionHandler
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JvmTokenDatabaseFactoryTest {
    private val tempDir = File(System.getProperty("java.io.tmpdir"), "okta-test-${System.nanoTime()}")
    private val dbPath = File(tempDir, "test-tokens.db").absolutePath
    private var database: TokenDatabase? = null

    @AfterTest
    fun tearDown() {
        database?.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun createJvmTokenDatabase_CreatesFileBasedDatabase() =
        runTest {
            database = createTokenDatabase(dbPath)

            assertNotNull(database)
            // Verify database is operational by querying
            assertTrue(database!!.tokenDao().allEntries().isEmpty())
        }

    @Test
    fun createJvmTokenDatabase_CreatesParentDirectories() {
        val nestedPath = File(tempDir, "nested/dirs/tokens.db").absolutePath
        database = createTokenDatabase(nestedPath)

        assertTrue(File(nestedPath).parentFile!!.exists())
    }

    @Test
    fun createJvmTokenDatabase_StorageRoundTrips() =
        runTest {
            val configuration = TestOAuth2ClientConfiguration.create()
            database = createTokenDatabase(dbPath)
            val storage = RoomTokenStorage(database!!, PassthroughEncryptionHandler(), configuration)

            val token =
                TokenData(
                    id = "file-test-id",
                    tokenType = "Bearer",
                    expiresIn = 3600,
                    accessToken = "file-access-token",
                    scope = "openid",
                    refreshToken = null,
                    idToken = null,
                    deviceSecret = null,
                    issuedTokenType = null,
                    configuration = configuration
                )
            storage.add(token, TokenMetadata(id = "file-test-id", tags = emptyMap(), payloadData = null)).getOrThrow()

            val retrieved = storage.getToken("file-test-id").getOrThrow()
            assertEquals("file-access-token", retrieved.accessToken)
        }

    @Test
    fun createJvmTokenDatabase_DataPersistsAcrossInstances() =
        runTest {
            val configuration = TestOAuth2ClientConfiguration.create()
            val db1 = createTokenDatabase(dbPath)
            val storage1 = RoomTokenStorage(db1, PassthroughEncryptionHandler(), configuration)

            val token =
                TokenData(
                    id = "persist-id",
                    tokenType = "Bearer",
                    expiresIn = 3600,
                    accessToken = "persist-access",
                    scope = null,
                    refreshToken = null,
                    idToken = null,
                    deviceSecret = null,
                    issuedTokenType = null,
                    configuration = configuration
                )
            storage1.add(token, TokenMetadata(id = "persist-id", tags = mapOf("env" to "test"), payloadData = null)).getOrThrow()
            db1.close()

            val db2 = createTokenDatabase(dbPath)
            database = db2
            val storage2 = RoomTokenStorage(db2, PassthroughEncryptionHandler(), configuration)

            val ids = storage2.allIds().getOrThrow()
            assertEquals(listOf("persist-id"), ids)

            val retrieved = storage2.getToken("persist-id").getOrThrow()
            assertEquals("persist-access", retrieved.accessToken)

            val metadata = storage2.metadata("persist-id").getOrThrow()
            assertNotNull(metadata)
            assertEquals("test", metadata.tags["env"])
        }

    private class PassthroughEncryptionHandler : TokenEncryptionHandler {
        override suspend fun encrypt(plaintext: ByteArray): EncryptionResult = EncryptionResult(ciphertext = plaintext)

        override suspend fun decrypt(
            ciphertext: ByteArray,
            encryptionExtras: Map<String, String>,
        ): ByteArray = ciphertext
    }
}
