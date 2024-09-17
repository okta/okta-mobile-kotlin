/*
 * Copyright 2024-Present Okta, Inc.
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
package com.okta.authfoundation.credential

import android.database.sqlite.SQLiteException
import com.okta.authfoundation.client.ApplicationContextHolder
import com.okta.authfoundation.credential.storage.TokenDatabase

object TokenDbRecoveryUtil {
    fun setupDatabaseRecovery() {
        val context = ApplicationContextHolder.appContext
        val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            if (exception is SQLiteException) {
                context.deleteDatabase(TokenDatabase.DB_NAME)
            } else {
                defaultUncaughtExceptionHandler?.uncaughtException(thread, exception)
            }
        }
    }
}
