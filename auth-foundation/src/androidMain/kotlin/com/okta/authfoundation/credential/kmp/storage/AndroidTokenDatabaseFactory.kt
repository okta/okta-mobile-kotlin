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
package com.okta.authfoundation.credential.kmp.storage

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.okta.authfoundation.client.OAuth2ClientConfiguration
import com.okta.authfoundation.credential.kmp.AndroidTokenEncryptionHandler
import kotlinx.coroutines.Dispatchers

/**
 * Creates a [TokenDatabase] for Android applications, backed by Room's bundled SQLite driver.
 *
 * Unlike the legacy SQLCipher-backed `RoomTokenStorage`, this database file itself is not whole-database
 * encrypted — only the access-token column is encrypted, via whichever [com.okta.authfoundation.credential.kmp.TokenEncryptionHandler]
 * is passed to [RoomTokenStorage] (see [createEncryptedTokenStorage]).
 *
 * @param context the Android application context.
 * @param dbName the Room database file name. Defaults to [TokenDatabase.DB_NAME].
 * @return a configured [TokenDatabase] instance.
 */
fun createTokenDatabase(
    context: Context,
    dbName: String = TokenDatabase.DB_NAME,
): TokenDatabase =
    Room
        .databaseBuilder<TokenDatabase>(context = context.applicationContext, name = dbName)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

/**
 * Creates a [RoomTokenStorage] for Android applications, encrypting the access token via
 * [AndroidTokenEncryptionHandler] (Android Keystore, with optional biometric gating).
 *
 * @param context the Android application context.
 * @param configuration the [OAuth2ClientConfiguration] used to reconstruct tokens.
 * @param keyAlias the Android Keystore alias for the encryption key. Defaults to [AndroidTokenEncryptionHandler.DEFAULT_KEY_ALIAS].
 * @param requireBiometric when true, decrypting a stored token requires a biometric (or device credential)
 *   challenge. See [AndroidTokenEncryptionHandler].
 * @param userAuthenticationTimeout seconds after a successful biometric challenge during which decryption
 *   may proceed without re-prompting; `0` means "auth-per-use". Ignored when [requireBiometric] is false.
 * @param promptInfo the [BiometricPrompt.PromptInfo] to display for biometric challenges. Required
 *   (non-null) when [requireBiometric] is true — see [AndroidTokenEncryptionHandler].
 * @param dbName the Room database file name. Defaults to [TokenDatabase.DB_NAME].
 * @return a configured [RoomTokenStorage] instance with encryption enabled.
 */
fun createEncryptedTokenStorage(
    context: Context,
    configuration: OAuth2ClientConfiguration,
    keyAlias: String = AndroidTokenEncryptionHandler.DEFAULT_KEY_ALIAS,
    requireBiometric: Boolean = false,
    userAuthenticationTimeout: Int = 5,
    promptInfo: BiometricPrompt.PromptInfo? = null,
    dbName: String = TokenDatabase.DB_NAME,
): RoomTokenStorage {
    val database = createTokenDatabase(context, dbName)
    val encryptionHandler =
        AndroidTokenEncryptionHandler(
            keyAlias = keyAlias,
            requireBiometric = requireBiometric,
            userAuthenticationTimeout = userAuthenticationTimeout,
            promptInfo = promptInfo
        )
    return RoomTokenStorage(database, encryptionHandler, configuration)
}
