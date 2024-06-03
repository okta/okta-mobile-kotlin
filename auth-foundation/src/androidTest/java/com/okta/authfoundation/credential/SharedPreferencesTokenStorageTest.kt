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

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.credential.events.TokenStorageAccessErrorEvent
import com.okta.authfoundation.events.Event
import com.okta.authfoundation.events.EventCoordinator
import com.okta.authfoundation.events.EventHandler
import com.okta.testhelpers.RecordingEventHandler
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStoreException
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesTokenStorageTest {
    private lateinit var subject: SharedPreferencesTokenStorage
    private lateinit var eventHandler: RecordingEventHandler

    @Before fun setup() {
        eventHandler = RecordingEventHandler()
        val eventCoordinator = EventCoordinator(eventHandler)
        subject = SharedPreferencesTokenStorage(
            json = Json,
            dispatcher = EmptyCoroutineContext,
            eventCoordinator = eventCoordinator,
            context = ApplicationProvider.getApplicationContext(),
            keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC,
        )
        cleanup() // Cleanup before and after!
    }

    @After fun cleanup() {
        runBlocking {
            try {
                for (entry in subject.entries()) {
                    subject.remove(entry.identifier)
                }
            } catch (ignored: Exception) {
            }
        }
    }

    @Test fun testEntriesWithNoExistingEntries(): Unit = runBlocking {
        assertThat(subject.entries()).isEmpty()
    }

    @Test fun testAdd(): Unit = runBlocking {
        subject.add("one")
        assertThat(subject.entries()).hasSize(1)
    }

    @Test fun testRemove(): Unit = runBlocking {
        subject.add("one")
        assertThat(subject.entries()).hasSize(1)
        subject.remove("one")
        assertThat(subject.entries()).hasSize(0)
    }

    @Test fun testRemoveNonExistingEntries(): Unit = runBlocking {
        assertThat(subject.entries()).hasSize(0)
        subject.remove("one")
        assertThat(subject.entries()).hasSize(0)
    }

    @Test fun testReplace(): Unit = runBlocking {
        subject.add("one")
        assertThat(subject.entries()).hasSize(1)
        subject.replace(LegacyTokenStorage.Entry("one", null, mapOf("foo" to "bar")))
        val entry = subject.entries().first()
        assertThat(entry.identifier).isEqualTo("one")
        assertThat(entry.token).isNull()
        assertThat(entry.tags).containsEntry("foo", "bar")
    }

    @Test fun testReplaceNonExistingEntries(): Unit = runBlocking {
        subject.add("one")
        assertThat(subject.entries()).hasSize(1)
        subject.replace(LegacyTokenStorage.Entry("two", null, mapOf("foo" to "bar")))
        val entry = subject.entries().first()
        assertThat(entry.identifier).isEqualTo("one")
        assertThat(entry.token).isNull()
        assertThat(entry.tags).doesNotContainEntry("foo", "bar")
    }

    @Test fun testTokenStorageAndRestoration(): Unit = runBlocking {
        subject.add("one")
        assertThat(subject.entries()).hasSize(1)
        val token = Token(
            id = "id",
            tokenType = "Bearer",
            expiresIn = 600,
            accessToken = "anExampleButInvalidAccessToken",
            scope = "openid email offline_access etc",
            refreshToken = "anExampleButInvalidRefreshToken",
            idToken = null,
            deviceSecret = null,
            issuedTokenType = null,
            oidcConfiguration = OidcConfiguration("clientId", "defaultScope", "discoveryUrl")
        )
        subject.replace(LegacyTokenStorage.Entry("one", token, mapOf("foo" to "bar")))
        val entry = subject.entries().first()
        assertThat(entry.identifier).isEqualTo("one")
        assertThat(entry.token).isEqualTo(token)
        assertThat(entry.tags).containsEntry("foo", "bar")
    }

    @Test fun testInvalidKeyClearsSharedPreferencesAndIsInitializedWithoutError(): Unit = runBlocking {
        subject.add("one")
        assertThat(subject.entries()).hasSize(1)
        assertThat(eventHandler.size).isEqualTo(0)

        corruptEncryptedSharedPreferences()

        val eventCoordinator = EventCoordinator(eventHandler)
        subject = SharedPreferencesTokenStorage(
            json = Json,
            dispatcher = EmptyCoroutineContext,
            eventCoordinator = eventCoordinator,
            context = ApplicationProvider.getApplicationContext(),
            keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC,
        )
        assertThat(eventHandler.size).isEqualTo(0) // Access should be lazy!
        assertThat(subject.entries()).hasSize(0)
        assertThat(eventHandler.size).isEqualTo(1)
        val event = eventHandler[0]
        assertThat(event).isInstanceOf(TokenStorageAccessErrorEvent::class.java)

        // It should be functional after it's been reset!
        subject.add("another")
        assertThat(subject.entries()).hasSize(1)
    }

    @Test fun testCorruptEncryptedSharedPreferencesWithShouldClearStorageAndTryAgainSetToFalseShouldThrow() {
        corruptEncryptedSharedPreferences()
        val eventCoordinator = EventCoordinator(object : EventHandler {
            override fun onEvent(event: Event) {
                val errorEvent = event as TokenStorageAccessErrorEvent
                errorEvent.shouldClearStorageAndTryAgain = false
            }
        })
        subject = SharedPreferencesTokenStorage(
            json = Json,
            dispatcher = EmptyCoroutineContext,
            eventCoordinator = eventCoordinator,
            context = ApplicationProvider.getApplicationContext(),
            keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC,
        )
        assertFailsWith<Exception> {
            runBlocking {
                subject.add("another")
            }
        }
    }

    @Test fun testKeyGenParameterSpecIsUsed() {
        val spec = KeyGenParameterSpec.Builder(
            "com_okta_authfoundation_storage",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(10, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setKeySize(256)
            .build()
        val eventCoordinator = EventCoordinator(eventHandler)
        subject = SharedPreferencesTokenStorage(
            json = Json,
            dispatcher = EmptyCoroutineContext,
            eventCoordinator = eventCoordinator,
            context = ApplicationProvider.getApplicationContext(),
            keyGenParameterSpec = spec,
        )
        val exception = assertFailsWith<Exception> {
            runBlocking {
                subject.add("element")
            }
        }
        if (exception is InvalidAlgorithmParameterException) {
            // This is the exception we expect if no biometrics are enrolled.
            assertThat(exception.cause).hasMessageThat().isEqualTo("At least one biometric must be enrolled to create keys requiring user authentication for every use")
        } else {
            // This is the exception we expect if biometrics are enrolled.
            assertThat(exception).isInstanceOf(KeyStoreException::class.java)
            assertThat(exception).hasMessageThat().isEqualTo("the master key android-keystore://com_okta_authfoundation_storage exists but is unusable")
            assertThat(exception.cause).isInstanceOf(UserNotAuthenticatedException::class.java)
            assertThat(exception.cause).hasMessageThat().isEqualTo("User not authenticated")
        }
    }

    private fun corruptEncryptedSharedPreferences() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val sharedPreferences = context.getSharedPreferences(SharedPreferencesTokenStorage.FILE_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().commit()

        // Create with a different key!
        val builder = KeyGenParameterSpec.Builder(
            "TestingOnly!",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        val masterKeyAlias = MasterKeys.getOrCreate(builder.build())

        EncryptedSharedPreferences.create(
            SharedPreferencesTokenStorage.FILE_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
