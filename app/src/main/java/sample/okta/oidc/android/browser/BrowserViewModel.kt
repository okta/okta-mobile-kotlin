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
package sample.okta.oidc.android.browser

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import sample.okta.oidc.android.BuildConfig
import sample.okta.oidc.android.SocialRedirectCoordinator
import com.okta.oauth2.AuthorizationCodeFlow
import com.okta.webauthenticationui.WebAuthenticationClient
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.dto.OidcTokens
import com.okta.oauth2.RedirectEndSessionFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber

class BrowserViewModel : ViewModel() {
    private lateinit var client: WebAuthenticationClient
    private var authorizationCodeFlowContext: AuthorizationCodeFlow.Context? = null

    private val _state = MutableLiveData<BrowserState>(BrowserState.Idle)
    val state: LiveData<BrowserState> = _state

    init {
        SocialRedirectCoordinator.listeners += ::handleRedirect

        viewModelScope.launch {
            val oidcConfiguration = OidcConfiguration(
                clientId = BuildConfig.CLIENT_ID,
                scopes = setOf("openid", "email", "profile", "offline_access"),
            )
            when (val clientResult = OidcClient.create(
                oidcConfiguration,
                "${BuildConfig.ISSUER}/.well-known/openid-configuration".toHttpUrl(),
            )) {
                is OidcClientResult.Error -> {
                    Timber.e(clientResult.exception, "Failed to create client")
                }
                is OidcClientResult.Success -> {
                    val oidcClient = clientResult.result
                    val authorizationCodeFlow = AuthorizationCodeFlow(BuildConfig.REDIRECT_URI, oidcClient)
                    val redirectEndSessionFlow = RedirectEndSessionFlow(BuildConfig.END_SESSION_REDIRECT_URI, oidcClient)
                    client = WebAuthenticationClient(authorizationCodeFlow, redirectEndSessionFlow)
                }
            }
        }
    }

    override fun onCleared() {
        SocialRedirectCoordinator.listeners -= ::handleRedirect
    }

    fun login(context: Context) {
        authorizationCodeFlowContext = client.login(context)
    }

    fun handleRedirect(uri: Uri) {
        viewModelScope.launch {
            when (val result = client.resume(uri, authorizationCodeFlowContext!!)) {
                is AuthorizationCodeFlow.Result.Error -> {
                    _state.value = BrowserState.Error(result.message)
                }
                AuthorizationCodeFlow.Result.MissingResultCode -> {
                    _state.value = BrowserState.Error("Invalid redirect. Missing result code.")
                }
                AuthorizationCodeFlow.Result.RedirectSchemeMismatch -> {
                    _state.value = BrowserState.Error("Invalid redirect. Redirect scheme mismatch.")
                }
                is AuthorizationCodeFlow.Result.Tokens -> {
                    _state.value = BrowserState.Tokens(result.tokens)
                }
            }
        }
    }
}

sealed class BrowserState {
    object Idle : BrowserState()
    data class Error(val message: String): BrowserState()
    data class Tokens(val tokens: OidcTokens): BrowserState()
}
