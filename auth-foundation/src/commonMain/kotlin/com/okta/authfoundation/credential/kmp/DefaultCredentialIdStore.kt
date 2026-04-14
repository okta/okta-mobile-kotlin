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
 * implementation. On JVM, [InMemoryDefaultCredentialIdStore] provides a simple in-memory implementation.
 */
interface DefaultCredentialIdStore {
    /** Returns the current default credential ID, or null if none is set. */
    suspend fun getDefaultCredentialId(): String?

    /** Sets the default credential ID. */
    suspend fun setDefaultCredentialId(id: String)

    /** Clears the default credential ID. */
    suspend fun clearDefaultCredentialId()
}

/**
 * Simple in-memory implementation of [DefaultCredentialIdStore].
 *
 * Suitable for testing and JVM applications where persistence across restarts is not required.
 */
class InMemoryDefaultCredentialIdStore : DefaultCredentialIdStore {
    @Volatile
    private var defaultId: String? = null

    override suspend fun getDefaultCredentialId(): String? = defaultId

    override suspend fun setDefaultCredentialId(id: String) {
        defaultId = id
    }

    override suspend fun clearDefaultCredentialId() {
        defaultId = null
    }
}
