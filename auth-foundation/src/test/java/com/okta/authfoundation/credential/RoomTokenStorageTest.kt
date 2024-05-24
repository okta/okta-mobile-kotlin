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
package com.okta.authfoundation.credential

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.credential.storage.TokenDatabase
import com.okta.testhelpers.TestTokenEncryptionHandler
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class RoomTokenStorageTest {
    private val token = createToken()
    private val tokenMetadata = Token.Metadata(
        id = "id",
        tags = mapOf("key" to "value"),
        payloadData = buildJsonObject { put("claim", "claimValue") }
    )

    private lateinit var database: TokenDatabase
    private lateinit var roomTokenStorage: RoomTokenStorage

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TokenDatabase::class.java
        ).allowMainThreadQueries().build()
        roomTokenStorage = RoomTokenStorage(database, TestTokenEncryptionHandler())
    }

    @Test
    fun `empty storage returns empty for all IDs`() = runTest {
        assertThat(roomTokenStorage.allIds()).isEmpty()
    }

    @Test
    fun `get metadata for non-existent id returns null`() = runTest {
        assertThat(roomTokenStorage.metadata("non-existent-id")).isNull()
    }

    @Test
    fun `setMetadata for a token with non-existent id fails`() = runTest {
        assertFailsWith<NoSuchElementException> {
            roomTokenStorage.setMetadata(
                Token.Metadata("non-existent-id", emptyMap(), buildJsonObject { })
            )
        }
    }

    @Test
    fun `add token succeeds`() = runTest {
        roomTokenStorage.add(token, tokenMetadata)
        assertThat(roomTokenStorage.getToken(tokenMetadata.id)).isEqualTo(token)
    }

    @Test
    fun `add token fails if a token with the same id already exists`() = runTest {
        roomTokenStorage.add(token, tokenMetadata)
        val newToken = createToken(idToken = "different")
        assertFailsWith<SQLiteConstraintException> {
            roomTokenStorage.add(newToken, tokenMetadata)
        }
    }

    @Test
    fun `add token fails if token id and metadata id don't match`() = runTest {
        val exception = assertFailsWith<IllegalStateException> {
            roomTokenStorage.add(
                createToken(id = "tokenId"),
                tokenMetadata.copy(id = "differentId")
            )
        }
        assertThat(exception.message).isEqualTo("TokenStorage.add called with different token.id and metadata.id")
    }

    @Test
    fun `remove token succeeds`() = runTest {
        roomTokenStorage.add(token, tokenMetadata)
        assertThat(roomTokenStorage.allIds()).isEqualTo(listOf(tokenMetadata.id))
        roomTokenStorage.remove(tokenMetadata.id)
        assertThat(roomTokenStorage.allIds()).isEmpty()
    }

    @Test
    fun `remove non-existent id does nothing`() = runTest {
        roomTokenStorage.add(token, tokenMetadata)
        roomTokenStorage.remove("randomId")
        assertThat(roomTokenStorage.allIds()).isEqualTo(listOf(tokenMetadata.id))
    }

    @Test
    fun `replace token success`() = runTest {
        roomTokenStorage.add(token, tokenMetadata)
        assertThat(roomTokenStorage.getToken(tokenMetadata.id)).isEqualTo(token)
        val newToken = createToken(idToken = "newIdToken")
        roomTokenStorage.replace(newToken)
        assertThat(roomTokenStorage.getToken(tokenMetadata.id)).isEqualTo(newToken)
    }

    @Test
    fun `replace token with non-existent id`() = runTest {
        assertFailsWith<NoSuchElementException> {
            roomTokenStorage.replace(token)
        }
    }

    @Test
    fun `get token fails when no token with id exists`() = runTest {
        assertFailsWith<NoSuchElementException> {
            roomTokenStorage.getToken("non-existent-id")
        }
    }

    @Test
    fun `add and get biometric token`() = runTest {
        val biometricSecurity = Credential.Security.BiometricStrong(userAuthenticationTimeout = 20)
        roomTokenStorage.add(token, tokenMetadata, biometricSecurity)
        assertThat(roomTokenStorage.getToken(token.id)).isEqualTo(token)
        assertThat(database.tokenDao().getById(token.id)!!.security).isEqualTo(biometricSecurity)
    }

    @After
    fun tearDown() {
        database.close()
    }
}
