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
package com.okta.idx.android

import android.content.Context
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.credential.CredentialDataSource.Companion.createCredentialDataSource
import com.okta.authfoundationbootstrap.CredentialBootstrap
import com.okta.idx.android.dynamic.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl

internal object SampleCredentialHelper {
    fun initialize(context: Context) {
        val oidcConfiguration = OidcConfiguration(
            clientId = BuildConfig.CLIENT_ID,
            defaultScope = "openid email profile offline_access",
        )
        val oidcClient = OidcClient.createFromDiscoveryUrl(
            oidcConfiguration,
            "${BuildConfig.ISSUER}/.well-known/openid-configuration".toHttpUrl(),
        )
        CredentialBootstrap.initialize(oidcClient.createCredentialDataSource(context))
    }
}
