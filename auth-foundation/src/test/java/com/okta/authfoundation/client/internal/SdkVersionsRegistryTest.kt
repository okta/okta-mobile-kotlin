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
package com.okta.authfoundation.client.internal

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SdkVersionsRegistryTest {
    private val defaultAndroidVersion = 19

    @Before fun reset() {
        SdkVersionsRegistry.reset()
    }

    @Test fun testUserAgentWithNoOtherVersionsRegistered() {
        assertThat(SdkVersionsRegistry.userAgent).matches("okta-auth-foundation-kotlin/.* Android/$defaultAndroidVersion")
    }

    @Test fun testRegisterRegeneratesUserAgent() {
        SdkVersionsRegistry.register("atest/1.0.0")
        assertThat(SdkVersionsRegistry.userAgent).matches("atest/1.0.0 okta-auth-foundation-kotlin/.* Android/$defaultAndroidVersion")
    }

    @Test fun testRegisterRegeneratesUserAgentSorted() {
        SdkVersionsRegistry.register("ztest/1.0.0")
        assertThat(SdkVersionsRegistry.userAgent).matches("okta-auth-foundation-kotlin/.* ztest/1.0.0 Android/$defaultAndroidVersion")
    }

    @Test fun testRegisterCanBeCalledMultipleTimes() {
        val testVersion = "test/1.0.0"
        SdkVersionsRegistry.register(testVersion)
        SdkVersionsRegistry.register(testVersion)
        val userAgent = SdkVersionsRegistry.userAgent
        val occurrences = userAgent.windowed(testVersion.length) {
            if (it == testVersion)
                1
            else
                0
        }.sum()
        assertThat(occurrences).isEqualTo(1)
    }
}
