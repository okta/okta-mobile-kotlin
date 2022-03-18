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
import okhttp3.HttpUrl
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal object SingletonRedirectCoordinator : RedirectCoordinator by DefaultRedirectCoordinator()

internal class DefaultRedirectCoordinator : RedirectCoordinator {
    @Volatile private var continuation: Continuation<RedirectResult>? = null
    @Volatile private var webAuthenticationProvider: WebAuthenticationProvider? = null

    @VisibleForTesting var listeningCallback: (() -> Unit)? = null

    override suspend fun listenForResult(): RedirectResult {
        return suspendCoroutine { continuation ->
            this.continuation = continuation
            listeningCallback?.invoke()
        }
    }

    override fun initialize(webAuthenticationProvider: WebAuthenticationProvider, context: Context, url: HttpUrl) {
        this.webAuthenticationProvider = webAuthenticationProvider
        context.startActivity(ForegroundActivity.createIntent(context, url))
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
        val localContinuation = continuation
        continuation = null
        localContinuation?.resume(result)
    }

    override fun emit(uri: Uri?) {
        val result = if (uri != null) {
            RedirectResult.Redirect(uri)
        } else {
            RedirectResult.Error(WebAuthenticationClient.FlowCancelledException())
        }
        val localContinuation = continuation
        continuation = null
        localContinuation?.resume(result)
    }
}

internal interface RedirectCoordinator {
    suspend fun listenForResult(): RedirectResult
    fun initialize(webAuthenticationProvider: WebAuthenticationProvider, context: Context, url: HttpUrl)
    fun launchWebAuthenticationProvider(context: Context, url: HttpUrl): Boolean
    fun emitError(exception: Exception)
    fun emit(uri: Uri?)
}
