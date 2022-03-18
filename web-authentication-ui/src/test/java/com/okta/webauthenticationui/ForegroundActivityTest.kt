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
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatcher
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ForegroundActivityTest {
    @Test fun testResumeLaunchesWebAuthenticationProvider() {
        val launchedFromActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        val intent = ForegroundActivity.createIntent(launchedFromActivity, "https://example.com/redirect".toHttpUrl())
        val controller = Robolectric.buildActivity(ForegroundActivity::class.java, intent)
        val activity = controller.get()
        val redirectCoordinator = mock<RedirectCoordinator> {
            on { launchWebAuthenticationProvider(any(), any()) } doReturn true
        }
        activity.redirectCoordinator = redirectCoordinator
        controller.create().resume()
        verify(redirectCoordinator).launchWebAuthenticationProvider(any(), any())
        assertThat(activity.isFinishing).isFalse()
        verify(redirectCoordinator, never()).emit(anyOrNull())
        verify(redirectCoordinator, never()).emitError(any())
    }

    @Test fun testResumeFinishesWhenWebAuthenticationProviderFails() {
        val launchedFromActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        val intent = ForegroundActivity.createIntent(launchedFromActivity, "https://example.com/redirect".toHttpUrl())
        val controller = Robolectric.buildActivity(ForegroundActivity::class.java, intent)
        val activity = controller.get()
        val redirectCoordinator = mock<RedirectCoordinator> {
            on { launchWebAuthenticationProvider(any(), any()) } doReturn false
        }
        activity.redirectCoordinator = redirectCoordinator
        controller.create().resume()
        assertThat(activity.isFinishing).isTrue()
        verify(redirectCoordinator, never()).emit(anyOrNull())
        verify(redirectCoordinator, never()).emitError(any())
    }

    @Test fun testResumeFinishesWhenUrlIsInvalid() {
        val launchedFromActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        val intent = ForegroundActivity.createIntent(launchedFromActivity, "https://example.com/redirect".toHttpUrl())
        intent.removeExtra("com.okta.webauthenticationui.ForegroundActivity.url")
        val controller = Robolectric.buildActivity(ForegroundActivity::class.java, intent)
        val activity = controller.get()
        val redirectCoordinator = mock<RedirectCoordinator> {
            on { launchWebAuthenticationProvider(any(), any()) } doReturn false
        }
        activity.redirectCoordinator = redirectCoordinator
        controller.create().resume()
        assertThat(activity.isFinishing).isTrue()
        verify(redirectCoordinator, never()).emit(anyOrNull())
        verify(redirectCoordinator).emitError(argThat(ExceptionMatcher(IllegalStateException("Url not provided when launching ForegroundActivity."))))
    }

    @Test fun testCancellation() {
        val launchedFromActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        val intent = ForegroundActivity.createIntent(launchedFromActivity, "https://example.com/redirect".toHttpUrl())
        val controller = Robolectric.buildActivity(ForegroundActivity::class.java, intent)
        val activity = controller.get()
        val redirectCoordinator = mock<RedirectCoordinator> {
            on { launchWebAuthenticationProvider(any(), any()) } doReturn true
        }
        activity.redirectCoordinator = redirectCoordinator
        controller.create().resume().pause().resume()
        assertThat(activity.isFinishing).isTrue()
        verify(redirectCoordinator).emit(null)
        verify(redirectCoordinator, never()).emitError(any())
    }

    @Test fun testRedirect() {
        val launchedFromActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        val intent = ForegroundActivity.createIntent(launchedFromActivity, "https://example.com/redirect".toHttpUrl())
        val controller = Robolectric.buildActivity(ForegroundActivity::class.java, intent)
        val activity = controller.get()
        val redirectCoordinator = mock<RedirectCoordinator> {
            on { launchWebAuthenticationProvider(any(), any()) } doReturn true
        }
        activity.redirectCoordinator = redirectCoordinator
        controller.create().resume()
        assertThat(activity.isFinishing).isFalse()
        verify(redirectCoordinator, never()).emit(anyOrNull())
        verify(redirectCoordinator, never()).emitError(any())

        val uri = Uri.parse("unitTest:/redirect")
        controller.newIntent(ForegroundActivity.redirectIntent(launchedFromActivity, uri))
        assertThat(activity.isFinishing).isTrue()
        verify(redirectCoordinator).emit(uri)
    }
}

private class ExceptionMatcher(
    private val expected: Exception,
) : ArgumentMatcher<Exception> {
    override fun matches(argument: Exception): Boolean {
        return expected.javaClass == argument.javaClass && expected.message == argument.message
    }
}
