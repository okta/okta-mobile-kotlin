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

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.credential.CredentialDataSource.Companion.credentialDataSource
import com.okta.testhelpers.OktaRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CredentialDataSourceTest {
    @get:Rule val oktaRule = OktaRule()

    @Test fun testCreate(): Unit = runBlocking {
        val tokenStorage = spy(InMemoryTokenStorage())
        val oidcClient = oktaRule.createOidcClient()
        val dataSource = oidcClient.credentialDataSource(tokenStorage)
        val credential = dataSource.create()
        assertThat(credential.token).isNull()
        assertThat(credential.metadata).isEmpty()
        assertThat(credential.oidcClient.credential).isSameInstanceAs(credential)
        assertThat(credential.oidcClient.endpoints).isSameInstanceAs(oidcClient.endpoints)
        verify(tokenStorage).add(any())
    }

    @Test fun testAll(): Unit = runBlocking {
        val tokenStorage = spy(InMemoryTokenStorage())
        tokenStorage.add("First")
        tokenStorage.add("Second")
        val dataSource = oktaRule.createOidcClient().credentialDataSource(tokenStorage)
        val credentials = dataSource.all()
        assertThat(credentials).hasSize(2)
        assertThat(credentials[0].storageIdentifier).isEqualTo("First")
        assertThat(credentials[1].storageIdentifier).isEqualTo("Second")
        verify(tokenStorage).entries() // Verify it only happened once, not twice.
    }

    @Test fun testConcurrentCreate(): Unit = runBlocking {
        val countDownLatch = CountDownLatch(2)
        val entriesCount = AtomicInteger(0)
        val tokenStorage = spy(object : TokenStorage {
            override suspend fun entries(): List<TokenStorage.Entry> {
                entriesCount.incrementAndGet()
                assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
                // Delay and change threads before returning the result to make sure we enqueue both requests before returning.
                delay(10)
                return withContext(Dispatchers.Default) {
                    emptyList()
                }
            }

            override suspend fun add(id: String) {}

            override suspend fun remove(id: String) {
                throw AssertionError("Not expected")
            }

            override suspend fun replace(updatedEntry: TokenStorage.Entry) {
                throw AssertionError("Not expected")
            }
        })
        val dataSource = oktaRule.createOidcClient().credentialDataSource(tokenStorage)
        val deferred1 = async(Dispatchers.IO) {
            countDownLatch.countDown()
            dataSource.create()
        }
        val deferred2 = async(Dispatchers.IO) {
            countDownLatch.countDown()
            dataSource.create()
        }

        deferred1.await()
        deferred2.await()

        assertThat(dataSource.all()).hasSize(2)
        assertThat(entriesCount.get()).isEqualTo(1)
        verify(tokenStorage, times(2)).add(any())
    }

    @Test fun testAllReturnsCreatedInstance(): Unit = runBlocking {
        val tokenStorage = InMemoryTokenStorage()
        tokenStorage.add("First")
        tokenStorage.add("Second")
        val dataSource = oktaRule.createOidcClient().credentialDataSource(tokenStorage)
        val credential = dataSource.create()
        assertThat(credential.token).isNull()
        assertThat(credential.metadata).isEmpty()
        assertThat(credential.oidcClient.credential).isEqualTo(credential)
        assertThat(dataSource.all()).hasSize(3)
        assertThat(dataSource.all()[2]).isSameInstanceAs(credential)
    }

    @Test fun testRemove(): Unit = runBlocking {
        val tokenStorage = InMemoryTokenStorage()
        tokenStorage.add("First")
        tokenStorage.add("Second")
        val dataSource = oktaRule.createOidcClient().credentialDataSource(tokenStorage)
        val credential = dataSource.all().first()
        credential.remove()
        assertThat(dataSource.all()).hasSize(1)
    }
}
