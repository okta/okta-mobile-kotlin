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
package com.okta.idx.android.dynamic.auth

import com.okta.idx.android.dynamic.BuildConfig
import com.okta.idx.kotlin.client.IdxClientConfiguration
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Provides Okta organization configuration for IDX client.
 */
internal object IdxClientConfigurationProvider {
    fun get(): IdxClientConfiguration {
        return IdxClientConfiguration(
            issuer = BuildConfig.ISSUER.toHttpUrl(),
            clientId = BuildConfig.CLIENT_ID,
            scopes = setOf("openid", "email", "profile", "offline_access"),
            redirectUri = BuildConfig.REDIRECT_URI,
        )
    }
}
