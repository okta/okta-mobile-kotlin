/*
 * Copyright 2024-Present Okta, Inc.
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
package com.okta.authfoundation.credential.storage

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class TokenDatabaseTest {
    private val baseTokenEntity = TokenEntity(
        "id",
        "encryptedToken".toByteArray(),
        tags = mapOf("key" to "value"),
        payloadData = buildJsonObject {
            put("claim", "claimValue")
        },
        "keyAlias",
        tokenEncryptionType = TokenEntity.EncryptionType.DEFAULT,
        biometricTimeout = null,
        encryptionExtras = mapOf("encryptionExtra" to "value")
    )

    private lateinit var database: TokenDatabase
    private lateinit var tokenDao: TokenDao

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TokenDatabase::class.java
        ).allowMainThreadQueries().build()
        tokenDao = database.tokenDao()
    }

    @Test fun `db returns empty list of tokens initially`() = runTest {
        assertThat(tokenDao.allEntries()).isEmpty()
    }

    @Test fun `insertTokenEntity successfully inserts new token into db`() = runTest {
        tokenDao.insertTokenEntity(baseTokenEntity)
        val result = database.tokenDao().allEntries()
        assertThat(result).isEqualTo(listOf(baseTokenEntity))
    }

    @Test fun `insert fails if another token with same id is already in the db`() = runTest {
        tokenDao.insertTokenEntity(baseTokenEntity)
        val differentTokenWithSameId = baseTokenEntity.copy(encryptedToken = "anotherValue".toByteArray())
        assertFailsWith<SQLiteConstraintException> {
            tokenDao.insertTokenEntity(differentTokenWithSameId)
        }
    }

    @Test fun `update token`() = runTest {
        tokenDao.insertTokenEntity(baseTokenEntity)
        val updatedToken = baseTokenEntity.copy(encryptedToken = "updatedValue".toByteArray())
        tokenDao.updateTokenEntity(updatedToken)

        val result = tokenDao.allEntries()
        assertThat(result).isEqualTo(listOf(updatedToken))
    }

    @Test fun `updating a token with non-existing id in db does nothing`() = runTest {
        tokenDao.insertTokenEntity(baseTokenEntity)
        val updatedToken = baseTokenEntity.copy(id = "differentId")
        tokenDao.updateTokenEntity(updatedToken)

        val result = tokenDao.allEntries()
        assertThat(result).isEqualTo(listOf(baseTokenEntity))
    }

    @Test fun `delete token`() = runTest {
        tokenDao.insertTokenEntity(baseTokenEntity)
        tokenDao.deleteTokenEntity(baseTokenEntity)

        val result = tokenDao.allEntries()
        assertThat(result).isEqualTo(emptyList<TokenEntity>())
    }

    @Test fun `delete token with non-existent id nothing`() = runTest {
        tokenDao.insertTokenEntity(baseTokenEntity)
        val tokenWithDifferentId = baseTokenEntity.copy(id = "differentId")
        tokenDao.deleteTokenEntity(tokenWithDifferentId)

        val result = tokenDao.allEntries()
        assertThat(result).isEqualTo(listOf(baseTokenEntity))
    }

    @Test fun `delete token with different fields but same id deletes token with matching id`() = runTest {
        tokenDao.insertTokenEntity(baseTokenEntity)
        val tokenWithDifferentNonIdFields = baseTokenEntity.copy(
            encryptedToken = "differentToken".toByteArray(), tags = emptyMap()
        )
        tokenDao.deleteTokenEntity(tokenWithDifferentNonIdFields)

        val result = tokenDao.allEntries()
        assertThat(result).isEqualTo(emptyList<TokenEntity>())
    }

    @After fun tearDown() {
        database.close()
    }
}
