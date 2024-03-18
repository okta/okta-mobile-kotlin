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
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.AuthFoundationDefaults
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
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
import kotlin.time.Duration.Companion.seconds

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class RedirectCoordinatorTest {
    private val testDebounceTime = 15.seconds

    private lateinit var testScope: TestScope
    private lateinit var subject: DefaultRedirectCoordinator

    @Before fun setup() {
        mockkObject(AuthFoundationDefaults)
        every { AuthFoundationDefaults.loginCancellationDebounceTime } returns testDebounceTime
        testScope = TestScope()
        subject = DefaultRedirectCoordinator(testScope)
    }

    @After fun cleanup() {
        unmockkAll()
    }

    private fun TestScope.listenRedirectAsync(): Deferred<RedirectResult> {
        val countDownLatch = CountDownLatch(1)
        subject.redirectContinuationListeningCallback = {
            countDownLatch.countDown()
        }
        val resultDeferred = async(Dispatchers.IO) { subject.listenForResult() }
        assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        return resultDeferred
    }

    @Test fun testEmitError(): Unit = testScope.runTest {
        val startTime = currentTime
        val resultDeferred = listenRedirectAsync()
        subject.emitError(IllegalStateException("From Test!"))
        val result = resultDeferred.await()
        val elapsedTime = currentTime - startTime
        assertThat(result).isInstanceOf(RedirectResult.Error::class.java)
        val error = result as RedirectResult.Error
        assertThat(error.exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(error.exception).hasMessageThat().isEqualTo("From Test!")
        assertThat(elapsedTime).isEqualTo(0)
    }

    @Test fun testEmit(): Unit = testScope.runTest {
        val startTime = currentTime
        val resultDeferred = listenRedirectAsync()
        subject.emit(Uri.parse("unitTest:/hello"))
        val result = resultDeferred.await()
        val elapsedTime = currentTime - startTime
        assertThat(result).isInstanceOf(RedirectResult.Redirect::class.java)
        val redirect = result as RedirectResult.Redirect
        assertThat(redirect.uri).isEqualTo(Uri.parse("unitTest:/hello"))
        assertThat(elapsedTime).isEqualTo(0)
    }

    @Test fun testEmitNullIsCancel(): Unit = testScope.runTest {
        val startTime = currentTime
        val resultDeferred = listenRedirectAsync()
        subject.emit(null)
        val result = resultDeferred.await()
        val elapsedTime = currentTime - startTime
        assertThat(result).isInstanceOf(RedirectResult.Error::class.java)
        val error = result as RedirectResult.Error
        assertThat(error.exception).isInstanceOf(WebAuthentication.FlowCancelledException::class.java)
        assertThat(elapsedTime).isEqualTo(testDebounceTime.inWholeMilliseconds)
    }

    @Test fun testEmitNullIsReplacedOnSecondEmit(): Unit = testScope.runTest {
        val startTime = currentTime
        val resultDeferred = listenRedirectAsync()
        subject.emit(null)
        advanceTimeBy(testDebounceTime / 2)
        runCurrent()
        subject.emit(Uri.parse("unitTest:/hello"))
        val result = resultDeferred.await()
        val elapsedTime = currentTime - startTime
        assertThat(result).isInstanceOf(RedirectResult.Redirect::class.java)
        val redirect = result as RedirectResult.Redirect
        assertThat(redirect.uri).isEqualTo(Uri.parse("unitTest:/hello"))
        assertThat(elapsedTime).isEqualTo((testDebounceTime / 2).inWholeMilliseconds)
    }

    @Test fun testEmitNullIsCancelAfterDebounceTime(): Unit = testScope.runTest {
        val startTime = currentTime
        val resultDeferred = listenRedirectAsync()
        subject.emit(null)
        advanceTimeBy(testDebounceTime)
        runCurrent()
        subject.emit(Uri.parse("unitTest:/hello"))
        val result = resultDeferred.await()
        val elapsedTime = currentTime - startTime
        assertThat(result).isInstanceOf(RedirectResult.Error::class.java)
        val error = result as RedirectResult.Error
        assertThat(error.exception).isInstanceOf(WebAuthentication.FlowCancelledException::class.java)
        assertThat(elapsedTime).isEqualTo(testDebounceTime.inWholeMilliseconds)
    }

    @Test fun testEmitNullCancelsInitialization(): Unit = testScope.runTest {
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
        assertThat(error.exception).isInstanceOf(WebAuthentication.FlowCancelledException::class.java)
    }

    @Test fun testEmitWithoutListening(): Unit = testScope.runTest {
        subject.emit(null)
        advanceUntilIdle()
    }

    @Test fun testEmitErrorWithoutListening(): Unit = testScope.runTest {
        subject.emitError(IllegalStateException("From Test!"))
        advanceUntilIdle()
    }

    @Test fun testLaunchWebAuthenticationProviderWithoutInitialize(): Unit = testScope.runTest {
        val resultDeferred = listenRedirectAsync()
        assertThat(subject.launchWebAuthenticationProvider(mock(), "https://example.com".toHttpUrl())).isFalse()
        val result = resultDeferred.await()
        assertThat(result).isInstanceOf(RedirectResult.Error::class.java)
        val error = result as RedirectResult.Error
        assertThat(error.exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(error.exception).hasMessageThat().isEqualTo("RedirectListener has not been initialized.")
    }

    @Test fun testRunInitializationFunctionReturnsErrorIfInitializeIsNotCalled(): Unit = testScope.runTest {
        val webAuthenticationProvider = mock<WebAuthenticationProvider>()
        val result = subject.runInitializationFunction()
        verifyNoInteractions(webAuthenticationProvider)
        assertThat(result).isInstanceOf(RedirectInitializationResult.Error::class.java)
        val errorResult = result as RedirectInitializationResult.Error<*>
        assertThat(errorResult.exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("No initializer")
    }

    @Test fun testRunInitializationFunctionClearsInitializer(): Unit = testScope.runTest {
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

    @Test fun testInitializeStartsForegroundActivity(): Unit = testScope.runTest {
        val webAuthenticationProvider = mock<WebAuthenticationProvider> {
            on { launch(any(), any()) } doReturn null
        }
        val activityController = Robolectric.buildActivity(ComponentActivity::class.java)
        activityController.create().start().resume()
        val launchedFromActivity = activityController.get()
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

    @Test fun testInitializeDoesNotStartsForegroundActivityWhenBackgrounded(): Unit = testScope.runTest {
        val webAuthenticationProvider = mock<WebAuthenticationProvider>()
        val activityController = Robolectric.buildActivity(ComponentActivity::class.java)
        activityController.create().start().resume().pause().stop()
        val launchedFromActivity = activityController.get()
        val result = subject.initialize(webAuthenticationProvider, launchedFromActivity) {
            RedirectInitializationResult.Success("https://example.com/oauth".toHttpUrl(), Any())
        }
        verify(webAuthenticationProvider, never()).launch(anyOrNull(), anyOrNull())
        assertThat(result).isInstanceOf(RedirectInitializationResult.Error::class.java)
        val errorResult = result as RedirectInitializationResult.Error
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("Activity is not resumed.")
    }

    @Test fun testLaunchWebAuthenticationProvider(): Unit = testScope.runTest {
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

    @Test fun testLaunchWebAuthenticationProviderActivityNotFound(): Unit = testScope.runTest {
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

    @Test fun testInitializeDoesNothingWhenCancelled(): Unit = testScope.runTest {
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

    @Test fun testListenForRedirectDoesNothingWhenCancelled(): Unit = testScope.runTest {
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
