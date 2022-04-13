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
import android.content.ActivityNotFoundException
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class RedirectCoordinatorTest {
    private lateinit var subject: DefaultRedirectCoordinator

    @Before fun setup() {
        subject = DefaultRedirectCoordinator()
    }

    private fun CoroutineScope.listenRedirectAsync(): Deferred<RedirectResult> {
        val countDownLatch = CountDownLatch(1)
        subject.redirectContinuationListeningCallback = {
            countDownLatch.countDown()
        }
        val resultDeferred = async(Dispatchers.IO) { subject.listenForResult() }
        assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        return resultDeferred
    }

    @Test fun testEmitError(): Unit = runBlocking {
        val resultDeferred = listenRedirectAsync()
        subject.emitError(IllegalStateException("From Test!"))
        val result = resultDeferred.await()
        assertThat(result).isInstanceOf(RedirectResult.Error::class.java)
        val error = result as RedirectResult.Error
        assertThat(error.exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(error.exception).hasMessageThat().isEqualTo("From Test!")
    }

    @Test fun testEmit(): Unit = runBlocking {
        val resultDeferred = listenRedirectAsync()
        subject.emit(Uri.parse("unitTest:/hello"))
        val result = resultDeferred.await()
        assertThat(result).isInstanceOf(RedirectResult.Redirect::class.java)
        val redirect = result as RedirectResult.Redirect
        assertThat(redirect.uri).isEqualTo(Uri.parse("unitTest:/hello"))
    }

    @Test fun testEmitNullIsCancel(): Unit = runBlocking {
        val resultDeferred = listenRedirectAsync()
        subject.emit(null)
        val result = resultDeferred.await()
        assertThat(result).isInstanceOf(RedirectResult.Error::class.java)
        val error = result as RedirectResult.Error
        assertThat(error.exception).isInstanceOf(WebAuthenticationClient.FlowCancelledException::class.java)
    }

    @Test fun testEmitNullCancelsInitialization(): Unit = runBlocking {
        val countDownLatch = CountDownLatch(1)
        subject.initializerContinuationListeningCallback = {
            countDownLatch.countDown()
        }
        val resultDeferred = async(Dispatchers.IO) {
            subject.initialize(mock(), mock()) {
                RedirectInitializationResult.Success(mock(), Any())
            }
        }
        assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        subject.emit(null)
        val result = resultDeferred.await()
        assertThat(result).isInstanceOf(RedirectInitializationResult.Error::class.java)
        val error = result as RedirectInitializationResult.Error
        assertThat(error.exception).isInstanceOf(WebAuthenticationClient.FlowCancelledException::class.java)
    }

    @Test fun testEmitWithoutListening(): Unit = runBlocking {
        subject.emit(null)
    }

    @Test fun testEmitErrorWithoutListening(): Unit = runBlocking {
        subject.emitError(IllegalStateException("From Test!"))
    }

    @Test fun testLaunchWebAuthenticationProviderWithoutInitialize(): Unit = runBlocking {
        val resultDeferred = listenRedirectAsync()
        assertThat(subject.launchWebAuthenticationProvider(mock(), "https://example.com".toHttpUrl())).isFalse()
        val result = resultDeferred.await()
        assertThat(result).isInstanceOf(RedirectResult.Error::class.java)
        val error = result as RedirectResult.Error
        assertThat(error.exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(error.exception).hasMessageThat().isEqualTo("RedirectListener has not been initialized.")
    }

    @Test fun testRunInitializationFunctionReturnsErrorIfInitializeIsNotCalled(): Unit = runBlocking {
        val webAuthenticationProvider = mock<WebAuthenticationProvider>()
        val result = subject.runInitializationFunction()
        verifyNoInteractions(webAuthenticationProvider)
        assertThat(result).isInstanceOf(RedirectInitializationResult.Error::class.java)
        val errorResult = result as RedirectInitializationResult.Error<*>
        assertThat(errorResult.exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No initializer")
    }

    @Test fun testRunInitializationFunctionClearsInitializer(): Unit = runBlocking {
        val webAuthenticationProvider = mock<WebAuthenticationProvider>()
        val initializerCountDownLatch = CountDownLatch(1)
        subject.initializerContinuationListeningCallback = {
            initializerCountDownLatch.countDown()
        }
        launch(Dispatchers.IO) {
            subject.initialize(webAuthenticationProvider, mock()) {
                RedirectInitializationResult.Success(mock(), Any())
            }
        }
        assertThat(initializerCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        val result1 = subject.runInitializationFunction()
        assertThat(result1).isInstanceOf(RedirectInitializationResult.Success::class.java)
        val result2 = subject.runInitializationFunction()
        verifyNoInteractions(webAuthenticationProvider)
        assertThat(result2).isInstanceOf(RedirectInitializationResult.Error::class.java)
        val errorResult = result2 as RedirectInitializationResult.Error<*>
        assertThat(errorResult.exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No initializer")
    }

    @Test fun testInitializeStartsForegroundActivity(): Unit = runBlocking {
        val webAuthenticationProvider = mock<WebAuthenticationProvider> {
            on { launch(any(), any()) } doReturn null
        }
        val launchedFromActivity = Robolectric.buildActivity(Activity::class.java).get()
        val url = "https://example.com/oauth".toHttpUrl()
        val initializerCountDownLatch = CountDownLatch(1)
        subject.initializerContinuationListeningCallback = {
            initializerCountDownLatch.countDown()
        }
        launch(Dispatchers.IO) {
            subject.initialize(webAuthenticationProvider, launchedFromActivity) {
                RedirectInitializationResult.Success(url, Any())
            }
        }
        assertThat(initializerCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        subject.runInitializationFunction()
        assertThat(subject.launchWebAuthenticationProvider(launchedFromActivity, url)).isTrue()
        verify(webAuthenticationProvider).launch(launchedFromActivity, url)
        val foregroundActivity = shadowOf(launchedFromActivity).nextStartedActivity
        assertThat(foregroundActivity.component?.className).isEqualTo(ForegroundActivity::class.java.name)
    }

    @Test fun testLaunchWebAuthenticationProvider(): Unit = runBlocking {
        val webAuthenticationProvider = mock<WebAuthenticationProvider> {
            on { launch(any(), any()) } doReturn null
        }
        val initializerCountDownLatch = CountDownLatch(1)
        subject.initializerContinuationListeningCallback = {
            initializerCountDownLatch.countDown()
        }
        launch(Dispatchers.IO) {
            subject.initialize(webAuthenticationProvider, mock()) {
                RedirectInitializationResult.Success(mock(), Any())
            }
        }
        assertThat(initializerCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        subject.runInitializationFunction()
        assertThat(subject.launchWebAuthenticationProvider(mock(), mock())).isTrue()
        verify(webAuthenticationProvider).launch(any(), any())
    }

    @Test fun testLaunchWebAuthenticationProviderActivityNotFound(): Unit = runBlocking {
        val resultDeferred = listenRedirectAsync()
        val webAuthenticationProvider = mock<WebAuthenticationProvider> {
            on { launch(any(), any()) } doReturn ActivityNotFoundException("From Test!")
        }
        val initializerCountDownLatch = CountDownLatch(1)
        subject.initializerContinuationListeningCallback = {
            initializerCountDownLatch.countDown()
        }
        launch(Dispatchers.IO) {
            subject.initialize(webAuthenticationProvider, mock()) {
                RedirectInitializationResult.Success(mock(), Any())
            }
        }
        assertThat(initializerCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        subject.runInitializationFunction()
        assertThat(subject.launchWebAuthenticationProvider(mock(), mock())).isFalse()
        verify(webAuthenticationProvider).launch(any(), any())
        val result = resultDeferred.await()
        assertThat(result).isInstanceOf(RedirectResult.Error::class.java)
        val error = result as RedirectResult.Error
        assertThat(error.exception).isInstanceOf(ActivityNotFoundException::class.java)
        assertThat(error.exception).hasMessageThat().isEqualTo("From Test!")
    }

    @Test fun testInitializeDoesNothingWhenCancelled(): Unit = runBlocking {
        val webAuthenticationProvider = mock<WebAuthenticationProvider> {
            on { launch(any(), any()) } doReturn null
        }
        val launchedFromActivity = Robolectric.buildActivity(Activity::class.java).get()
        subject.initializerContinuationListeningCallback = {
            throw IllegalStateException("Initializer callback was called.")
        }
        val startedCountDownLatch = CountDownLatch(1)
        val cancelledCountDownLatch = CountDownLatch(1)
        val job = launch(Dispatchers.IO) {
            startedCountDownLatch.countDown()
            assertThat(cancelledCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            subject.initialize(webAuthenticationProvider, launchedFromActivity) {
                RedirectInitializationResult.Success("https://example.com/oauth".toHttpUrl(), Any())
            }
        }
        assertThat(startedCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        job.cancel()
        cancelledCountDownLatch.countDown()
        verify(webAuthenticationProvider, never()).launch(any(), any())
        val foregroundActivity = shadowOf(launchedFromActivity).nextStartedActivity
        assertThat(foregroundActivity).isNull()
    }

    @Test fun testListenForRedirectDoesNothingWhenCancelled(): Unit = runBlocking {
        val startedCountDownLatch = CountDownLatch(1)
        val cancelledCountDownLatch = CountDownLatch(1)
        subject.redirectContinuationListeningCallback = {
            throw IllegalStateException("Redirect callback was called.")
        }
        val job = launch(Dispatchers.IO) {
            startedCountDownLatch.countDown()
            assertThat(cancelledCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            subject.listenForResult()
        }
        assertThat(startedCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        job.cancel()
        cancelledCountDownLatch.countDown()
        // Nothing happened due to no exception thrown above.
    }
}
