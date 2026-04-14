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

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.credential.kmp.DefaultCredentialIdStore

/**
 * Room-backed [DefaultCredentialIdStore] that persists the default credential ID
 * in the same [TokenDatabase] used by [RoomTokenStorage].
 *
 * @param database the [TokenDatabase] instance (shared with [RoomTokenStorage]).
 */
@InternalAuthFoundationApi
class RoomDefaultCredentialIdStore(
    database: TokenDatabase,
) : DefaultCredentialIdStore {
    private val settingDao = database.settingDao()

    override suspend fun getDefaultCredentialId(): Result<String?> =
        runCatching {
            settingDao.getValue(DEFAULT_CREDENTIAL_KEY)
        }

    override suspend fun setDefaultCredentialId(id: String): Result<Unit> =
        runCatching {
            settingDao.upsert(SettingEntity(key = DEFAULT_CREDENTIAL_KEY, value = id))
        }

    override suspend fun clearDefaultCredentialId(): Result<Unit> =
        runCatching {
            settingDao.delete(DEFAULT_CREDENTIAL_KEY)
        }

    private companion object {
        const val DEFAULT_CREDENTIAL_KEY = "default_credential_id"
    }
}
