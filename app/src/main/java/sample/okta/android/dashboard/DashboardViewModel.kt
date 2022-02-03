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
package sample.okta.android.dashboard

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.credential.Credential
import com.okta.authfoundation.credential.TokenType
import com.okta.oauth2.RedirectEndSessionFlow
import com.okta.webauthenticationui.WebAuthenticationClient.Companion.webAuthenticationClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sample.okta.android.DefaultCredential
import sample.okta.android.SocialRedirectCoordinator
import timber.log.Timber

internal class DashboardViewModel : ViewModel() {
    private val _requestStateLiveData = MutableLiveData<RequestState>(RequestState.Result(""))
    val requestStateLiveData: LiveData<RequestState> = _requestStateLiveData

    private val _userInfoLiveData = MutableLiveData<Map<String, String>>(emptyMap())
    val userInfoLiveData: LiveData<Map<String, String>> = _userInfoLiveData

    private var logoutFlowContext: RedirectEndSessionFlow.Context? = null

    var lastButtonId: Int = 0
    private var lastRequestJob: Job? = null

    init {
        SocialRedirectCoordinator.listeners += ::handleRedirect

        viewModelScope.launch {
            getUserInfo()
        }
    }

    override fun onCleared() {
        super.onCleared()
        SocialRedirectCoordinator.listeners -= ::handleRedirect
    }

    fun revoke(buttonId: Int, tokenType: TokenType) {
        performRequest(buttonId) { credential ->
            when (credential.revokeToken(tokenType)) {
                is OidcClientResult.Error -> {
                    RequestState.Result("Failed to revoke token.")
                }
                is OidcClientResult.Success -> {
                    RequestState.Result("Token Revoked.")
                }
            }
        }
    }

    fun refresh(buttonId: Int) {
        performRequest(buttonId) { credential ->
            when (credential.refreshToken()) {
                is OidcClientResult.Error -> {
                    RequestState.Result("Failed to refresh token.")
                }
                is OidcClientResult.Success -> {
                    RequestState.Result("Token Refreshed.")
                }
            }
        }
    }

    fun introspect(buttonId: Int, tokenType: TokenType) {
        performRequest(buttonId) { credential ->
            when (val result = credential.introspectToken(tokenType)) {
                is OidcClientResult.Error -> {
                    RequestState.Result("Failed to introspect token.")
                }
                is OidcClientResult.Success -> {
                    RequestState.Result(result.result.asMap().displayableKeyValues())
                }
            }
        }
    }

    fun logoutOfWeb(context: Context) {
        viewModelScope.launch {
            val idToken = DefaultCredential.get().token?.idToken ?: return@launch
            logoutFlowContext = DefaultCredential.get().oidcClient.webAuthenticationClient().logout(context, idToken)
        }
    }

    private fun performRequest(buttonId: Int, performer: suspend (Credential) -> RequestState) {
        if (lastRequestJob?.isActive == true) {
            // Re-enable the button, so it's not permanently disabled.
            _requestStateLiveData.value = RequestState.Result("")
        }
        lastRequestJob?.cancel()
        lastButtonId = buttonId

        val credential = DefaultCredential.get()
        _requestStateLiveData.value = RequestState.Loading

        lastRequestJob = viewModelScope.launch {
            _requestStateLiveData.postValue(performer(credential))
        }
    }

    private fun Map<String, String>.displayableKeyValues(): String {
        var result = ""
        for (entry in this) {
            result += entry.key + ": " + entry.value + "\n"
        }
        return result
    }

    private suspend fun getUserInfo() {
        when (val userInfoResult = DefaultCredential.get().getUserInfo()) {
            is OidcClientResult.Error -> {
                Timber.e(userInfoResult.exception, "Failed to fetch user info.")
                _userInfoLiveData.postValue(emptyMap())
            }
            is OidcClientResult.Success -> {
                _userInfoLiveData.postValue(userInfoResult.result.asMap())
            }
        }
    }

    sealed class RequestState {
        object Loading : RequestState()
        data class Result(val text: String) : RequestState()
    }

    fun handleRedirect(uri: Uri) {
        viewModelScope.launch {
            when (val result = DefaultCredential.get().oidcClient.webAuthenticationClient().resume(uri, logoutFlowContext!!)) {
                is RedirectEndSessionFlow.Result.Error -> {
                    _requestStateLiveData.value = RequestState.Result(result.message)
                }
                RedirectEndSessionFlow.Result.RedirectSchemeMismatch -> {
                    _requestStateLiveData.value = RequestState.Result("Invalid redirect. Redirect scheme mismatch.")
                }
                is RedirectEndSessionFlow.Result.Success -> {
                    DefaultCredential.get().remove()
                    _requestStateLiveData.value = RequestState.Result("Logout successful!")
                }
            }
        }
    }
}
