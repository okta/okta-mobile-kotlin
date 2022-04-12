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
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

internal class ForegroundViewModel : ViewModel() {
    companion object {
        // Needs to be inside the companion object due to the tests. Since this is accessed in init, we'd either need to pass it in the
        // constructor, or make it static. Since it's for tests only, making it static was the easier solution, which is why I chose
        // it.
        @VisibleForTesting var redirectCoordinator: RedirectCoordinator = SingletonRedirectCoordinator
    }

    sealed class State {
        object AwaitingInitialization : State()
        object Error : State()
        class LaunchBrowser(val url: HttpUrl) : State()
        object AwaitingBrowserCallback : State()
    }

    private val _stateLiveData = MutableLiveData<State>(State.AwaitingInitialization)
    val stateLiveData: LiveData<State> = _stateLiveData

    init {
        viewModelScope.launch {
            when (val result = redirectCoordinator.runInitializationFunction()) {
                is RedirectInitializationResult.Error -> {
                    _stateLiveData.value = State.Error
                }
                is RedirectInitializationResult.Success -> {
                    // Need to post so when we get the result back instantly, we don't resume, and cancel right away.
                    _stateLiveData.postValue(State.LaunchBrowser(result.url))
                }
            }
        }
    }

    fun launchBrowser(activity: Activity, url: HttpUrl) {
        _stateLiveData.value = State.AwaitingBrowserCallback
        if (!redirectCoordinator.launchWebAuthenticationProvider(activity, url)) {
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
