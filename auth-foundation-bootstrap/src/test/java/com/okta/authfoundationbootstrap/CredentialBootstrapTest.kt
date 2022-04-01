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
package com.okta.authfoundationbootstrap

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.credential.CredentialDataSource.Companion.createCredentialDataSource
import com.okta.testhelpers.InMemoryTokenStorage
import com.okta.testhelpers.OktaRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFailsWith

class CredentialBootstrapTest {
    @get:Rule val oktaRule = OktaRule()

    private fun initialize() {
        val oidcClient = oktaRule.createOidcClient()
        CredentialBootstrap.initialize(oidcClient.createCredentialDataSource(InMemoryTokenStorage()))
    }

    @Before fun reset() {
        CredentialBootstrap.reset()
    }

    @Test fun testCredentialFailsBeforeInitialize(): Unit = runBlocking {
        assertFailsWith<Exception> {
            CredentialBootstrap.credential()
        }
    }

    @Test fun testCredentialDataSourceFailsBeforeInitialize(): Unit = runBlocking {
        assertFailsWith<Exception> {
            CredentialBootstrap.credentialDataSource
        }
    }

    @Test fun testCredentialReturnsSameInstance(): Unit = runBlocking {
        initialize()
        val credential1 = CredentialBootstrap.credential()
        val credential2 = CredentialBootstrap.credential()
        assertThat(credential1).isNotNull()
        assertThat(credential1).isSameInstanceAs(credential2)
        assertThat(CredentialBootstrap.credentialDataSource.listCredentials()).hasSize(1)
    }

    @Test fun testCredentialDataSourceIsInitialized(): Unit = runBlocking {
        initialize()
        assertThat(CredentialBootstrap.credentialDataSource).isNotNull()
    }

    @Test fun testDeletedCredentialCreatesNewCredential(): Unit = runBlocking {
        initialize()
        val credential1 = CredentialBootstrap.credential()
        credential1.delete()
        val credential2 = CredentialBootstrap.credential()
        assertThat(credential1).isNotNull()
        assertThat(credential2).isNotNull()
        assertThat(credential1).isNotSameInstanceAs(credential2)
        assertThat(CredentialBootstrap.credentialDataSource.listCredentials()).hasSize(1)
    }

    @Test fun testCredentialOnlyReturnsDefaultCredential(): Unit = runBlocking {
        initialize()
        val credential1 = CredentialBootstrap.credentialDataSource.createCredential()
        val credential2 = CredentialBootstrap.credential()
        assertThat(credential1).isNotNull()
        assertThat(credential2).isNotNull()
        assertThat(credential1).isNotSameInstanceAs(credential2)
        assertThat(CredentialBootstrap.credentialDataSource.listCredentials()).hasSize(2)
    }

    @Test fun testCredentialReturnsSameInstanceWhenAsync(): Unit = runBlocking {
        initialize()
        val credential1 = async(Dispatchers.IO) { CredentialBootstrap.credential() }
        val credential2 = async(Dispatchers.IO) { CredentialBootstrap.credential() }
        assertThat(credential1.await()).isNotNull()
        assertThat(credential1.await()).isSameInstanceAs(credential2.await())
        assertThat(CredentialBootstrap.credentialDataSource.listCredentials()).hasSize(1)
    }
}
