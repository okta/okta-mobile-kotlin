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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryDefaultCredentialIdStoreTest {
    private val store = InMemoryDefaultCredentialIdStore()

    @Test
    fun getDefaultCredentialId_InitiallyNull() =
        runTest {
            assertNull(store.getDefaultCredentialId())
        }

    @Test
    fun setAndGetDefaultCredentialId() =
        runTest {
            store.setDefaultCredentialId("cred-1")
            assertEquals("cred-1", store.getDefaultCredentialId())
        }

    @Test
    fun setDefaultCredentialId_OverwritesPrevious() =
        runTest {
            store.setDefaultCredentialId("cred-1")
            store.setDefaultCredentialId("cred-2")
            assertEquals("cred-2", store.getDefaultCredentialId())
        }

    @Test
    fun clearDefaultCredentialId_SetsToNull() =
        runTest {
            store.setDefaultCredentialId("cred-3")
            store.clearDefaultCredentialId()
            assertNull(store.getDefaultCredentialId())
        }

    @Test
    fun clearDefaultCredentialId_WhenAlreadyNull_NoError() =
        runTest {
            store.clearDefaultCredentialId()
            assertNull(store.getDefaultCredentialId())
        }
}
