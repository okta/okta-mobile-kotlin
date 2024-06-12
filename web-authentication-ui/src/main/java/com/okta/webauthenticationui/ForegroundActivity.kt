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
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.okta.authfoundation.AuthFoundationDefaults

internal class ForegroundActivity : AppCompatActivity() {
    companion object {
        private const val ACTION_REDIRECT =
            "com.okta.webauthenticationui.ForegroundActivity.redirect"

        fun createIntent(context: Context): Intent {
            return Intent(context, ForegroundActivity::class.java)
        }

        fun redirectIntent(context: Context, uri: Uri?): Intent {
            val intent = Intent(context, ForegroundActivity::class.java)
            intent.action = ACTION_REDIRECT
            intent.data = uri
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return intent
        }
    }

    @VisibleForTesting
    val viewModel by viewModels<ForegroundViewModel>()

    private val eventCoordinator = AuthFoundationDefaults.eventCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eventCoordinator.sendEvent(ForegroundActivityEvent.OnCreate)

        handleRedirectIfAvailable(intent)
    }

    override fun onResume() {
        super.onResume()
        eventCoordinator.sendEvent(ForegroundActivityEvent.OnResume)

        viewModel.onResume(this)

        viewModel.stateLiveData.observe(this, stateObserver)
    }

    override fun onPause() {
        super.onPause()
        eventCoordinator.sendEvent(ForegroundActivityEvent.OnPause)

        // Removing the observer because we want the result delivered in onResume (where we observer), rather than onStart in the case
        // where the activity was backgrounded.
        viewModel.stateLiveData.removeObserver(stateObserver)
        if (isFinishing) {
            viewModel.flowCancelled()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        eventCoordinator.sendEvent(ForegroundActivityEvent.OnDestroy)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        eventCoordinator.sendEvent(ForegroundActivityEvent.OnNewIntent)
        handleRedirectIfAvailable(intent)
    }

    private fun handleRedirectIfAvailable(intent: Intent) {
        if (intent.action == ACTION_REDIRECT) {
            viewModel.onRedirect(intent.data)
            finish()
        }
    }

    @Suppress("OverrideDeprecatedMigration")
    override fun onBackPressed() {
        super.onBackPressed()
        eventCoordinator.sendEvent(ForegroundActivityEvent.OnBackPressed)
        viewModel.flowCancelled()
    }

    private val stateObserver = Observer<ForegroundViewModel.State> { state ->
        when (state) {
            ForegroundViewModel.State.Error -> {
                finish()
            }

            is ForegroundViewModel.State.LaunchBrowser -> {
                viewModel.launchBrowser(this, state.urlString)
            }

            ForegroundViewModel.State.AwaitingInitialization -> {
                // Idle, nothing to do.
            }

            ForegroundViewModel.State.AwaitingBrowserCallback -> {
                // Idle, nothing to do.
            }
        }
    }
}
