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
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundationbootstrap.CredentialBootstrap
import com.okta.webauthenticationui.WebAuthenticationClient.Companion.createWebAuthenticationClient
import kotlinx.coroutines.launch
import sample.okta.android.BuildConfig
import timber.log.Timber

class BrowserViewModel : ViewModel() {
    private val _state = MutableLiveData<BrowserState>(BrowserState.Idle)
    val state: LiveData<BrowserState> = _state

    fun login(context: Context, addDeviceSsoScope: Boolean) {
        viewModelScope.launch {
            _state.value = BrowserState.Loading

            val credential = CredentialBootstrap.defaultCredential()
            val webAuthenticationClient = CredentialBootstrap.oidcClient.createWebAuthenticationClient()
            var scopes = credential.scopes()
            if (addDeviceSsoScope) {
                scopes = scopes + setOf("device_sso")
            }

            when (
                val result = webAuthenticationClient.login(
                    context = context,
                    redirectUrl = BuildConfig.SIGN_IN_REDIRECT_URI,
                    extraRequestParameters = emptyMap(),
                    scopes = scopes,
                )
            ) {
                is OidcClientResult.Error -> {
                    Timber.e(result.exception, "Failed to start login flow.")
                    _state.value = BrowserState.Error("Failed to start login flow.")
                }
                is OidcClientResult.Success -> {
                    credential.storeToken(token = result.result)
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
