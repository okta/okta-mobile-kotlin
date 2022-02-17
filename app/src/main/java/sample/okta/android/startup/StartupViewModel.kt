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
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.credential.CredentialDataSource.Companion.credentialDataSource
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import sample.okta.android.BuildConfig
import sample.okta.android.OktaHelper

internal class StartupViewModel : ViewModel() {
    companion object {
        private const val DEFAULT_CREDENTIAL_NAME_METADATA_VALUE: String = "Default"
    }

    private val _state = MutableLiveData<StartupState>(StartupState.Loading)
    val state: LiveData<StartupState> = _state

    init {
        startup()
    }

    private fun startup() {
        if (OktaHelper.isInitialized()) {
            _state.value = StartupState.Complete
            return
        }

        _state.value = StartupState.Loading

        viewModelScope.launch {
            val oidcConfiguration = OidcConfiguration(
                clientId = BuildConfig.CLIENT_ID,
                defaultScopes = setOf("openid", "email", "profile", "offline_access"),
                signInRedirectUri = BuildConfig.SIGN_IN_REDIRECT_URI,
                signOutRedirectUri = BuildConfig.SIGN_OUT_REDIRECT_URI,
            )
            val oidcClient = OidcClient.createFromDiscoveryUrl(
                oidcConfiguration,
                "${BuildConfig.ISSUER}/.well-known/openid-configuration".toHttpUrl(),
            )
            val credentialDataSource = oidcClient.credentialDataSource()
            OktaHelper.credentialDataSource = credentialDataSource
            val credential = credentialDataSource.all().firstOrNull { credential ->
                credential.metadata[OktaHelper.CREDENTIAL_NAME_METADATA_KEY] == DEFAULT_CREDENTIAL_NAME_METADATA_VALUE
            } ?: credentialDataSource.create()
            OktaHelper.defaultCredential = credential
            OktaHelper.defaultCredential.storeToken(
                metadata = mapOf(
                    Pair(
                        OktaHelper.CREDENTIAL_NAME_METADATA_KEY,
                        DEFAULT_CREDENTIAL_NAME_METADATA_VALUE
                    )
                )
            )
            _state.value = StartupState.Complete
        }
    }
}

sealed class StartupState {
    object Loading : StartupState()
    object Complete : StartupState()
}
