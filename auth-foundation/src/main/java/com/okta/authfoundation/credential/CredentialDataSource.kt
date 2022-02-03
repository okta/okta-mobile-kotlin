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
package com.okta.authfoundation.credential

import com.okta.authfoundation.OktaSdk
import com.okta.authfoundation.client.OidcClient

class CredentialDataSource internal constructor(
    private val oidcClient: OidcClient,
    private val storage: TokenStorage,
) {
    companion object {
        fun create(
            oidcClient: OidcClient,
            storage: TokenStorage = OktaSdk.storage,
        ): CredentialDataSource {
            return CredentialDataSource(oidcClient, storage)
        }
    }

    // TODO: Store credentials in weak hash map

    suspend fun create(): Credential {
        return Credential(oidcClient, storage)
    }

    suspend fun fetch(filter: (Map<String, String>) -> Boolean): Credential {
        TODO()
    }
}
