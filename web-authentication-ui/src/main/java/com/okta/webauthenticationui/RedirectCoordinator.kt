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
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

internal object SingletonRedirectCoordinator : RedirectCoordinator by DefaultRedirectCoordinator()

internal class DefaultRedirectCoordinator : RedirectCoordinator {
    @Volatile private var initializationContinuation: Continuation<RedirectInitializationResult<*>>? = null
    @VisibleForTesting var initializerContinuationListeningCallback: (() -> Unit)? = null
    @Volatile private var initializer: (suspend () -> RedirectInitializationResult<*>)? = null

    @Volatile private var redirectContinuation: Continuation<RedirectResult>? = null
    @VisibleForTesting var redirectContinuationListeningCallback: (() -> Unit)? = null

    @Volatile private var webAuthenticationProvider: WebAuthenticationProvider? = null

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
        val result = if (uri != null) {
            RedirectResult.Redirect(uri)
        } else {
            val exception = WebAuthenticationClient.FlowCancelledException()
            initializationContinuation?.resume(RedirectInitializationResult.Error<Any>(exception))
            RedirectResult.Error(exception)
        }
        val localContinuation = redirectContinuation
        reset()
        localContinuation?.resume(result)
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
