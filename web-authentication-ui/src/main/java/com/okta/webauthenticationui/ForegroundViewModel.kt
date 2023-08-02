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
import android.net.Uri
import android.os.Parcelable
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import okhttp3.HttpUrl.Companion.toHttpUrl

internal class ForegroundViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    companion object {
        // Needs to be inside the companion object due to the tests. Since this is accessed in init, we'd either need to pass it in the
        // constructor, or make it static. Since it's for tests only, making it static was the easier solution, which is why I chose
        // it.
        @VisibleForTesting var redirectCoordinator: RedirectCoordinator = SingletonRedirectCoordinator
        private const val LIVE_DATA_KEY = "FOREGROUND_STATE_LIVE_DATA_KEY"
    }

    @Parcelize
    sealed class State : Parcelable {
        @Parcelize
        object AwaitingInitialization : State()
        @Parcelize
        object Error : State()
        @Parcelize
        class LaunchBrowser(val urlString: String) : State()
        @Parcelize
        object AwaitingBrowserCallback : State()
    }

    val stateLiveData: LiveData<State> = savedStateHandle.getLiveData(LIVE_DATA_KEY, State.AwaitingInitialization)

    init {
        viewModelScope.launch {
            if (stateLiveData.value == State.AwaitingInitialization) {
                when (val result = redirectCoordinator.runInitializationFunction()) {
                    is RedirectInitializationResult.Error -> {
                        savedStateHandle[LIVE_DATA_KEY] = State.Error
                    }

                    is RedirectInitializationResult.Success -> {
                        savedStateHandle[LIVE_DATA_KEY] = State.LaunchBrowser(result.url.toString())
                    }
                }
            }
        }
    }

    fun launchBrowser(activity: Activity, urlString: String) {
        savedStateHandle[LIVE_DATA_KEY] = State.AwaitingBrowserCallback
        if (!redirectCoordinator.launchWebAuthenticationProvider(activity, urlString.toHttpUrl())) {
            activity.finish()
        }
    }

    fun onResume(activity: Activity) {
        if (stateLiveData.value == State.AwaitingBrowserCallback) {
            redirectCoordinator.emit(null)
            activity.finish()
        }
    }

    fun onRedirect(data: Uri?) {
        redirectCoordinator.emit(data)
    }

    fun flowCancelled() {
        redirectCoordinator.emit(null)
    }
}
