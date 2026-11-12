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
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Lifecycle
import com.okta.authfoundation.AuthFoundationDefaults
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

internal object SingletonRedirectCoordinator : RedirectCoordinator by DefaultRedirectCoordinator(CoroutineScope(Dispatchers.Main))

internal class DefaultRedirectCoordinator(
    private val coroutineScope: CoroutineScope,
) : RedirectCoordinator {
    @Volatile
    private var initializationContinuation: Continuation<RedirectInitializationResult<*>>? = null

    @VisibleForTesting
    var initializerContinuationListeningCallback: (() -> Unit)? = null

    @Volatile
    private var initializer: (suspend () -> RedirectInitializationResult<*>)? = null

    @Volatile
    private var redirectContinuation: CancellableContinuation<RedirectResult>? = null

    @VisibleForTesting
    var redirectContinuationListeningCallback: (() -> Unit)? = null

    @Volatile
    private var webAuthenticationProvider: WebAuthenticationProvider? = null

    @Volatile
    private var redirectUrl: String? = null

    private var emitErrorJob: Job? = null

    // A redirect that arrived while nothing was listening, e.g. because the process was killed while the
    // user was in the browser. Held so WebAuthentication.resumeLogin can finish the flow.
    @Volatile
    private var orphanedRedirect: Uri? = null

    // Makes "is anyone listening? otherwise hold the redirect" atomic with registering a listener.
    // Only ever held for a few non-suspending field accesses, never across a suspension point.
    private val stateLock = Any()

    private val flowMutex = Mutex()

    @Volatile
    private var isFlowActive = false

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> initialize(
        webAuthenticationProvider: WebAuthenticationProvider,
        context: Context,
        redirectUrl: String,
        initializer: suspend () -> RedirectInitializationResult<T>,
    ): RedirectInitializationResult<T> {
        currentCoroutineContext().ensureActive()
        flowMutex.withLock {
            orphanedRedirect = null
            if (isFlowActive) {
                return RedirectInitializationResult.Error(WebAuthentication.FlowAlreadyInProgressException())
            }
            isFlowActive = true
            this.webAuthenticationProvider = webAuthenticationProvider
            this.redirectUrl = redirectUrl
            this.initializer = initializer
        }

        if (context is ComponentActivity) {
            if (!context.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                reset()
                return RedirectInitializationResult.Error(IllegalStateException("Activity is not resumed."))
            }
        }

        val intent = ForegroundActivity.createIntent(context)
        if (context !is Activity) {
            // A non-Activity context (e.g. the application context) has no task of its own to
            // launch into, so ForegroundActivity must be started in a new task.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

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
            val orphaned =
                synchronized(stateLock) {
                    orphanedRedirect.also {
                        if (it != null) {
                            // No new flow can have started since the redirect arrived: initialize() discards orphans.
                            orphanedRedirect = null
                            resetLocked()
                        } else {
                            redirectContinuation = continuation
                        }
                    }
                }
            if (orphaned != null) {
                continuation.resume(RedirectResult.Redirect(orphaned))
            } else {
                // Only tear down state this listener owns; another flow may have registered since.
                continuation.invokeOnCancellation {
                    synchronized(stateLock) { if (redirectContinuation === continuation) resetLocked() }
                }
                redirectContinuationListeningCallback?.invoke()
            }
        }
    }

    override suspend fun listenForResumedResult(): RedirectResult? {
        flowMutex.withLock {
            if (isFlowActive) return null
            isFlowActive = true
        }
        return listenForResult()
    }

    override fun launchWebAuthenticationProvider(
        context: Context,
        url: HttpUrl,
        authTabLauncher: ActivityResultLauncher<Intent>,
    ): Boolean {
        val localWebAuthenticationProvider = webAuthenticationProvider
        val localRedirectUrl = redirectUrl
        webAuthenticationProvider = null
        redirectUrl = null

        if (localWebAuthenticationProvider != null && localRedirectUrl != null) {
            val result =
                if (localWebAuthenticationProvider is AuthTabWebAuthenticationProvider) {
                    localWebAuthenticationProvider.launchAuthTab(context, url, localRedirectUrl, authTabLauncher)
                } else {
                    localWebAuthenticationProvider.launchCustomTab(context, url)
                }
            result
                .onSuccess {
                    return true
                }.onFailure { emitError(it as? Exception ?: Exception(it)) }
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
            val localContinuation =
                synchronized(stateLock) {
                    redirectContinuation.also {
                        reset()
                        if (it == null) orphanedRedirect = uri
                    }
                }
            // If the listener is cancelled after being resumed, the redirect was consumed by nobody: hold it again.
            localContinuation?.resume(RedirectResult.Redirect(uri)) { orphanedRedirect = uri }
        } else if (orphanedRedirect == null) {
            // Return a redirect error after the debounce time. In some cases, the browser can return a null redirect
            // quickly followed by a non-null redirect. In this case, we wait for loginCancellationDebounceTime before
            // accepting the error.
            emitErrorJob =
                coroutineScope.launch {
                    delay(AuthFoundationDefaults.loginCancellationDebounceTime)
                    if (orphanedRedirect != null) return@launch
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
        synchronized(stateLock) { resetLocked() }
    }

    private fun resetLocked() {
        isFlowActive = false
        initializationContinuation = null
        initializerContinuationListeningCallback = null
        initializer = null
        redirectContinuation = null
        redirectContinuationListeningCallback = null
        webAuthenticationProvider = null
        redirectUrl = null
    }
}

internal interface RedirectCoordinator {
    suspend fun <T> initialize(
        webAuthenticationProvider: WebAuthenticationProvider,
        context: Context,
        redirectUrl: String,
        initializer: suspend () -> RedirectInitializationResult<T>,
    ): RedirectInitializationResult<T>

    suspend fun runInitializationFunction(): RedirectInitializationResult<*>

    suspend fun listenForResult(): RedirectResult

    /**
     * Like [listenForResult], but for a login interrupted by process death. Claims the coordinator
     * so no other flow can run concurrently. Returns `null`, without waiting, if a flow is already
     * running, since that flow owns the pending redirect.
     */
    suspend fun listenForResumedResult(): RedirectResult?

    fun launchWebAuthenticationProvider(
        context: Context,
        url: HttpUrl,
        authTabLauncher: ActivityResultLauncher<Intent>,
    ): Boolean

    fun emitError(exception: Exception)

    fun emit(uri: Uri?)
}
