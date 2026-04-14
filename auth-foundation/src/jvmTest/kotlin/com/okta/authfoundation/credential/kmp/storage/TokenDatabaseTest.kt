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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenDatabaseTest {
    private val database: TokenDatabase =
        Room
            .inMemoryDatabaseBuilder<TokenDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    private val dao = database.tokenDao()

    @AfterTest
    fun tearDown() {
        database.close()
    }

    private fun createEntity(
        id: String = "token-1",
        tags: Map<String, String> = emptyMap(),
        payloadData: JsonObject? = null,
    ): TokenEntity =
        TokenEntity(
            id = id,
            clientId = "client-id",
            issuerUrl = "https://test.okta.com",
            tokenType = "Bearer",
            expiresIn = 3600,
            accessToken = "access-token".encodeToByteArray(),
            scope = "openid",
            refreshToken = "refresh-token",
            idToken = null,
            deviceSecret = null,
            issuedTokenType = null,
            issuedAt = 1000000L,
            tags = tags,
            payloadData = payloadData,
            encryptionExtras = emptyMap()
        )

    @Test
    fun insertAndRetrieve_RoundTrips() =
        runTest {
            val entity = createEntity()
            dao.insertTokenEntity(entity)

            val retrieved = dao.getById("token-1")
            assertNotNull(retrieved)
            assertEquals("token-1", retrieved.id)
            assertEquals("client-id", retrieved.clientId)
            assertEquals("Bearer", retrieved.tokenType)
            assertEquals(3600, retrieved.expiresIn)
            assertTrue(entity.accessToken.contentEquals(retrieved.accessToken))
            assertEquals("openid", retrieved.scope)
            assertEquals("refresh-token", retrieved.refreshToken)
            assertEquals(1000000L, retrieved.issuedAt)
        }

    @Test
    fun allEntries_ReturnsAll() =
        runTest {
            dao.insertTokenEntity(createEntity("id1"))
            dao.insertTokenEntity(createEntity("id2"))

            val all = dao.allEntries()
            assertEquals(2, all.size)
        }

    @Test
    fun getById_NonExistent_ReturnsNull() =
        runTest {
            assertNull(dao.getById("nonexistent"))
        }

    @Test
    fun update_ModifiesEntity() =
        runTest {
            val entity = createEntity()
            dao.insertTokenEntity(entity)

            val updated = entity.copy(tokenType = "MAC", expiresIn = 7200)
            dao.updateTokenEntity(updated)

            val retrieved = dao.getById("token-1")
            assertNotNull(retrieved)
            assertEquals("MAC", retrieved.tokenType)
            assertEquals(7200, retrieved.expiresIn)
        }

    @Test
    fun delete_RemovesEntity() =
        runTest {
            val entity = createEntity()
            dao.insertTokenEntity(entity)
            dao.deleteTokenEntity(entity)

            assertNull(dao.getById("token-1"))
        }

    @Test
    fun tags_RoundTrips() =
        runTest {
            val tags = mapOf("env" to "production", "label" to "primary")
            dao.insertTokenEntity(createEntity(tags = tags))

            val retrieved = dao.getById("token-1")
            assertNotNull(retrieved)
            assertEquals(tags, retrieved.tags)
        }

    @Test
    fun payloadData_RoundTrips() =
        runTest {
            val payload = JsonObject(mapOf("sub" to JsonPrimitive("user123")))
            dao.insertTokenEntity(createEntity(payloadData = payload))

            val retrieved = dao.getById("token-1")
            assertNotNull(retrieved)
            assertEquals(payload, retrieved.payloadData)
        }

    @Test
    fun nullableFields_HandleNulls() =
        runTest {
            val entity =
                TokenEntity(
                    id = "null-test",
                    clientId = "client",
                    issuerUrl = "https://test.okta.com",
                    tokenType = "Bearer",
                    expiresIn = 3600,
                    accessToken = "token".encodeToByteArray(),
                    scope = null,
                    refreshToken = null,
                    idToken = null,
                    deviceSecret = null,
                    issuedTokenType = null,
                    issuedAt = 0L,
                    tags = emptyMap(),
                    payloadData = null,
                    encryptionExtras = emptyMap()
                )
            dao.insertTokenEntity(entity)

            val retrieved = dao.getById("null-test")
            assertNotNull(retrieved)
            assertNull(retrieved.scope)
            assertNull(retrieved.refreshToken)
            assertNull(retrieved.idToken)
            assertNull(retrieved.payloadData)
        }
}
