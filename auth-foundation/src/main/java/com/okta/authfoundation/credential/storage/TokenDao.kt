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
package com.okta.authfoundation.credential.storage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
internal interface TokenDao {
    @Query("SELECT * FROM TokenEntity")
    suspend fun allEntries(): List<TokenEntity>

    @Query("SELECT * FROM TokenEntity WHERE id = :id")
    suspend fun getById(id: String): TokenEntity?

    @Insert
    suspend fun insertTokenEntity(tokenEntity: TokenEntity)

    @Update
    suspend fun updateTokenEntity(vararg tokenEntity: TokenEntity)

    @Delete
    suspend fun deleteTokenEntity(tokenEntity: TokenEntity)
}
