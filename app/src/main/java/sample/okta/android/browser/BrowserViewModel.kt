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
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.oauth2.AuthorizationCodeFlow
import com.okta.webauthenticationui.WebAuthenticationClient
import com.okta.webauthenticationui.WebAuthenticationClient.Companion.webAuthenticationClient
import kotlinx.coroutines.launch
import sample.okta.android.OktaHelper
import sample.okta.android.SocialRedirectCoordinator
import timber.log.Timber

class BrowserViewModel : ViewModel() {
    private var authorizationCodeFlowContext: AuthorizationCodeFlow.ResumeResult.Context? = null

    private val _state = MutableLiveData<BrowserState>(BrowserState.Idle)
    val state: LiveData<BrowserState> = _state

    init {
        SocialRedirectCoordinator.listeners += ::handleRedirect
    }

    override fun onCleared() {
        SocialRedirectCoordinator.listeners -= ::handleRedirect
    }

    fun login(context: Context, addDeviceSsoScope: Boolean) {
        val webAuthenticationClient = OktaHelper.defaultCredential.oidcClient.webAuthenticationClient()
        var scopes = OktaHelper.defaultCredential.scopes()
        if (addDeviceSsoScope) {
            scopes = scopes + setOf("device_sso")
        }
        viewModelScope.launch {
            when (val result = webAuthenticationClient.login(context, scopes)) {
                is WebAuthenticationClient.LoginResult.Error -> {
                    Timber.e(result.exception, "Failed to start login flow.")
                    _state.value = BrowserState.Error("Failed to start login flow.")
                }
                is WebAuthenticationClient.LoginResult.Success -> {
                    authorizationCodeFlowContext = result.flowContext
                }
                WebAuthenticationClient.LoginResult.EndpointsNotAvailable -> {
                    Timber.e("Failed to start login flow. Check OidcClient configuration.")
                    _state.value = BrowserState.Error("Failed to start login flow. Check OidcClient configuration.")
                }
            }
        }
    }

    fun handleRedirect(uri: Uri) {
        viewModelScope.launch {
            when (val result =
                OktaHelper.defaultCredential.oidcClient.webAuthenticationClient().resume(uri, authorizationCodeFlowContext!!)) {
                is AuthorizationCodeFlow.Result.Error -> {
                    _state.value = BrowserState.Error(result.message)
                }
                AuthorizationCodeFlow.Result.MissingResultCode -> {
                    _state.value = BrowserState.Error("Invalid redirect. Missing result code.")
                }
                AuthorizationCodeFlow.Result.RedirectSchemeMismatch -> {
                    _state.value = BrowserState.Error("Invalid redirect. Redirect scheme mismatch.")
                }
                is AuthorizationCodeFlow.Result.Token -> {
                    _state.value = BrowserState.Token
                }
            }
        }
    }
}

sealed class BrowserState {
    object Idle : BrowserState()
    data class Error(val message: String) : BrowserState()
    object Token : BrowserState()
}
