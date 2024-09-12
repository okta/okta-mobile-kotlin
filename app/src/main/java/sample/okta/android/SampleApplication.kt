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
package sample.okta.android

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import com.okta.authfoundation.AuthFoundation
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.credential.TokenDbRecoveryUtil
import timber.log.Timber

class SampleApplication : Application() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
    }

    override fun onCreate() {
        super.onCreate()

        context = this

        Timber.plant(Timber.DebugTree())

        AuthFoundation.initializeAndroidContext(this)
        OidcConfiguration.default = OidcConfiguration(
            clientId = BuildConfig.CLIENT_ID,
            defaultScope = SampleHelper.DEFAULT_SCOPE,
            issuer = BuildConfig.ISSUER
        )
        // Use this in case token database is corrupted due to automatic backup/restore
        TokenDbRecoveryUtil.setupDatabaseRecovery()
    }
}
