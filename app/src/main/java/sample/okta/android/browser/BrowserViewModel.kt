/*
 * Copyright 2021-Present Okta, Inc.
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
package sample.okta.android.browser

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.credential.Credential
import com.okta.webauthenticationui.WebAuthentication
import kotlinx.coroutines.launch
import sample.okta.android.BuildConfig
import sample.okta.android.SampleHelper
import timber.log.Timber

class BrowserViewModel : ViewModel() {
    private val _state = MutableLiveData<BrowserState>(BrowserState.Idle)
    val state: LiveData<BrowserState> = _state

    fun login(context: Context, addDeviceSsoScope: Boolean) {
        viewModelScope.launch {
            _state.value = BrowserState.Loading

            val webAuthentication = WebAuthentication()
            var scope = SampleHelper.DEFAULT_SCOPE
            if (addDeviceSsoScope) {
                scope += " device_sso"
            }

            when (
                val result = webAuthentication.login(
                    context = context,
                    redirectUrl = BuildConfig.SIGN_IN_REDIRECT_URI,
                    extraRequestParameters = emptyMap(),
                    scope = scope,
                )
            ) {
                is OAuth2ClientResult.Error -> {
                    Timber.e(result.exception, "Failed to start login flow.")
                    _state.value = BrowserState.Error("Failed to start login flow.")
                }
                is OAuth2ClientResult.Success -> {
                    val credential = Credential.store(result.result)
                    Credential.setDefaultAsync(credential)
                    _state.value = BrowserState.Token
                }
            }
        }
    }
}

sealed class BrowserState {
    object Idle : BrowserState()
    object Loading : BrowserState()
    data class Error(val message: String) : BrowserState()
    object Token : BrowserState()
}
