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

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.okta.authfoundation.client.OAuth2ClientConfiguration
import com.okta.authfoundation.credential.kmp.JceTokenEncryptionHandler
import com.okta.authfoundation.credential.kmp.TokenEncryptionHandler
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.crypto.SecretKey

/**
 * Creates a [TokenDatabase] for JVM applications using file-based SQLite.
 *
 * The database file is created at [dbPath]. Parent directories are created if they do not exist.
 *
 * @param dbPath the file system path for the database. Defaults to `~/.okta/common_token_database`.
 * @return a configured [TokenDatabase] instance.
 */
fun createTokenDatabase(dbPath: String = "${System.getProperty("user.home")}${File.separator}.okta${File.separator}${TokenDatabase.DB_NAME}"): TokenDatabase {
    val dbFile = File(dbPath)
    dbFile.parentFile?.mkdirs()
    return Room
        .databaseBuilder<TokenDatabase>(name = dbFile.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

/**
 * Creates a [RoomTokenStorage] for JVM applications with AES-256-GCM encryption.
 *
 * The encryption handler uses [JceTokenEncryptionHandler] by default, which manages
 * encryption keys at `~/.okta/.encryption_key` (Base64 encoded). Custom key providers
 * can be supplied via [encryptionKeyProvider].
 *
 * @param configuration the [OAuth2ClientConfiguration] used to reconstruct tokens.
 * @param dbPath the file system path for the database. Defaults to `~/.okta/common_token_database`.
 * @param encryptionKeyProvider optional lambda to provide a custom [SecretKey]. Defaults to JCE key management.
 * @return a configured [RoomTokenStorage] instance with encryption enabled.
 */
fun createEncryptedTokenStorage(
    configuration: OAuth2ClientConfiguration,
    dbPath: String = "${System.getProperty("user.home")}${File.separator}.okta${File.separator}${TokenDatabase.DB_NAME}",
    encryptionKeyProvider: (() -> SecretKey)? = null,
): RoomTokenStorage {
    val database = createTokenDatabase(dbPath)
    val encryptionHandler: TokenEncryptionHandler =
        if (encryptionKeyProvider != null) {
            JceTokenEncryptionHandler(encryptionKeyProvider)
        } else {
            JceTokenEncryptionHandler()
        }
    return RoomTokenStorage(database, encryptionHandler, configuration)
}
