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
package com.okta.idx.android.dynamic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.credential.CredentialDataSource.Companion.credentialDataSource
import com.okta.idx.android.OktaHelper
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl

internal class LaunchViewModel : ViewModel() {
    companion object {
        private const val DEFAULT_CREDENTIAL_NAME_METADATA_VALUE: String = "Default"
    }

    fun start() {
        if (OktaHelper.isInitialized()) return

        viewModelScope.launch {
            val oidcConfiguration = OidcConfiguration(
                clientId = BuildConfig.CLIENT_ID,
                defaultScopes = setOf("openid", "email", "profile", "offline_access"),
                signInRedirectUri = BuildConfig.REDIRECT_URI,
            )
            val oidcClient = OidcClient.createFromDiscoveryUrl(
                oidcConfiguration,
                "${BuildConfig.ISSUER}/.well-known/openid-configuration".toHttpUrl(),
            )
            val credentialDataSource = oidcClient.credentialDataSource(SampleApplication.context)
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
        }
    }
}
