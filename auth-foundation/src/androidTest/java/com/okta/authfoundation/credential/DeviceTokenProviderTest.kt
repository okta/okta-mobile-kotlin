/*
 * Copyright 2023-Present Okta, Inc.
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.client.DeviceTokenProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceTokenProviderTest {
    private lateinit var context: Context
    private lateinit var deviceTokenProvider: DeviceTokenProvider

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        deviceTokenProvider = DeviceTokenProvider(context)
        clearSharedPreferences()
    }

    private fun clearSharedPreferences() {
        context.getSharedPreferences(DeviceTokenProvider.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testDeviceTokenProviderInitializesCorrectly() {
        val actualDeviceToken = deviceTokenProvider.deviceToken
        val expectedDeviceToken = deviceTokenProvider.sharedPrefs.getString(
            DeviceTokenProvider.PREFERENCE_KEY, null
        )!!.filter { it.isLetterOrDigit() }
        assertThat(actualDeviceToken).isEqualTo(expectedDeviceToken)
    }

    @Test
    fun testDeviceTokenIsAtMost32Characters() {
        val actualDeviceToken = deviceTokenProvider.deviceToken
        assertThat(actualDeviceToken.length).isAtMost(32)
    }
}
