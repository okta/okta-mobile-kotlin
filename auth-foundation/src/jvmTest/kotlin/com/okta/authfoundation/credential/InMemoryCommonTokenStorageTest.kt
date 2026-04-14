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

import com.okta.authfoundation.credential.kmp.TokenData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryCommonTokenStorageTest {
    private val storage = InMemoryTokenStorage()

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
            configuration = TestOAuth2ClientConfiguration.create()
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
            assertTrue(storage.allIds().isEmpty())
        }

    @Test
    fun add_StoresTokenAndMetadata() =
        runTest {
            val token = createTestToken()
            val metadata = createTestMetadata()
            storage.add(token, metadata)

            assertEquals(listOf("test-id"), storage.allIds())
            assertNotNull(storage.metadata("test-id"))
        }

    @Test
    fun getToken_ReturnsStoredToken() =
        runTest {
            val token = createTestToken()
            storage.add(token, createTestMetadata())

            val retrieved = storage.getToken("test-id")
            assertEquals("access-test-id", retrieved.accessToken)
            assertEquals("Bearer", retrieved.tokenType)
        }

    @Test
    fun getToken_NonExistentId_ThrowsNoSuchElement() =
        runTest {
            assertFailsWith<NoSuchElementException> {
                storage.getToken("nonexistent")
            }
        }

    @Test
    fun remove_DeletesTokenAndMetadata() =
        runTest {
            storage.add(createTestToken(), createTestMetadata())
            storage.remove("test-id")

            assertTrue(storage.allIds().isEmpty())
            assertNull(storage.metadata("test-id"))
        }

    @Test
    fun replace_UpdatesExistingToken() =
        runTest {
            storage.add(createTestToken(), createTestMetadata())
            val updated = createTestToken().copy(id = "test-id")
            storage.replace(updated)

            val retrieved = storage.getToken("test-id")
            assertEquals("access-test-id", retrieved.accessToken)
        }

    @Test
    fun replace_NonExistentId_ThrowsNoSuchElement() =
        runTest {
            assertFailsWith<NoSuchElementException> {
                storage.replace(createTestToken("nonexistent"))
            }
        }

    @Test
    fun setMetadata_UpdatesTags() =
        runTest {
            storage.add(createTestToken(), createTestMetadata())
            val updatedMetadata = createTestMetadata(tags = mapOf("key" to "value"))
            storage.setMetadata(updatedMetadata)

            val metadata = storage.metadata("test-id")
            assertNotNull(metadata)
            assertEquals("value", metadata.tags["key"])
        }

    @Test
    fun multipleTokens_IndependentCrud() =
        runTest {
            storage.add(createTestToken("id1"), createTestMetadata("id1"))
            storage.add(createTestToken("id2"), createTestMetadata("id2"))

            assertEquals(2, storage.allIds().size)

            storage.remove("id1")
            assertEquals(listOf("id2"), storage.allIds())

            val token2 = storage.getToken("id2")
            assertEquals("access-id2", token2.accessToken)
        }
}
