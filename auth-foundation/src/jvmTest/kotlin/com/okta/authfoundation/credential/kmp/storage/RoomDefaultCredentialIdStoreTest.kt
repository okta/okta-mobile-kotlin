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
import kotlin.test.assertNull

class RoomDefaultCredentialIdStoreTest {
    private val database: TokenDatabase =
        Room
            .inMemoryDatabaseBuilder<TokenDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    private val store = RoomDefaultCredentialIdStore(database)

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun getDefaultCredentialId_InitiallyNull() =
        runTest {
            assertNull(store.getDefaultCredentialId().getOrThrow())
        }

    @Test
    fun setAndGetDefaultCredentialId_RoundTrips() =
        runTest {
            store.setDefaultCredentialId("cred-123").getOrThrow()
            assertEquals("cred-123", store.getDefaultCredentialId().getOrThrow())
        }

    @Test
    fun setDefaultCredentialId_OverwritesPrevious() =
        runTest {
            store.setDefaultCredentialId("cred-1").getOrThrow()
            store.setDefaultCredentialId("cred-2").getOrThrow()
            assertEquals("cred-2", store.getDefaultCredentialId().getOrThrow())
        }

    @Test
    fun clearDefaultCredentialId_RemovesStoredId() =
        runTest {
            store.setDefaultCredentialId("cred-123").getOrThrow()
            store.clearDefaultCredentialId().getOrThrow()
            assertNull(store.getDefaultCredentialId().getOrThrow())
        }

    @Test
    fun clearDefaultCredentialId_WhenEmpty_DoesNothing() =
        runTest {
            store.clearDefaultCredentialId().getOrThrow()
            assertNull(store.getDefaultCredentialId().getOrThrow())
        }

    @Test
    fun sharesDatabase_WithTokenStorage() =
        runTest {
            val configuration = TestOAuth2ClientConfiguration.create()
            val tokenStorage = RoomTokenStorage(database, PassthroughEncryptionHandler(), configuration)

            val token =
                TokenData(
                    id = "shared-db-token",
                    tokenType = "Bearer",
                    expiresIn = 3600,
                    accessToken = "access",
                    scope = null,
                    refreshToken = null,
                    idToken = null,
                    deviceSecret = null,
                    issuedTokenType = null,
                    configuration = configuration
                )
            tokenStorage.add(token, TokenMetadata(id = "shared-db-token", tags = emptyMap(), payloadData = null)).getOrThrow()
            store.setDefaultCredentialId("shared-db-token").getOrThrow()

            assertEquals("shared-db-token", store.getDefaultCredentialId().getOrThrow())
            assertEquals(listOf("shared-db-token"), tokenStorage.allIds().getOrThrow())
        }

    private class PassthroughEncryptionHandler : TokenEncryptionHandler {
        override suspend fun encrypt(plaintext: ByteArray): EncryptionResult = EncryptionResult(ciphertext = plaintext)

        override suspend fun decrypt(
            ciphertext: ByteArray,
            encryptionExtras: Map<String, String>,
        ): ByteArray = ciphertext
    }
}
