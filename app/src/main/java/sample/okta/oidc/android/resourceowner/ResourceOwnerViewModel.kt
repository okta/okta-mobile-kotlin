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
package sample.okta.oidc.android.resourceowner

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.dto.OidcTokens
import com.okta.oauth2.ResourceOwnerFlow
import com.okta.oauth2.ResourceOwnerFlow.Companion.resourceOwnerFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import sample.okta.oidc.android.BuildConfig
import timber.log.Timber

internal class ResourceOwnerViewModel : ViewModel() {
    private val _state = MutableLiveData<ResourceOwnerState>(ResourceOwnerState.Idle)
    val state: LiveData<ResourceOwnerState> = _state

    fun login(username: String, password: String) {
        _state.value = ResourceOwnerState.Loading

        viewModelScope.launch {
            val oidcConfiguration = OidcConfiguration(
                clientId = BuildConfig.CLIENT_ID,
                scopes = setOf("openid", "email", "profile", "offline_access"),
                signInRedirectUri = BuildConfig.SIGN_IN_REDIRECT_URI,
                signOutRedirectUri = BuildConfig.SIGN_OUT_REDIRECT_URI,
            )
            when (val clientResult = OidcClient.create(
                oidcConfiguration,
                "${BuildConfig.ISSUER}/.well-known/openid-configuration".toHttpUrl(),
            )) {
                is OidcClientResult.Error -> {
                    Timber.e(clientResult.exception, "Failed to create client")
                    _state.value = ResourceOwnerState.Error("Failed to create client.")
                }
                is OidcClientResult.Success -> {
                    val oidcClient = clientResult.result
                    val resourceOwnerFlow = oidcClient.resourceOwnerFlow()
                    _state.value = resourceOwnerFlow.start(username, password).transformToState()
                }
            }
        }
    }

    private fun ResourceOwnerFlow.Result.transformToState(): ResourceOwnerState {
        return when (this) {
            is ResourceOwnerFlow.Result.Error -> {
                ResourceOwnerState.Error(message)
            }
            is ResourceOwnerFlow.Result.Tokens -> {
                ResourceOwnerState.Tokens(tokens)
            }
        }
    }
}

sealed class ResourceOwnerState {
    object Idle : ResourceOwnerState()
    object Loading : ResourceOwnerState()
    data class Error(val message: String) : ResourceOwnerState()
    data class Tokens(val tokens: OidcTokens) : ResourceOwnerState()
}
