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

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Lifecycle
import com.okta.authfoundation.AuthFoundationDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

internal object SingletonRedirectCoordinator : RedirectCoordinator by DefaultRedirectCoordinator(CoroutineScope(Dispatchers.Main))

internal class DefaultRedirectCoordinator(
    private val coroutineScope: CoroutineScope,
) : RedirectCoordinator {
    @Volatile private var initializationContinuation: Continuation<RedirectInitializationResult<*>>? = null
    @VisibleForTesting var initializerContinuationListeningCallback: (() -> Unit)? = null
    @Volatile private var initializer: (suspend () -> RedirectInitializationResult<*>)? = null

    @Volatile private var redirectContinuation: Continuation<RedirectResult>? = null
    @VisibleForTesting var redirectContinuationListeningCallback: (() -> Unit)? = null

    @Volatile private var webAuthenticationProvider: WebAuthenticationProvider? = null

    private var emitErrorJob: Job? = null

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> initialize(
        webAuthenticationProvider: WebAuthenticationProvider,
        context: Context,
        initializer: suspend () -> RedirectInitializationResult<T>,
    ): RedirectInitializationResult<T> {
        if (!currentCoroutineContext().isActive) {
            reset()
        }
        currentCoroutineContext().ensureActive()

        this.webAuthenticationProvider = webAuthenticationProvider
        this.initializer = initializer

        if (context is ComponentActivity) {
            if (!context.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                return RedirectInitializationResult.Error(IllegalStateException("Activity is not resumed."))
            }
        }

        context.startActivity(ForegroundActivity.createIntent(context))

        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { reset() }
            this.initializationContinuation = continuation as Continuation<RedirectInitializationResult<*>>
            initializerContinuationListeningCallback?.invoke()
        }
    }

    override suspend fun runInitializationFunction(): RedirectInitializationResult<*> {
        val localInitializer = initializer ?: return RedirectInitializationResult.Error<Any>(IllegalStateException("No initializer"))
        initializer = null
        val result = localInitializer()
        val localContinuation = initializationContinuation
        initializationContinuation = null
        localContinuation?.resume(result)
        return result
    }

    override suspend fun listenForResult(): RedirectResult {
        if (!currentCoroutineContext().isActive) {
            reset()
        }
        currentCoroutineContext().ensureActive()

        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { reset() }
            this.redirectContinuation = continuation
            redirectContinuationListeningCallback?.invoke()
        }
    }

    override fun launchWebAuthenticationProvider(context: Context, url: HttpUrl): Boolean {
        val localWebAuthenticationProvider = webAuthenticationProvider
        webAuthenticationProvider = null

        if (localWebAuthenticationProvider != null) {
            val exception = localWebAuthenticationProvider.launch(context, url)
            if (exception == null) {
                return true
            } else {
                emitError(exception)
            }
        } else {
            emitError(IllegalStateException("RedirectListener has not been initialized."))
        }
        return false
    }

    override fun emitError(exception: Exception) {
        val result = RedirectResult.Error(exception)
        val localContinuation = redirectContinuation
        reset()
        localContinuation?.resume(result)
    }

    override fun emit(uri: Uri?) {
        emitErrorJob?.cancel()
        emitErrorJob = null
        if (uri != null) {
            val localContinuation = redirectContinuation
            reset()
            localContinuation?.resume(RedirectResult.Redirect(uri))
        } else {
            // Return a redirect error after the debounce time. In some cases, the browser can return a null redirect
            // quickly followed by a non-null redirect. In this case, we wait for loginCancellationDebounceTime before
            // accepting the error.
            emitErrorJob = coroutineScope.launch {
                delay(AuthFoundationDefaults.loginCancellationDebounceTime)
                val exception = WebAuthentication.FlowCancelledException()
                initializationContinuation?.resume(RedirectInitializationResult.Error<Any>(exception))
                val localContinuation = redirectContinuation
                reset()
                localContinuation?.resume(RedirectResult.Error(exception))
            }
        }
    }

    // Nulls all instance variables.
    private fun reset() {
        initializationContinuation = null
        initializerContinuationListeningCallback = null
        initializer = null
        redirectContinuation = null
        redirectContinuationListeningCallback = null
        webAuthenticationProvider = null
    }
}

internal interface RedirectCoordinator {
    suspend fun <T> initialize(
        webAuthenticationProvider: WebAuthenticationProvider,
        context: Context,
        initializer: suspend () -> RedirectInitializationResult<T>,
    ): RedirectInitializationResult<T>

    suspend fun runInitializationFunction(): RedirectInitializationResult<*>

    suspend fun listenForResult(): RedirectResult
    fun launchWebAuthenticationProvider(context: Context, url: HttpUrl): Boolean
    fun emitError(exception: Exception)
    fun emit(uri: Uri?)
}
