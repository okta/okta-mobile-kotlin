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

import android.content.ActivityNotFoundException
import android.content.Context
import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

/**
 * Covers [WebAuthenticationProvider.launchCustomTab]'s default implementation, which delegates to
 * the deprecated [WebAuthenticationProvider.launch] so that existing implementations of the
 * interface keep compiling without also having to implement [WebAuthenticationProvider.launchCustomTab].
 */
@RunWith(RobolectricTestRunner::class)
class WebAuthenticationProviderTest {
    @Test
    fun testLaunchCustomTabDefaultImpl_delegatesToLaunch_onSuccess() {
        val provider = FakeLegacyWebAuthenticationProvider(exception = null)
        val result = provider.launchCustomTab(mock(), "https://example.com".toHttpUrl())
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun testLaunchCustomTabDefaultImpl_delegatesToLaunch_onFailure() {
        val exception = ActivityNotFoundException("From Test!")
        val provider = FakeLegacyWebAuthenticationProvider(exception)
        val result = provider.launchCustomTab(mock(), "https://example.com".toHttpUrl())
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isSameInstanceAs(exception)
    }

    /** A [WebAuthenticationProvider] implementation that only overrides the deprecated [launch], as pre-existing implementations do. */
    private class FakeLegacyWebAuthenticationProvider(
        private val exception: Exception?,
    ) : WebAuthenticationProvider {
        @Suppress("DEPRECATION")
        @Deprecated("Use launchCustomTab(context, url) to receive a Result-based outcome.")
        override fun launch(
            context: Context,
            url: HttpUrl,
        ): Exception? = exception
    }
}
