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
import com.okta.authfoundation.credential.kmp.TokenData
import kotlinx.coroutines.test.runTest
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateEncryptedTokenStorageTest {
    private val tempDir = File(System.getProperty("java.io.tmpdir"), "okta-encrypted-test-${System.nanoTime()}")
    private val dbPath = File(tempDir, "encrypted-tokens.db").absolutePath
    private val configuration = TestOAuth2ClientConfiguration.create()

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun createEncryptedTokenStorage_DefaultKey_CreatesStorage() =
        runTest {
            val storage = createEncryptedTokenStorage(configuration, dbPath)

            assertNotNull(storage)
            assertTrue(storage.allIds().getOrThrow().isEmpty())
        }

    @Test
    fun createEncryptedTokenStorage_DefaultKey_RoundTrips() =
        runTest {
            val storage = createEncryptedTokenStorage(configuration, dbPath)

            val token = createTestTokenData("enc-default-1")
            storage.add(token, createTestMetadata("enc-default-1")).getOrThrow()

            val retrieved = storage.getToken("enc-default-1").getOrThrow()
            assertEquals("access-enc-default-1", retrieved.accessToken)
        }

    @Test
    fun createEncryptedTokenStorage_CustomKeyProvider_RoundTrips() =
        runTest {
            val customKey = generateTestKey()
            val storage = createEncryptedTokenStorage(configuration, dbPath) { customKey }

            val token = createTestTokenData("enc-custom-1")
            storage.add(token, createTestMetadata("enc-custom-1")).getOrThrow()

            val retrieved = storage.getToken("enc-custom-1").getOrThrow()
            assertEquals("access-enc-custom-1", retrieved.accessToken)
        }

    @Test
    fun createEncryptedTokenStorage_CustomKeyProvider_DataEncryptedAtRest() =
        runTest {
            val customKey = generateTestKey()
            val storage = createEncryptedTokenStorage(configuration, dbPath) { customKey }

            val token = createTestTokenData("enc-verify-1", accessToken = "secret-value")
            storage.add(token, createTestMetadata("enc-verify-1")).getOrThrow()

            // Read raw database file — the plaintext access token should not appear
            val dbFile = File(dbPath)
            assertTrue(dbFile.exists())
            val rawBytes = dbFile.readBytes()
            val rawContent = rawBytes.toString(Charsets.UTF_8)
            assertTrue(
                !rawContent.contains("secret-value"),
                "Plaintext access token should not appear in the database file"
            )
        }

    @Test
    fun createEncryptedTokenStorage_MultipleTokens_AllRetrievable() =
        runTest {
            val storage = createEncryptedTokenStorage(configuration, dbPath)

            for (i in 1..3) {
                val token = createTestTokenData("enc-multi-$i")
                storage.add(token, createTestMetadata("enc-multi-$i")).getOrThrow()
            }

            val ids = storage.allIds().getOrThrow()
            assertEquals(3, ids.size)

            for (i in 1..3) {
                val retrieved = storage.getToken("enc-multi-$i").getOrThrow()
                assertEquals("access-enc-multi-$i", retrieved.accessToken)
            }
        }

    private fun createTestTokenData(
        id: String,
        accessToken: String = "access-$id",
    ): TokenData =
        TokenData(
            id = id,
            tokenType = "Bearer",
            expiresIn = 3600,
            accessToken = accessToken,
            scope = "openid",
            refreshToken = null,
            idToken = null,
            deviceSecret = null,
            issuedTokenType = null,
            configuration = configuration
        )

    private fun createTestMetadata(id: String): TokenMetadata = TokenMetadata(id = id, tags = emptyMap(), payloadData = null)

    private fun generateTestKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        return keyGen.generateKey()
    }
}
