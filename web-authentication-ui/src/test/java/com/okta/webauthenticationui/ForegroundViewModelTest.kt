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

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.browser.auth.AuthTabIntent
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Uses primitive-typed [ForegroundViewModel.onAuthTabResult] arguments rather than a real
 * `AuthTabIntent.AuthResult` — that type has a package-private constructor and can't be constructed
 * or mocked from this module, which is exactly why [ForegroundActivity] unwraps it before calling in.
 */
@RunWith(RobolectricTestRunner::class)
class ForegroundViewModelTest {
    private lateinit var redirectCoordinator: RedirectCoordinator
    private lateinit var viewModel: ForegroundViewModel

    @Before fun setup() {
        redirectCoordinator =
            mock<RedirectCoordinator> {
                onBlocking { runInitializationFunction() } doReturn
                    RedirectInitializationResult.Error<Any>(IllegalStateException("Not used in this test."))
            }
        ForegroundViewModel.redirectCoordinator = redirectCoordinator
        viewModel = ForegroundViewModel(SavedStateHandle())
    }

    @After fun tearDown() {
        ForegroundViewModel.redirectCoordinator = SingletonRedirectCoordinator
    }

    @Test fun testOnAuthTabResult_ResultOk_EmitsUri() {
        val uri = Uri.parse("unitTest:/callback?code=abc")
        viewModel.onAuthTabResult(AuthTabIntent.RESULT_OK, uri)
        verify(redirectCoordinator).emit(uri)
    }

    @Test fun testOnAuthTabResult_ResultCanceled_EmitsNull() {
        viewModel.onAuthTabResult(AuthTabIntent.RESULT_CANCELED, null)
        verify(redirectCoordinator).emit(null)
    }

    @Test fun testOnAuthTabResult_VerificationFailed_EmitsNull() {
        viewModel.onAuthTabResult(AuthTabIntent.RESULT_VERIFICATION_FAILED, null)
        verify(redirectCoordinator).emit(null)
    }

    @Test fun testOnAuthTabResult_VerificationTimedOut_EmitsNull() {
        viewModel.onAuthTabResult(AuthTabIntent.RESULT_VERIFICATION_TIMED_OUT, null)
        verify(redirectCoordinator).emit(null)
    }

    @Test fun testOnAuthTabResult_UnknownCode_EmitsNull() {
        viewModel.onAuthTabResult(AuthTabIntent.RESULT_UNKNOWN_CODE, null)
        verify(redirectCoordinator).emit(null)
    }

    @Test fun testLaunchBrowser_PassesAuthTabLauncherThrough() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val launcher = mock<ActivityResultLauncher<Intent>>()
        whenever(redirectCoordinator.launchWebAuthenticationProvider(any(), any(), any())).thenReturn(true)

        viewModel.launchBrowser(activity, "https://example.com/authorize", launcher)

        verify(redirectCoordinator).launchWebAuthenticationProvider(activity, "https://example.com/authorize".toHttpUrl(), launcher)
        assertThat(activity.isFinishing).isFalse()
    }

    @Test fun testLaunchBrowser_FinishesActivityWhenLaunchFails() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val launcher = mock<ActivityResultLauncher<Intent>>()
        whenever(redirectCoordinator.launchWebAuthenticationProvider(any(), any(), any())).thenReturn(false)

        viewModel.launchBrowser(activity, "https://example.com/authorize", launcher)

        assertThat(activity.isFinishing).isTrue()
    }
}
