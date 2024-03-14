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
package com.okta.authfoundation.client

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class SharedPreferencesCacheTest {
    @Before fun setup() {
        mockkObject(ApplicationContextHolder)
        coEvery { ApplicationContextHolder.getApplicationContext() } returns ApplicationProvider.getApplicationContext()
    }

    @After fun tearDown() {
        unmockkAll()
    }

    @Test fun testGetWithoutSet() {
        val subject = SharedPreferencesCache.instance
        assertThat(subject.get("foo")).isNull()
    }

    @Test fun testGetWithSet() {
        val subject = SharedPreferencesCache.instance
        subject.set("foo", "bar")
        assertThat(subject.get("foo")).isEqualTo("bar")
    }

    @Test fun testCacheEntriesAreNotShared() {
        val subject = SharedPreferencesCache.instance
        subject.set("foo", "bar")
        subject.set("food", "chocolate")
        assertThat(subject.get("foo")).isEqualTo("bar")
        assertThat(subject.get("food")).isEqualTo("chocolate")
    }
}
