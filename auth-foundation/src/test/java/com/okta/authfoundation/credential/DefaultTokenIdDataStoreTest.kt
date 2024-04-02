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

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.client.ApplicationContextHolder
import com.okta.testhelpers.MockAesEncryptionHandler
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultTokenIdDataStoreTest {
    private lateinit var defaultTokenIdDataStore: DefaultTokenIdDataStore

    @Before
    fun setUp() {
        mockkObject(ApplicationContextHolder)
        every { ApplicationContextHolder.appContext } returns ApplicationProvider.getApplicationContext()
        defaultTokenIdDataStore = DefaultTokenIdDataStore(MockAesEncryptionHandler.instance)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getDefaultTokenId returns null if no default token is set`() = runTest {
        assertThat(defaultTokenIdDataStore.getDefaultTokenId()).isNull()
    }

    @Test
    fun `getDefaultTokenId returns the token id provided in setDefaultTokenId`() = runTest {
        val tokenId = "tokenId"
        defaultTokenIdDataStore.setDefaultTokenId(tokenId)
        assertThat(defaultTokenIdDataStore.getDefaultTokenId()).isEqualTo(tokenId)
    }

    @Test
    fun `getDefaultTokenId returns the token id provided by the latest call to setDefaultTokenId`() = runTest {
        val oldTokenId = "oldTokenId"
        val newTokenId = "newTokenId"
        defaultTokenIdDataStore.setDefaultTokenId(oldTokenId)
        defaultTokenIdDataStore.setDefaultTokenId(newTokenId)

        assertThat(defaultTokenIdDataStore.getDefaultTokenId()).isEqualTo(newTokenId)
    }
}
