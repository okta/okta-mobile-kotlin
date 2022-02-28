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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesTokenStorageTest {
    private lateinit var subject: SharedPreferencesTokenStorage

    @Before fun setup() {
        subject = SharedPreferencesTokenStorage(
            json = Json,
            dispatcher = Dispatchers.Unconfined,
            context = ApplicationProvider.getApplicationContext(),
        )
        cleanup() // Cleanup before and after!
    }

    @After fun cleanup() {
        runBlocking {
            for (entry in subject.entries()) {
                subject.remove(entry.identifier)
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

    @Test fun testReplace(): Unit = runBlocking {
        subject.add("one")
        assertThat(subject.entries()).hasSize(1)
        subject.replace(TokenStorage.Entry("one", null, mapOf("foo" to "bar")))
        val entry = subject.entries().first()
        assertThat(entry.identifier).isEqualTo("one")
        assertThat(entry.token).isNull()
        assertThat(entry.metadata).containsEntry("foo", "bar")
    }

    @Test fun testTokenStorageAndRestoration(): Unit = runBlocking {
        subject.add("one")
        assertThat(subject.entries()).hasSize(1)
        val token = Token(
            tokenType = "Bearer",
            expiresIn = 600,
            accessToken = "anExampleButInvalidAccessToken",
            scope = "openid email offline_access etc",
            refreshToken = "anExampleButInvalidRefreshToken"
        )
        subject.replace(TokenStorage.Entry("one", token, mapOf("foo" to "bar")))
        val entry = subject.entries().first()
        assertThat(entry.identifier).isEqualTo("one")
        assertThat(entry.token).isEqualTo(token)
        assertThat(entry.metadata).containsEntry("foo", "bar")
    }
}
