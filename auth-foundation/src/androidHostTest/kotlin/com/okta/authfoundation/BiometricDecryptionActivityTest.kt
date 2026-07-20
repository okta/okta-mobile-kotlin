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
package com.okta.authfoundation

import androidx.biometric.BiometricPrompt
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class BiometricDecryptionActivityTest {
    @After
    fun tearDown() = unmockkAll()

    /**
     * Regression test: onAuthenticationFailed() previously called resumeWithException()
     * which propagated a fatal exception on the main thread. Verify it is now a no-op for
     * the coroutine — the prompt stays open for retry.
     */
    @Test
    fun onAuthenticationFailed_DoesNotResumeCoroutine_PromptRemainsOpen() {
        val continuationResumed = AtomicBoolean(false)
        val callback = buildCallbackWithContinuation { continuationResumed.set(true) }

        callback.onAuthenticationFailed()

        assertFalse(continuationResumed.get())
    }

    @Test
    fun onAuthenticationFailed_ThenSucceeded_CompletesSuccessfully() {
        val result = AtomicReference<Result<Unit>>()
        val callback = buildCallbackWithContinuation { result.set(it) }

        callback.onAuthenticationFailed()
        callback.onAuthenticationSucceeded(mockk(relaxed = true))

        assertTrue(assertNotNull(result.get()).isSuccess)
    }

    @Test
    fun onAuthenticationFailed_ThenError_TerminatesWithBiometricException() {
        val result = AtomicReference<Result<Unit>>()
        val callback = buildCallbackWithContinuation { result.set(it) }

        callback.onAuthenticationFailed()
        callback.onAuthenticationError(BiometricPrompt.ERROR_USER_CANCELED, "Cancelled")

        val r = assertNotNull(result.get())
        assertTrue(r.isFailure)
        val ex = assertIs<BiometricAuthenticationException>(r.exceptionOrNull())
        assertIs<BiometricExceptionDetails.OnAuthenticationError>(ex.biometricExceptionDetails)
    }

    private fun buildCallbackWithContinuation(onResume: (Result<Unit>) -> Unit): BiometricPrompt.AuthenticationCallback {
        val continuation =
            object : Continuation<Unit> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<Unit>) = onResume(result)
            }

        setStaticBiometricAction(continuation)

        return BiometricDecryptionActivity.createAuthenticationCallback(finish = {})
    }

    private fun setStaticBiometricAction(continuation: Continuation<Unit>) {
        val unlockInstance =
            Class
                .forName("com.okta.authfoundation.BiometricDecryptionActivity\$BiometricAction\$Unlock")
                .declaredConstructors[0]
                .also { it.isAccessible = true }
                .newInstance(continuation)

        BiometricDecryptionActivity::class.java
            .getDeclaredField("biometricAction")
            .also { it.isAccessible = true }
            .set(null, unlockInstance)
    }
}
