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
package sample.okta.android.legacy.legacybrowser

import android.app.Activity
import androidx.annotation.NonNull
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.oidc.AuthorizationStatus
import com.okta.oidc.ResultCallback
import com.okta.oidc.util.AuthorizationException
import kotlinx.coroutines.launch
import sample.okta.android.legacy.SampleWebAuthClientHelper
import timber.log.Timber

internal class LegacyBrowserViewModel : ViewModel() {
    private val _state = MutableLiveData<BrowserState>(BrowserState.Idle)
    val state: LiveData<BrowserState> = _state

    fun registerCallback(activity: Activity) {
        val webClient = SampleWebAuthClientHelper.webAuthClient

        webClient.registerCallback(
            object : ResultCallback<AuthorizationStatus, AuthorizationException> {
                override fun onSuccess(@NonNull status: AuthorizationStatus) {
                    if (status == AuthorizationStatus.AUTHORIZED) {
                        _state.value = BrowserState.Token
                    } else {
                        _state.value = BrowserState.Error("An error occurred.")
                    }
                }

                override fun onCancel() {
                    _state.value = BrowserState.Error("Login was cancelled.")
                }

                override fun onError(@NonNull msg: String?, error: AuthorizationException?) {
                    Timber.e(error, "Failed to login.")
                    _state.value = BrowserState.Error(msg ?: "An error occurred.")
                }
            },
            activity,
        )
    }

    fun login(activity: Activity) {
        viewModelScope.launch {
            _state.value = BrowserState.Loading

            SampleWebAuthClientHelper.webAuthClient.signIn(activity, null)
        }
    }
}

sealed class BrowserState {
    object Idle : BrowserState()
    object Loading : BrowserState()
    data class Error(val message: String) : BrowserState()
    object Token : BrowserState()
}
