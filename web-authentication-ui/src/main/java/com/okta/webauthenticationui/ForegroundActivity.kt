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
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal class ForegroundActivity : Activity() {
    companion object {
        private const val EXTRA_URL = "com.okta.webauthenticationui.ForegroundActivity.url"

        private const val ACTION_REDIRECT = "com.okta.webauthenticationui.ForegroundActivity.redirect"

        private const val SAVED_STATE_HAS_STARTED = "com.okta.webauthenticationui.ForegroundActivity.hasStarted"

        fun createIntent(context: Context, url: HttpUrl): Intent {
            val intent = Intent(context, ForegroundActivity::class.java)
            intent.putExtra(EXTRA_URL, url.toString())
            return intent
        }

        fun redirectIntent(context: Context, uri: Uri?): Intent {
            val intent = Intent(context, ForegroundActivity::class.java)
            intent.action = ACTION_REDIRECT
            intent.data = uri
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return intent
        }
    }

    private var hasStarted: Boolean = false

    @VisibleForTesting var redirectCoordinator: RedirectCoordinator = SingletonRedirectCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            hasStarted = savedInstanceState.getBoolean(SAVED_STATE_HAS_STARTED, hasStarted)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SAVED_STATE_HAS_STARTED, hasStarted)
    }

    override fun onResume() {
        super.onResume()

        if (hasStarted) {
            redirectCoordinator.emit(null)
            finish()
        } else {
            val url = intent.getStringExtra(EXTRA_URL)?.toHttpUrlOrNull()
            if (url != null) {
                if (!redirectCoordinator.launchWebAuthenticationProvider(this, url)) {
                    finish()
                }
                hasStarted = true
            } else {
                redirectCoordinator.emitError(IllegalStateException("Url not provided when launching ForegroundActivity."))
                finish()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRedirectIfAvailable(intent)
    }

    private fun handleRedirectIfAvailable(intent: Intent) {
        if (intent.action == ACTION_REDIRECT) {
            redirectCoordinator.emit(intent.data)
            finish()
        }
    }
}
