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
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

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
}
