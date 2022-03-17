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
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class RedirectCoordinatorTest {
    private lateinit var subject: DefaultRedirectCoordinator

    @Before fun setup() {
        subject = DefaultRedirectCoordinator()
    }

    private fun CoroutineScope.listenAsync(): Deferred<RedirectResult> {
        val countDownLatch = CountDownLatch(1)
        subject.listeningCallback = {
            countDownLatch.countDown()
        }
        val resultDeferred = async(Dispatchers.IO) { subject.listenForResult() }
        assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        return resultDeferred
    }

    @Test fun testEmitError(): Unit = runBlocking {
        val resultDeferred = listenAsync()
        subject.emitError(IllegalStateException("From Test!"))
        val result = resultDeferred.await()
        assertThat(result).isInstanceOf(RedirectResult.Error::class.java)
        val error = result as RedirectResult.Error
        assertThat(error.exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(error.exception).hasMessageThat().isEqualTo("From Test!")
    }

    @Test fun testEmit(): Unit = runBlocking {
        val resultDeferred = listenAsync()
        subject.emit(Uri.parse("unitTest:/hello"))
        val result = resultDeferred.await()
        assertThat(result).isInstanceOf(RedirectResult.Redirect::class.java)
        val redirect = result as RedirectResult.Redirect
        assertThat(redirect.uri).isEqualTo(Uri.parse("unitTest:/hello"))
    }

    @Test fun testEmitNullIsCancel(): Unit = runBlocking {
        val resultDeferred = listenAsync()
        subject.emit(null)
        val result = resultDeferred.await()
        assertThat(result).isInstanceOf(RedirectResult.Error::class.java)
        val error = result as RedirectResult.Error
        assertThat(error.exception).isInstanceOf(WebAuthenticationClient.FlowCancelledException::class.java)
    }

    @Test fun testEmitWithoutListening(): Unit = runBlocking {
        subject.emit(null)
    }

    @Test fun testEmitErrorWithoutListening(): Unit = runBlocking {
        subject.emitError(IllegalStateException("From Test!"))
    }

    @Test fun testLaunchWebAuthenticationProviderWithoutInitialize(): Unit = runBlocking {
        val resultDeferred = listenAsync()
        assertThat(subject.launchWebAuthenticationProvider(mock(), "https://example.com".toHttpUrl())).isFalse()
        val result = resultDeferred.await()
        assertThat(result).isInstanceOf(RedirectResult.Error::class.java)
        val error = result as RedirectResult.Error
        assertThat(error.exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(error.exception).hasMessageThat().isEqualTo("RedirectListener has not been initialized.")
    }

    @Test fun testLaunchWebAuthenticationProvider(): Unit = runBlocking {
        val webAuthenticationProvider = mock<WebAuthenticationProvider> {
            on { launch(any(), any()) } doReturn null
        }
        subject.initialize(webAuthenticationProvider)
        assertThat(subject.launchWebAuthenticationProvider(mock(), mock())).isTrue()
        verify(webAuthenticationProvider).launch(any(), any())
    }

    @Test fun testLaunchWebAuthenticationProviderActivityNotFound(): Unit = runBlocking {
        val resultDeferred = listenAsync()
        val webAuthenticationProvider = mock<WebAuthenticationProvider> {
            on { launch(any(), any()) } doReturn ActivityNotFoundException("From Test!")
        }
        subject.initialize(webAuthenticationProvider)
        assertThat(subject.launchWebAuthenticationProvider(mock(), mock())).isFalse()
        verify(webAuthenticationProvider).launch(any(), any())
        val result = resultDeferred.await()
        assertThat(result).isInstanceOf(RedirectResult.Error::class.java)
        val error = result as RedirectResult.Error
        assertThat(error.exception).isInstanceOf(ActivityNotFoundException::class.java)
        assertThat(error.exception).hasMessageThat().isEqualTo("From Test!")
    }
}
