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
package com.okta.authfoundation.credential.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import com.okta.authfoundation.InternalAuthFoundationApi

@InternalAuthFoundationApi
@Database(
    entities = [
        TokenEntity::class
    ],
    version = TokenDatabase.VERSION
)
abstract class TokenDatabase : RoomDatabase() {
    internal abstract fun tokenDao(): TokenDao

    companion object {
        internal const val VERSION = 1
        internal const val DB_NAME = "token_database"
    }
}
