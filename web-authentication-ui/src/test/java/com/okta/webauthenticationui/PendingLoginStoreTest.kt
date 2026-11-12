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
package com.okta.webauthenticationui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.okta.oauth2.kmp.AuthorizationCodeFlowContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PendingLoginStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val flowContext = AuthorizationCodeFlowContext("https://e.com/authorize", "app:/cb", "verifier", "state", "nonce", null)

    @Test fun load_AfterSave_ReturnsSavedContext() =
        runTest {
            val store = PendingLoginStore(context)
            store.save(flowContext)

            assertThat(PendingLoginStore(context).load()?.flowContext).isEqualTo(flowContext)
        }

    @Test fun load_AfterClear_ReturnsNull() =
        runTest {
            val store = PendingLoginStore(context)
            store.save(flowContext)
            store.clear()

            assertThat(store.load()).isNull()
        }

    @Test fun load_ExpiredEntry_ReturnsNullAndDiscardsIt() =
        runTest {
            var now = 0L
            val store = PendingLoginStore(context, clock = { now }, maxAgeMillis = 1_000L)
            store.save(flowContext)
            now = 1_001L

            assertThat(store.load()).isNull()
            now = 0L
            assertThat(store.load()).isNull()
        }

    @Test fun save_SecondLogin_OverwritesFirst() =
        runTest {
            val store = PendingLoginStore(context)
            val second = flowContext.copy(state = "other-state")
            store.save(flowContext)
            store.save(second)

            assertThat(store.load()?.flowContext).isEqualTo(second)
        }

    private val reversingEncryptor =
        object : PendingLoginEncryptor {
            override suspend fun encrypt(plaintext: String) = Result.success(plaintext.reversed())

            override suspend fun decrypt(ciphertext: String) = Result.success(ciphertext.reversed())
        }

    @Test fun load_WithEncryptor_RoundTripsAndStoresTransformedValue() =
        runTest {
            PendingLoginStore(context, reversingEncryptor).save(flowContext)

            assertThat(PendingLoginStore(context, reversingEncryptor).load()?.flowContext).isEqualTo(flowContext)
            // Without the encryptor the reversed (non-JSON) value is unreadable.
            assertThat(PendingLoginStore(context).load()).isNull()
        }

    @Test fun save_EncryptorFails_ReturnsFailureAndPersistsNothing() =
        runTest {
            val failing =
                object : PendingLoginEncryptor {
                    override suspend fun encrypt(plaintext: String) = Result.failure<String>(IllegalStateException("no key"))

                    override suspend fun decrypt(ciphertext: String) = Result.failure<String>(IllegalStateException("no key"))
                }
            val store = PendingLoginStore(context, failing)

            assertThat(store.save(flowContext).isFailure).isTrue()
            assertThat(store.load()).isNull()
        }

    @Test fun load_DecryptFails_ReturnsNull() =
        runTest {
            PendingLoginStore(context, reversingEncryptor).save(flowContext)
            val failingDecrypt =
                object : PendingLoginEncryptor {
                    override suspend fun encrypt(plaintext: String) = Result.success(plaintext)

                    override suspend fun decrypt(ciphertext: String) = Result.failure<String>(IllegalStateException("key lost"))
                }

            assertThat(PendingLoginStore(context, failingDecrypt).load()).isNull()
        }

    @Test fun load_ReportsRemainingLifetime() =
        runTest {
            var now = 1_000L
            val store = PendingLoginStore(context, clock = { now }, maxAgeMillis = 10_000L)
            store.save(flowContext)
            now = 4_000L

            assertThat(store.load()?.remainingMillis).isEqualTo(7_000L)
        }

    @Test fun load_ClockMovedBackwards_TreatsEntryAsExpired() =
        runTest {
            var now = 5_000L
            val store = PendingLoginStore(context, clock = { now })
            store.save(flowContext)
            now = 1_000L

            assertThat(store.load()).isNull()
        }

    @Test(expected = CancellationException::class)
    fun save_EncryptorCancelled_PropagatesCancellation(): Unit =
        runTest {
            val cancelling =
                object : PendingLoginEncryptor {
                    override suspend fun encrypt(plaintext: String): Result<String> = throw CancellationException("cancelled")

                    override suspend fun decrypt(ciphertext: String) = Result.success(ciphertext)
                }
            PendingLoginStore(context, cancelling).save(flowContext)
        }
}
