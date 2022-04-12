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
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import java.lang.IllegalStateException

@RunWith(RobolectricTestRunner::class)
class ForegroundActivityTest {
    @Test fun testResumeLaunchesWebAuthenticationProvider() {
        val launchedFromActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        val intent = ForegroundActivity.createIntent(launchedFromActivity)
        val controller = Robolectric.buildActivity(ForegroundActivity::class.java, intent)
        val activity = controller.get()
        val redirectCoordinator = mock<RedirectCoordinator> {
            on { launchWebAuthenticationProvider(any(), any()) } doReturn true
            onBlocking { runInitializationFunction() } doAnswer {
                RedirectInitializationResult.Success(mock(), Any())
            }
        }
        ForegroundViewModel.redirectCoordinator = redirectCoordinator
        controller.create().resume()

        // Needs to run in a delay to ensure it's not instantly cancelled by onResume.
        verify(redirectCoordinator, never()).launchWebAuthenticationProvider(any(), any())

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        verify(redirectCoordinator).launchWebAuthenticationProvider(any(), any())
        assertThat(activity.isFinishing).isFalse()
        verify(redirectCoordinator, never()).emit(anyOrNull())
        verify(redirectCoordinator, never()).emitError(any())
    }

    @Test fun testResumeFinishesWhenWebAuthenticationProviderFails() {
        val launchedFromActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        val intent = ForegroundActivity.createIntent(launchedFromActivity)
        val controller = Robolectric.buildActivity(ForegroundActivity::class.java, intent)
        val activity = controller.get()
        val redirectCoordinator = mock<RedirectCoordinator> {
            on { launchWebAuthenticationProvider(any(), any()) } doReturn false
            onBlocking { runInitializationFunction() } doAnswer {
                RedirectInitializationResult.Success(mock(), Any())
            }
        }
        ForegroundViewModel.redirectCoordinator = redirectCoordinator
        controller.create().resume()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertThat(activity.isFinishing).isTrue()
        verify(redirectCoordinator, never()).emit(anyOrNull())
        verify(redirectCoordinator, never()).emitError(any())
    }

    @Test fun testCancellation() {
        val launchedFromActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        val intent = ForegroundActivity.createIntent(launchedFromActivity)
        val controller = Robolectric.buildActivity(ForegroundActivity::class.java, intent)
        val activity = controller.get()
        val redirectCoordinator = mock<RedirectCoordinator> {
            on { launchWebAuthenticationProvider(any(), any()) } doReturn true
            onBlocking { runInitializationFunction() } doAnswer {
                RedirectInitializationResult.Success(mock(), Any())
            }
        }
        ForegroundViewModel.redirectCoordinator = redirectCoordinator
        controller.create().resume()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertThat(activity.isFinishing).isFalse()
        controller.pause().resume()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertThat(activity.isFinishing).isTrue()
        verify(redirectCoordinator).emit(null)
        verify(redirectCoordinator, never()).emitError(any())
    }

    @Test fun testRedirect() {
        val launchedFromActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        val intent = ForegroundActivity.createIntent(launchedFromActivity)
        val controller = Robolectric.buildActivity(ForegroundActivity::class.java, intent)
        val activity = controller.get()
        val redirectCoordinator = mock<RedirectCoordinator> {
            on { launchWebAuthenticationProvider(any(), any()) } doReturn true
            onBlocking { runInitializationFunction() } doAnswer {
                RedirectInitializationResult.Success(mock(), Any())
            }
        }
        ForegroundViewModel.redirectCoordinator = redirectCoordinator
        controller.create().resume()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertThat(activity.isFinishing).isFalse()
        verify(redirectCoordinator, never()).emit(anyOrNull())
        verify(redirectCoordinator, never()).emitError(any())

        val uri = Uri.parse("unitTest:/redirect")
        controller.newIntent(ForegroundActivity.redirectIntent(launchedFromActivity, uri))
        assertThat(activity.isFinishing).isTrue()
        verify(redirectCoordinator).emit(uri)
    }

    @Test fun testNetworkFailureFinishesActivity() {
        val launchedFromActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        val intent = ForegroundActivity.createIntent(launchedFromActivity)
        val controller = Robolectric.buildActivity(ForegroundActivity::class.java, intent)
        val activity = controller.get()
        val redirectCoordinator = mock<RedirectCoordinator> {
            on { launchWebAuthenticationProvider(any(), any()) } doReturn true
            onBlocking { runInitializationFunction() } doAnswer {
                RedirectInitializationResult.Error<Any>(IllegalStateException("From Test!"))
            }
        }
        ForegroundViewModel.redirectCoordinator = redirectCoordinator
        controller.create().resume()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verify(redirectCoordinator, never()).launchWebAuthenticationProvider(any(), any())
        assertThat(activity.isFinishing).isTrue()
        verify(redirectCoordinator, never()).emit(anyOrNull())
        verify(redirectCoordinator, never()).emitError(any())
    }
}
