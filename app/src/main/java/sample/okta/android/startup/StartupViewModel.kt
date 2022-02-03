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
package sample.okta.android.startup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.credential.CredentialDataSource
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import sample.okta.android.BuildConfig
import sample.okta.android.DefaultCredential
import timber.log.Timber

internal class StartupViewModel : ViewModel() {
    private val _state = MutableLiveData<StartupState>(StartupState.Loading)
    val state: LiveData<StartupState> = _state

    init {
        startup()
    }

    fun startup() {
        _state.value = StartupState.Loading

        viewModelScope.launch {
            val oidcConfiguration = OidcConfiguration(
                clientId = BuildConfig.CLIENT_ID,
                defaultScopes = setOf("openid", "email", "profile", "offline_access"),
                signInRedirectUri = BuildConfig.SIGN_IN_REDIRECT_URI,
                signOutRedirectUri = BuildConfig.SIGN_OUT_REDIRECT_URI,
            )
            when (val clientResult = OidcClient.create(
                oidcConfiguration,
                "${BuildConfig.ISSUER}/.well-known/openid-configuration".toHttpUrl(),
            )) {
                is OidcClientResult.Error -> {
                    Timber.e(clientResult.exception, "Failed to create client")
                    _state.value = StartupState.Error("Failed to create client.")
                }
                is OidcClientResult.Success -> {
                    val oidcClient = clientResult.result
                    val credentialDataSource = CredentialDataSource.create(oidcClient)
                    DefaultCredential.instance = credentialDataSource.create()
                    _state.value = StartupState.Complete
                }
            }
        }
    }
}

sealed class StartupState {
    object Loading : StartupState()
    data class Error(val message: String) : StartupState()
    object Complete : StartupState()
}
