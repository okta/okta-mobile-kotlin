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

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricPrompt
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.credential.kmp.BiometricKeyInvalidatedException
import com.okta.authfoundation.credential.storage.TokenDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class BiometricKeyInvalidatedTest {
    private val token = createToken(id = "testTokenId")
    private val tokenMetadata =
        Token.Metadata(id = "testTokenId", tags = emptyMap(), payloadData = null)

    private lateinit var database: TokenDatabase
    private lateinit var roomTokenStorage: RoomTokenStorage

    @Before
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    TokenDatabase::class.java
                ).allowMainThreadQueries()
                .build()
        roomTokenStorage = RoomTokenStorage(database, BiometricFailingEncryptionHandler())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun getToken_ThrowsBiometricKeyInvalidatedException_WhenKeyInvalidated() =
        runTest {
            roomTokenStorage.add(token, tokenMetadata, Credential.Security.standard)

            val exception =
                assertFailsWith<BiometricKeyInvalidatedException> {
                    roomTokenStorage.getToken("testTokenId", null)
                }

            assertThat(exception.tokenId).isEqualTo("testTokenId")
            assertThat(exception.keyAlias).isNotEmpty()
        }

    @Test
    fun getToken_AutoDeletesTokenByDefault_WhenKeyInvalidated() =
        runTest {
            roomTokenStorage.add(token, tokenMetadata, Credential.Security.standard)
            assertThat(roomTokenStorage.allIds()).isEqualTo(listOf("testTokenId"))

            assertFailsWith<BiometricKeyInvalidatedException> {
                roomTokenStorage.getToken("testTokenId", null)
            }

            // Token must be auto-deleted (backward compat: deleteInvalidatedToken = true by default)
            assertThat(roomTokenStorage.allIds()).isEmpty()
        }
}

/**
 * A [TokenEncryptionHandler] that encrypts normally but simulates biometric key invalidation
 * by throwing [KeyPermanentlyInvalidatedException] on every [decrypt] call.
 */
private class BiometricFailingEncryptionHandler : TokenEncryptionHandler {
    override fun generateKey(security: Credential.Security) = Unit

    override suspend fun encrypt(
        token: Token,
        security: Credential.Security,
    ): TokenEncryptionHandler.EncryptionResult {
        val serialized = Json.encodeToString(Token.serializer(), token)
        return TokenEncryptionHandler.EncryptionResult(serialized.toByteArray(), emptyMap())
    }

    override suspend fun decrypt(
        encryptedToken: ByteArray,
        encryptionExtras: Map<String, String>,
        security: Credential.Security,
        promptInfo: BiometricPrompt.PromptInfo?,
    ): Token = throw KeyPermanentlyInvalidatedException()
}
