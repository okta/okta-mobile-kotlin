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
import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.AuthFoundationDefaults
import com.okta.authfoundation.events.EventCoordinator
import com.okta.testhelpers.RecordingEventHandler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import okhttp3.HttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ForegroundActivityTest {
    private lateinit var mockUrl: HttpUrl
    private lateinit var eventHandler: RecordingEventHandler
    private val testUrlString = "http://testing"

    @Before
    fun setup() {
        mockUrl = mockk(relaxed = true)
        every { mockUrl.toString() } returns testUrlString
        eventHandler = RecordingEventHandler()
        mockkObject(AuthFoundationDefaults)
        every { AuthFoundationDefaults.eventCoordinator } returns EventCoordinator(eventHandler)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test fun testResumeLaunchesWebAuthenticationProvider() {
        val launchedFromActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        val intent = ForegroundActivity.createIntent(launchedFromActivity)
        val controller = Robolectric.buildActivity(ForegroundActivity::class.java, intent)
        val activity = controller.get()
        val redirectCoordinator = mock<RedirectCoordinator> {
            on { launchWebAuthenticationProvider(any(), any()) } doReturn true
            onBlocking { runInitializationFunction() } doAnswer {
                RedirectInitializationResult.Success(mockUrl, Any())
            }
        }
        ForegroundViewModel.redirectCoordinator = redirectCoordinator
        controller.create().resume()

        verify(redirectCoordinator).launchWebAuthenticationProvider(any(), any())
        assertThat(activity.isFinishing).isFalse()
        verify(redirectCoordinator, never()).emit(anyOrNull())
        verify(redirectCoordinator, never()).emitError(any())
    }

    @Test fun testLaunchingWebAuthenticationProviderIsDelayedUntilActivityIsInForeground() {
        val launchedFromActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        val intent = ForegroundActivity.createIntent(launchedFromActivity)
        val controller = Robolectric.buildActivity(ForegroundActivity::class.java, intent)
        val activity = controller.get()
        val deferred = CompletableDeferred<RedirectInitializationResult<Any>>()
        val redirectCoordinator = mock<RedirectCoordinator> {
            on { launchWebAuthenticationProvider(any(), any()) } doReturn true
            onBlocking { runInitializationFunction() } doSuspendableAnswer {
                deferred.await()
            }
        }
        ForegroundViewModel.redirectCoordinator = redirectCoordinator
        controller.create().resume().pause()
        verify(redirectCoordinator, never()).launchWebAuthenticationProvider(any(), any())
        deferred.complete(RedirectInitializationResult.Success(mockUrl, Any()))
        controller.resume()

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
                RedirectInitializationResult.Success(mockUrl, Any())
            }
        }
        ForegroundViewModel.redirectCoordinator = redirectCoordinator
        controller.create().resume()
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
                RedirectInitializationResult.Success(mockUrl, Any())
            }
        }
        ForegroundViewModel.redirectCoordinator = redirectCoordinator
        controller.create().resume()
        assertThat(activity.isFinishing).isFalse()
        controller.pause().resume()
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
                RedirectInitializationResult.Success(mockUrl, Any())
            }
        }
        ForegroundViewModel.redirectCoordinator = redirectCoordinator
        controller.create().resume()
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

        verify(redirectCoordinator, never()).launchWebAuthenticationProvider(any(), any())
        assertThat(activity.isFinishing).isTrue()
        verify(redirectCoordinator, never()).emit(anyOrNull())
        verify(redirectCoordinator, never()).emitError(any())
    }

    @Test
    fun `test lifecycle events`() {
        val launchedFromActivity = Robolectric.buildActivity(Activity::class.java).create().get()
        val intent = ForegroundActivity.createIntent(launchedFromActivity)
        val controller = Robolectric.buildActivity(ForegroundActivity::class.java, intent)
        val redirectCoordinator = mockk<RedirectCoordinator> {
            every { launchWebAuthenticationProvider(any(), any()) } returns true
            coEvery { runInitializationFunction() } answers {
                RedirectInitializationResult.Success(mockUrl, Any())
            }
        }
        ForegroundViewModel.redirectCoordinator = redirectCoordinator

        controller.create().resume().pause().newIntent(Intent()).destroy()
        assertThat(eventHandler.toList()).isEqualTo(
            listOf(
                ForegroundActivityEvent.OnCreate,
                ForegroundActivityEvent.OnResume,
                ForegroundActivityEvent.OnPause,
                ForegroundActivityEvent.OnNewIntent,
                ForegroundActivityEvent.OnDestroy
            )
        )
    }
}
