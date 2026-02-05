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
package com.okta.authfoundation

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFailsWith

class NoSetAfterGetWithLazyDefaultFactoryTest {
    class Example {
        val counter = AtomicInteger(0)
        var test: String by NoSetAfterGetWithLazyDefaultFactory {
            counter.getAndAdd(1)
            "default"
        }
    }

    private lateinit var subject: Example

    @Before fun setup() {
        subject = Example()
    }

    @Test fun testDefaultFactory() {
        assertThat(subject.test).isEqualTo("default")
    }

    @Test fun testSet() {
        subject.test = "Custom!"
        assertThat(subject.test).isEqualTo("Custom!")
        assertThat(subject.counter.get()).isEqualTo(0)
    }

    @Test fun testFactoryOnlyCalledOnce() {
        assertThat(subject.counter.get()).isEqualTo(0)
        assertThat(subject.test).isEqualTo("default")
        assertThat(subject.counter.get()).isEqualTo(1)
        assertThat(subject.test).isEqualTo("default")
        assertThat(subject.counter.get()).isEqualTo(1)
    }

    @Test fun testSetAfterGetThrows() {
        assertThat(subject.test).isEqualTo("default")
        assertFailsWith<IllegalStateException>(message = "test was already accessed, and can't be set.") {
            subject.test = "This will throw!"
        }
    }

    @Test fun testGetCalledFromThreadOtherThanSet() {
        val latch = CountDownLatch(1)
        Executors.newSingleThreadExecutor().submit {
            subject.test = "Custom!"
            latch.countDown()
        }
        latch.await()
        assertThat(subject.test).isEqualTo("Custom!")
    }

    @Test fun testFactoryOnlyCalledOnceFromMultipleThreads() {
        val executor = Executors.newFixedThreadPool(25)
        val latch = CountDownLatch(25)
        repeat(25) {
            executor.submit {
                assertThat(subject.test).isEqualTo("default")
                latch.countDown()
            }
        }
        latch.await()
        assertThat(subject.counter.get()).isEqualTo(1)
    }
}
