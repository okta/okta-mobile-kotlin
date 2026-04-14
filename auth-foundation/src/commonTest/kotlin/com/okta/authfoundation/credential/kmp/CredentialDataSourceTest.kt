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
import com.okta.authfoundation.credential.FakeCommonTokenStorage
import com.okta.authfoundation.credential.TestConfiguration
import com.okta.authfoundation.credential.TokenMetadata
import com.okta.authfoundation.credential.createTestMetadata
import com.okta.authfoundation.credential.createTestToken
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(InternalAuthFoundationApi::class)
class CredentialDataSourceTest {
    private val storage = FakeCommonTokenStorage()
    private val dataSource = CredentialDataSource(storage)

    @Test
    fun allIds_EmptyStorage_ReturnsEmpty() =
        runTest {
            assertTrue(dataSource.allIds().isEmpty())
        }

    @Test
    fun createToken_StoresAndReturnsToken() =
        runTest {
            val token = createTestToken(id = "tok1")
            val result = dataSource.createToken(token)

            assertEquals("tok1", result.id)
            assertEquals(listOf("tok1"), dataSource.allIds())
        }

    @Test
    fun createToken_StoresMetadataWithTags() =
        runTest {
            val token = createTestToken(id = "tok3")
            dataSource.createToken(token, tags = mapOf("env" to "prod"))

            val metadata = dataSource.metadata("tok3")
            assertNotNull(metadata)
            assertEquals("prod", metadata.tags["env"])
        }

    @Test
    fun getToken_ReturnsCachedToken() =
        runTest {
            val token = createTestToken(id = "tok4")
            dataSource.createToken(token)

            val retrieved = dataSource.getToken("tok4")
            assertNotNull(retrieved)
            assertEquals("tok4", retrieved.id)
        }

    @Test
    fun getToken_NonexistentId_ReturnsNull() =
        runTest {
            assertNull(dataSource.getToken("nonexistent"))
        }

    @Test
    fun getToken_FetchesFromStorageWhenNotCached() =
        runTest {
            val token = createTestToken(id = "tok5")
            storage.add(token, createTestMetadata("tok5"))

            val retrieved = dataSource.getToken("tok5")
            assertNotNull(retrieved)
            assertEquals("tok5", retrieved.id)
        }

    @Test
    fun remove_DeletesFromStorageAndCache() =
        runTest {
            val token = createTestToken(id = "tok6")
            dataSource.createToken(token)
            dataSource.remove("tok6")

            assertTrue(dataSource.allIds().isEmpty())
            assertNull(dataSource.getToken("tok6"))
        }

    @Test
    fun replaceToken_UpdatesCacheAndStorage() =
        runTest {
            val original = createTestToken(id = "tok7", accessToken = "old-at")
            dataSource.createToken(original)

            val updated = original.copy(id = "tok7")
            dataSource.replaceToken(updated)

            val retrieved = dataSource.getToken("tok7")
            assertNotNull(retrieved)
        }

    @Test
    fun replaceToken_NonexistentId_ThrowsIllegalState() =
        runTest {
            val token = createTestToken(id = "nonexistent")
            assertFailsWith<IllegalStateException> {
                dataSource.replaceToken(token)
            }
        }

    @Test
    fun setMetadata_UpdatesStoredMetadata() =
        runTest {
            val token = createTestToken(id = "tok9")
            dataSource.createToken(token, tags = mapOf("old" to "value"))

            dataSource.setMetadata(TokenMetadata(id = "tok9", tags = mapOf("new" to "data"), payloadData = null))

            val metadata = dataSource.metadata("tok9")
            assertNotNull(metadata)
            assertEquals("data", metadata.tags["new"])
            assertNull(metadata.tags["old"])
        }

    @Test
    fun findTokens_MatchesByTag() =
        runTest {
            dataSource.createToken(createTestToken(id = "a"), tags = mapOf("type" to "primary"))
            dataSource.createToken(createTestToken(id = "b"), tags = mapOf("type" to "secondary"))

            val found = dataSource.findTokens { it.tags["type"] == "primary" }
            assertEquals(1, found.size)
            assertEquals("a", found[0].id)
        }

    @Test
    fun findTokens_NoMatches_ReturnsEmpty() =
        runTest {
            dataSource.createToken(createTestToken(id = "c"), tags = mapOf("type" to "other"))

            val found = dataSource.findTokens { it.tags["type"] == "missing" }
            assertTrue(found.isEmpty())
        }

    @Test
    fun multipleTokens_IndependentLifecycles() =
        runTest {
            dataSource.createToken(createTestToken(id = "x"))
            dataSource.createToken(createTestToken(id = "y"))
            dataSource.createToken(createTestToken(id = "z"))

            assertEquals(3, dataSource.allIds().size)

            dataSource.remove("y")

            assertEquals(2, dataSource.allIds().size)
            assertNotNull(dataSource.getToken("x"))
            assertNull(dataSource.getToken("y"))
            assertNotNull(dataSource.getToken("z"))
        }
}
