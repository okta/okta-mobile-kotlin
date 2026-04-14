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
package com.okta.authfoundation.credential.kmp

/**
 * Platform-agnostic abstraction for storing the default credential ID.
 *
 * On Android, the existing [DefaultCredentialIdDataStore] provides an encrypted DataStore-based
 * implementation. For cross-platform persistent storage, use
 * [RoomDefaultCredentialIdStore][com.okta.authfoundation.credential.storage.RoomDefaultCredentialIdStore].
 */
interface DefaultCredentialIdStore {
    /** Returns the current default credential ID, or null if none is set. */
    suspend fun getDefaultCredentialId(): Result<String?>

    /** Sets the default credential ID. */
    suspend fun setDefaultCredentialId(id: String): Result<Unit>

    /** Clears the default credential ID. */
    suspend fun clearDefaultCredentialId(): Result<Unit>
}
