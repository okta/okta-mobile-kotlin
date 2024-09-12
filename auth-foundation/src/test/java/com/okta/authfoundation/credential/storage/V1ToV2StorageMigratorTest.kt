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

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.AuthFoundation
import com.okta.authfoundation.client.ApplicationContextHolder
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.credential.DefaultCredentialIdDataStore
import com.okta.authfoundation.credential.LegacyTokenStorage
import com.okta.authfoundation.credential.RoomTokenStorage
import com.okta.authfoundation.credential.SharedPreferencesTokenStorage
import com.okta.authfoundation.credential.createToken
import com.okta.authfoundation.credential.storage.migration.V1ToV2StorageMigrator
import com.okta.authfoundation.credential.storage.migration.V1ToV2StorageMigrator.Companion.LEGACY_CREDENTIAL_NAME_TAG_KEY
import com.okta.authfoundation.credential.storage.migration.V1ToV2StorageMigrator.Companion.LEGACY_DEFAULT_CREDENTIAL_NAME_TAG_VALUE
import com.okta.testhelpers.MockAesEncryptionHandler
import com.okta.testhelpers.TestTokenEncryptionHandler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V1ToV2StorageMigratorTest {
    private lateinit var context: Context
    private lateinit var database: TokenDatabase
    private lateinit var roomTokenStorage: RoomTokenStorage
    private lateinit var legacySharedPreferences: SharedPreferences
    private lateinit var defaultCredentialIdDataStore: DefaultCredentialIdDataStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TokenDatabase::class.java
        ).allowMainThreadQueries().build()
        roomTokenStorage = RoomTokenStorage(database, TestTokenEncryptionHandler())
        legacySharedPreferences = context.getSharedPreferences("testPrefs", Context.MODE_PRIVATE)

        mockkObject(ApplicationContextHolder)
        every { ApplicationContextHolder.appContext } returns context
        mockkObject(SharedPreferencesTokenStorage)
        every {
            SharedPreferencesTokenStorage.createSharedPreferences(
                any(),
                any()
            )
        } answers { legacySharedPreferences }
        defaultCredentialIdDataStore = DefaultCredentialIdDataStore(MockAesEncryptionHandler.getInstance())
        mockkObject(DefaultCredentialIdDataStore)
        every { DefaultCredentialIdDataStore.instance } returns defaultCredentialIdDataStore
        mockkObject(OidcConfiguration)
        every { OidcConfiguration.default } returns OidcConfiguration("clientId", "defaultScope", "issuer")
        mockkObject(V1ToV2StorageMigrator)
        every { V1ToV2StorageMigrator.legacyKeyGenParameterSpec } returns mockk()
        mockkObject(AuthFoundation)
        coEvery { AuthFoundation.initializeStorage() } just runs
    }

    @After
    fun tearDown() {
        database.close()
        unmockkAll()
    }

    @Test
    fun `migration from legacy storage`() = runTest {
        val legacyTokenStorage = SharedPreferencesTokenStorage(
            OidcConfiguration.defaultJson(),
            this.coroutineContext,
            mockk(relaxed = true),
            context,
            mockk()
        )
        every { V1ToV2StorageMigrator.legacyStorage } returns legacyTokenStorage
        legacyTokenStorage.add("testId")
        legacyTokenStorage.replace(
            LegacyTokenStorage.Entry("testId", createToken(), tags = mapOf("key" to "value"))
        )
        val migrator = V1ToV2StorageMigrator(roomTokenStorage)
        assertThat(migrator.isMigrationNeeded()).isTrue()
        migrator.migrateIfNeeded()
        assertThat(roomTokenStorage.allIds()).containsExactly("testId")
        assertThat(migrator.isMigrationNeeded()).isFalse()
    }

    @Test
    fun `migration from legacy storage with default token`() = runTest {
        val legacyTokenStorage = SharedPreferencesTokenStorage(
            OidcConfiguration.defaultJson(),
            this.coroutineContext,
            mockk(relaxed = true),
            context,
            mockk()
        )
        every { V1ToV2StorageMigrator.legacyStorage } returns legacyTokenStorage
        legacyTokenStorage.add("defaultTokenId")
        legacyTokenStorage.replace(
            LegacyTokenStorage.Entry(
                "defaultTokenId",
                createToken(),
                tags = mapOf(LEGACY_CREDENTIAL_NAME_TAG_KEY to LEGACY_DEFAULT_CREDENTIAL_NAME_TAG_VALUE)
            )
        )
        legacyTokenStorage.add("testId")
        legacyTokenStorage.replace(
            LegacyTokenStorage.Entry("testId", createToken(), tags = mapOf("key" to "value"))
        )
        val migrator = V1ToV2StorageMigrator(roomTokenStorage)
        assertThat(migrator.isMigrationNeeded()).isTrue()
        migrator.migrateIfNeeded()
        assertThat(roomTokenStorage.allIds()).containsExactlyElementsIn(listOf("testId", "defaultTokenId"))
        assertThat(DefaultCredentialIdDataStore.instance.getDefaultCredentialId()).isEqualTo("defaultTokenId")
        assertThat(migrator.isMigrationNeeded()).isFalse()
    }

    @Test
    fun `migration only happens once`() = runTest {
        val legacyTokenStorage = SharedPreferencesTokenStorage(
            OidcConfiguration.defaultJson(),
            this.coroutineContext,
            mockk(relaxed = true),
            context,
            mockk()
        )
        every { V1ToV2StorageMigrator.legacyStorage } returns legacyTokenStorage
        legacyTokenStorage.add("testId")
        legacyTokenStorage.replace(
            LegacyTokenStorage.Entry("testId", createToken(), tags = mapOf("key" to "value"))
        )
        val migrator = V1ToV2StorageMigrator(roomTokenStorage)
        migrator.migrateIfNeeded()
        assertThat(roomTokenStorage.allIds()).containsExactly("testId")

        legacyTokenStorage.add("testId2")
        legacyTokenStorage.replace(
            LegacyTokenStorage.Entry("testId2", createToken(), tags = mapOf("key" to "value"))
        )
        migrator.migrateIfNeeded()
        assertThat(roomTokenStorage.allIds()).containsExactly("testId") // Same as before adding testId2
    }
}
