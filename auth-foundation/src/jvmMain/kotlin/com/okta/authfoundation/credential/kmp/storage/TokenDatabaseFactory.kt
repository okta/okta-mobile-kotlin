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
import com.okta.authfoundation.InternalAuthFoundationApi
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Creates a [TokenDatabase] for JVM applications using file-based SQLite.
 *
 * The database file is created at [dbPath]. Parent directories are created if they do not exist.
 *
 * @param dbPath the file system path for the database. Defaults to `~/.okta/common_token_database`.
 * @return a configured [TokenDatabase] instance.
 */
@InternalAuthFoundationApi
fun createTokenDatabase(dbPath: String = "${System.getProperty("user.home")}${File.separator}.okta${File.separator}${TokenDatabase.DB_NAME}"): TokenDatabase {
    val dbFile = File(dbPath)
    dbFile.parentFile?.mkdirs()
    return Room
        .databaseBuilder<TokenDatabase>(name = dbFile.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
