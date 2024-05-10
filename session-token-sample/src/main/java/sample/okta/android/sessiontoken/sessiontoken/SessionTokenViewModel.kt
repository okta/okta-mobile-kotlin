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
package sample.okta.android.sessiontoken.sessiontoken

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.credential.Credential
import com.okta.authn.sdk.AuthenticationStateHandlerAdapter
import com.okta.authn.sdk.client.AuthenticationClients
import com.okta.authn.sdk.resource.AuthenticationResponse
import com.okta.oauth2.SessionTokenFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import sample.okta.android.sessiontoken.BuildConfig
import timber.log.Timber

class SessionTokenViewModel : ViewModel() {
    private val _state = MutableLiveData<SessionTokenState>(SessionTokenState.Idle)
    val state: LiveData<SessionTokenState> = _state

    fun login(username: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.postValue(SessionTokenState.Loading)

            try {
                val orgUrl = BuildConfig.ISSUER.toHttpUrl().newBuilder()
                    .encodedPath("/")
                    .build().toString()
                val authenticationClient = AuthenticationClients.builder().setOrgUrl(orgUrl).build()
                authenticationClient.authenticate(
                    username,
                    password.toCharArray(),
                    null,
                    object : AuthenticationStateHandlerAdapter() {
                        override fun handleUnknown(unknownResponse: AuthenticationResponse) {
                            _state.postValue(SessionTokenState.Error("Unknown response."))
                        }

                        override fun handleSuccess(successResponse: AuthenticationResponse) {
                            completeLoginWithSessionToken(successResponse.sessionToken)
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to login.")
                _state.value = SessionTokenState.Error("Failed to login.")
            }
        }
    }

    private fun completeLoginWithSessionToken(sessionToken: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val sessionTokenFlow = SessionTokenFlow()
            when (val result = sessionTokenFlow.start(sessionToken, BuildConfig.SIGN_IN_REDIRECT_URI)) {
                is OAuth2ClientResult.Error -> {
                    Timber.e(result.exception, "Failed to login.")
                    _state.value = SessionTokenState.Error("Failed to login.")
                }
                is OAuth2ClientResult.Success -> {
                    val credential = Credential.store(token = result.result)
                    Credential.setDefaultAsync(credential)
                    _state.value = SessionTokenState.Token
                }
            }
        }
    }
}

sealed class SessionTokenState {
    object Idle : SessionTokenState()
    object Loading : SessionTokenState()
    data class Error(val message: String) : SessionTokenState()
    object Token : SessionTokenState()
}
