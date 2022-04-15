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
package sample.okta.android.legacy

import android.content.Context
import com.okta.oidc.OIDCConfig
import com.okta.oidc.Okta
import com.okta.oidc.clients.web.WebAuthClient

internal object SampleWebAuthClientHelper {
    lateinit var webAuthClient: WebAuthClient

    fun initialize(context: Context) {
        val config = OIDCConfig.Builder()
            .clientId(BuildConfig.CLIENT_ID)
            .redirectUri(BuildConfig.LEGACY_SIGN_IN_REDIRECT_URI)
            .endSessionRedirectUri(BuildConfig.LEGACY_SIGN_OUT_REDIRECT_URI)
            .scopes("openid", "profile", "offline_access")
            .discoveryUri(BuildConfig.ISSUER)
            .create()

        webAuthClient = Okta.WebAuthBuilder()
            .withConfig(config)
            .withContext(context)
            .setRequireHardwareBackedKeyStore(false)
            .create()
    }
}
