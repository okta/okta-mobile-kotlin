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
package com.okta.idx.kotlin.client

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.client.ApplicationContextHolder
import com.okta.authfoundation.client.DeviceTokenProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DeviceTokenProviderTest {
    private val uuid = UUID.randomUUID()
    private val deviceToken = uuid.toString().filter { it.isLetterOrDigit() }
    private lateinit var deviceTokenProvider: DeviceTokenProvider

    @Before
    fun setup() {
        unmockkAll()
        mockkObject(ApplicationContextHolder)
        every { ApplicationContextHolder.appContext } returns ApplicationProvider.getApplicationContext()
        mockkStatic(UUID::class)
        every { UUID.randomUUID() } returns uuid
        mockkConstructor(LegacyDeviceTokenProvider::class)
        every { anyConstructed<LegacyDeviceTokenProvider>().containsDeviceToken() } returns false

        deviceTokenProvider = DeviceTokenProvider(
            mockk {
                every { encryptString(any()) } returnsArgument 0
                every { decryptString(any()) } returnsArgument 0
            }
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `return a random token on initialization`() = runTest {
        val token = deviceTokenProvider.getDeviceToken()
        assertThat(token).isEqualTo(deviceToken)
    }

    @Test
    fun `return the same token on repeated calls to DeviceTokenProvider`() = runTest {
        val token1 = deviceTokenProvider.getDeviceToken()
        every { UUID.randomUUID() } throws IllegalStateException("UUID.randomUUID called a second time")
        val token2 = deviceTokenProvider.getDeviceToken()
        assertThat(token1).isEqualTo(token2)
        assertThat(token1).isEqualTo(deviceToken)
    }

    @Test
    fun `return device token from LegacyDeviceTokenProvider instead of creating a new token`() = runTest {
        val legacyDeviceToken = "legacyDeviceToken"
        every { anyConstructed<LegacyDeviceTokenProvider>().containsDeviceToken() } returns true
        every { anyConstructed<LegacyDeviceTokenProvider>().deviceToken } returns legacyDeviceToken

        assertThat(deviceTokenProvider.getDeviceToken()).isEqualTo(legacyDeviceToken)
    }

    @Test
    fun `device token is at most 32 characters`() = runTest {
        unmockkStatic(UUID::class) // Make sure we're always getting a real UUID here
        val token = deviceTokenProvider.getDeviceToken()
        assertThat(token.length).isAtMost(32)
        assertThat(token.length).isEqualTo(32)
    }
}
