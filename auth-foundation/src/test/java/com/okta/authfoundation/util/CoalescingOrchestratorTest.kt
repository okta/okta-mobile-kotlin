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
package com.okta.authfoundation.util

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(DelicateCoroutinesApi::class)
internal class CoalescingOrchestratorTest {
    @Test fun testGetReturnsData(): Unit = runBlocking {
        val subject = CoalescingOrchestrator({ "it works" }, { true })
        assertThat(subject.get()).isEqualTo("it works")
    }

    @Test fun testGetOnlyCallsFactoryOnceWhenStoringInMemory(): Unit = runBlocking {
        val factoryCount = AtomicInteger(0)
        val subject = CoalescingOrchestrator(
            factory = {
                factoryCount.incrementAndGet()
                "it works"
            },
            keepDataInMemory = { true }
        )
        assertThat(subject.get()).isEqualTo("it works")
        assertThat(subject.get()).isEqualTo("it works")
        assertThat(factoryCount.get()).isEqualTo(1)
    }

    @Test fun testGetCallsFactoryTwiceWhenNotStoringInMemory(): Unit = runBlocking {
        val factoryCount = AtomicInteger(0)
        val subject = CoalescingOrchestrator(
            factory = {
                "it works ${factoryCount.getAndIncrement()}"
            },
            keepDataInMemory = { false }
        )
        assertThat(subject.get()).isEqualTo("it works 0")
        assertThat(subject.get()).isEqualTo("it works 1")
        assertThat(factoryCount.get()).isEqualTo(2)
    }

    @Test fun testGetCallsFactoryUntilKeepDataInMemoryIsTrue(): Unit = runBlocking {
        val factoryCount = AtomicInteger(0)
        val keepDataInMemoryCount = AtomicInteger(0)
        val subject = CoalescingOrchestrator(
            factory = {
                "it works ${factoryCount.getAndIncrement()}"
            },
            keepDataInMemory = {
                keepDataInMemoryCount.getAndIncrement()
                it == "it works 1"
            }
        )
        assertThat(subject.get()).isEqualTo("it works 0")
        assertThat(subject.get()).isEqualTo("it works 1")
        assertThat(subject.get()).isEqualTo("it works 1")
        assertThat(subject.get()).isEqualTo("it works 1")
        assertThat(factoryCount.get()).isEqualTo(2)
        assertThat(keepDataInMemoryCount.get()).isEqualTo(2)
    }

    @Test fun testCancellation() {
        val factoryCount = AtomicInteger(0)
        val countDownLatch = CountDownLatch(1)
        val subject = CoalescingOrchestrator(
            factory = {
                countDownLatch.countDown()
                val count = factoryCount.getAndIncrement()
                if (count == 0) {
                    delay(10_000)
                    yield()
                }
                "it works $count"
            },
            keepDataInMemory = { true }
        )

        val job1 = GlobalScope.launch(Dispatchers.IO) {
            subject.get()
        }
        assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        runBlocking {
            withTimeout(1000) {
                job1.cancelAndJoin()
            }
        }

        val job2 = GlobalScope.async(Dispatchers.IO) {
            subject.get()
        }
        runBlocking {
            assertThat(job2.await()).isEqualTo("it works 1")
        }
        assertThat(factoryCount.get()).isEqualTo(2)
    }

    @Test fun testCancellingOneDoesNotAffectOther() {
        val factoryCount = AtomicInteger(0)
        val enterFactoryCountDownLatch = CountDownLatch(1)
        val subject = CoalescingOrchestrator(
            factory = {
                yield()
                delay(10)
                enterFactoryCountDownLatch.countDown()
                val count = factoryCount.getAndIncrement()
                "it works $count"
            },
            keepDataInMemory = { false }
        )

        val callSubjectGetCountDownLatch = CountDownLatch(2)
        val job1 = GlobalScope.async(Dispatchers.IO) {
            callSubjectGetCountDownLatch.countDown()
            subject.get()
        }
        val job2 = GlobalScope.async(Dispatchers.IO) {
            callSubjectGetCountDownLatch.countDown()
            subject.get()
        }
        assertThat(enterFactoryCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        runBlocking {
            assertThat(callSubjectGetCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            job2.cancelAndJoin()
            assertThat(job1.await()).isEqualTo("it works 0")
        }

        assertThat(factoryCount.get()).isEqualTo(1)
    }

    @Test fun testParallelCallsFactoryOnce() {
        val factoryCount = AtomicInteger(0)
        val countDownLatch = CountDownLatch(1)
        val subject = CoalescingOrchestrator(
            factory = {
                countDownLatch.countDown()
                val count = factoryCount.getAndIncrement()
                delay(10)
                yield()
                "it works $count"
            },
            keepDataInMemory = { false }
        )

        val job1 = GlobalScope.async(Dispatchers.IO) {
            subject.get()
        }
        val job2 = GlobalScope.async(Dispatchers.IO) {
            subject.get()
        }
        assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        runBlocking {
            assertThat(job1.await()).isEqualTo("it works 0")
            assertThat(job2.await()).isEqualTo("it works 0")
        }

        assertThat(factoryCount.get()).isEqualTo(1)
    }
}
