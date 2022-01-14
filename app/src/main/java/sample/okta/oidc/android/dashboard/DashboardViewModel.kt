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
package sample.okta.oidc.android.dashboard

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.dto.OidcTokenType
import com.okta.oauth2.AuthorizationCodeFlow
import com.okta.oauth2.RedirectEndSessionFlow
import com.okta.webauthenticationui.WebAuthenticationClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import sample.okta.oidc.android.BuildConfig
import sample.okta.oidc.android.SocialRedirectCoordinator
import timber.log.Timber

internal class DashboardViewModel : ViewModel() {
    private val _requestStateLiveData = MutableLiveData<RequestState>(RequestState.Result(""))
    val requestStateLiveData: LiveData<RequestState> = _requestStateLiveData

    private val _userInfoLiveData = MutableLiveData<Map<String, String>>(emptyMap())
    val userInfoLiveData: LiveData<Map<String, String>> = _userInfoLiveData

    private var oidcClient: OidcClient? = null
    private var webAuthenticationClient: WebAuthenticationClient? = null

    private var logoutFlowContext: RedirectEndSessionFlow.Context? = null

    var lastButtonId: Int = 0
    private var lastRequestJob: Job? = null

    init {
        SocialRedirectCoordinator.listeners += ::handleRedirect

        viewModelScope.launch {
            val configuration =
                OidcConfiguration(BuildConfig.CLIENT_ID, setOf("openid", "email", "profile", "offline_access"))
            when (val clientResult = OidcClient.create(
                configuration,
                "${BuildConfig.ISSUER}/.well-known/openid-configuration".toHttpUrl()
            )) {
                is OidcClientResult.Error -> {
                    Timber.e(clientResult.exception, "Failed to create client")
                }
                is OidcClientResult.Success -> {
                    oidcClient = clientResult.result
                    oidcClient?.storeTokens(TokenViewModel.tokens)
                    getUserInfo()
                    setupWebAuthenticationClient()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        SocialRedirectCoordinator.listeners -= ::handleRedirect
    }

    private fun setupWebAuthenticationClient() {
        val oidcClient = oidcClient ?: return
        val authorizationCodeFlow = AuthorizationCodeFlow(BuildConfig.REDIRECT_URI, oidcClient)
        val redirectEndSessionFlow = RedirectEndSessionFlow(BuildConfig.END_SESSION_REDIRECT_URI, oidcClient)
        webAuthenticationClient = WebAuthenticationClient(authorizationCodeFlow, redirectEndSessionFlow)
    }

    fun revoke(buttonId: Int, tokenType: OidcTokenType) {
        performRequest(buttonId) { client ->
            when (client.revokeToken(tokenType)) {
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
        performRequest(buttonId) { client ->
            when (client.refreshToken()) {
                is OidcClientResult.Error -> {
                    RequestState.Result("Failed to refresh token.")
                }
                is OidcClientResult.Success -> {
                    RequestState.Result("Token Refreshed.")
                }
            }
        }
    }

    fun introspect(buttonId: Int, tokenType: OidcTokenType) {
        performRequest(buttonId) { client ->
            when (val result = client.introspectToken(tokenType)) {
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
            logoutFlowContext = webAuthenticationClient?.logout(context)
        }
    }

    private fun performRequest(buttonId: Int, performer: suspend (OidcClient) -> RequestState) {
        if (lastRequestJob?.isActive == true) {
            // Re-enable the button, so it's not permanently disabled.
            _requestStateLiveData.value = RequestState.Result("")
        }
        lastRequestJob?.cancel()
        lastButtonId = buttonId

        val client = oidcClient
        if (client == null) {
            Timber.d("Client not present.")
            return
        }
        _requestStateLiveData.value = RequestState.Loading

        lastRequestJob = viewModelScope.launch {
            _requestStateLiveData.postValue(performer(client))
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
        when (val userInfoResult = oidcClient?.getUserInfo()) {
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
            when (val result = webAuthenticationClient?.resume(uri, logoutFlowContext!!)) {
                is RedirectEndSessionFlow.Result.Error -> {
                    _requestStateLiveData.value = RequestState.Result(result.message)
                }
                RedirectEndSessionFlow.Result.MissingResultCode -> {
                    _requestStateLiveData.value = RequestState.Result("Invalid redirect. Missing result code.")
                }
                RedirectEndSessionFlow.Result.RedirectSchemeMismatch -> {
                    _requestStateLiveData.value = RequestState.Result("Invalid redirect. Redirect scheme mismatch.")
                }
                is RedirectEndSessionFlow.Result.Success -> {
                    _requestStateLiveData.value = RequestState.Result("Logout successful!")
                }
            }
        }
    }
}
