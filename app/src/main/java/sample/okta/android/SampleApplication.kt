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
package sample.okta.android

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.biometric.BiometricPrompt
import com.okta.authfoundation.AuthFoundation
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.authfoundation.credential.TokenDbRecoveryUtil
import com.okta.authfoundation.credential.kmp.AndroidTokenEncryptionHandler
import com.okta.authfoundation.credential.kmp.TokenCredentialManager
import com.okta.authfoundation.credential.kmp.storage.RoomDefaultCredentialIdStore
import com.okta.authfoundation.credential.kmp.storage.RoomTokenStorage
import com.okta.authfoundation.credential.kmp.storage.createTokenDatabase
import timber.log.Timber

class SampleApplication : Application() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context

        val oAuth2Client: OAuth2Client by lazy {
            OAuth2ClientBuilder
                .create(
                    issuerUrl = BuildConfig.ISSUER,
                    clientId = BuildConfig.CLIENT_ID,
                    scope = SampleHelper.DEFAULT_SCOPE.split(" ")
                ).getOrThrow()
        }

        /** Backs biometric-gated decryption for [credentialManager]. */
        val tokenEncryptionHandler: AndroidTokenEncryptionHandler by lazy {
            AndroidTokenEncryptionHandler(
                requireBiometric = true,
                userAuthenticationTimeout = 0,
                promptInfo =
                    BiometricPrompt.PromptInfo
                        .Builder()
                        .setTitle("Authenticate")
                        .setSubtitle("Verify your identity to access your account")
                        .setNegativeButtonText("Cancel")
                        .build()
            )
        }

        val credentialManager: TokenCredentialManager by lazy {
            val database = createTokenDatabase(context)
            val storage = RoomTokenStorage(database, tokenEncryptionHandler, oAuth2Client.configuration)
            val defaultIdStore = RoomDefaultCredentialIdStore(database)
            TokenCredentialManager(oAuth2Client, storage, defaultIdStore)
        }
    }

    override fun onCreate() {
        super.onCreate()

        context = this

        Timber.plant(Timber.DebugTree())

        AuthFoundation.initializeAndroidContext(this)
        // Use this in case token database is corrupted due to automatic backup/restore
        TokenDbRecoveryUtil.setupDatabaseRecovery()
    }
}
