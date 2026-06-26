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
package com.okta.directauth.app

import android.app.Application
import com.okta.authfoundation.AuthFoundation
import com.okta.authfoundation.client.OidcConfiguration

class DirectAuthApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AuthFoundation.initializeAndroidContext(this)
        val issuer =
            if (AppConfig.AUTHORIZATION_SERVER_ID.isBlank()) {
                AppConfig.ISSUER
            } else {
                "${AppConfig.ISSUER}/oauth2/${AppConfig.AUTHORIZATION_SERVER_ID}"
            }
        OidcConfiguration.default =
            OidcConfiguration(
                clientId = AppConfig.CLIENT_ID,
                defaultScope = "openid email profile offline_access",
                issuer = issuer
            )
    }
}
