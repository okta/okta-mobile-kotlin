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

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricPrompt
import com.okta.authfoundation.AuthFoundationDefaults
import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.credential.events.CredentialCreatedEvent
import com.okta.authfoundation.jwt.JwtParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections

@InternalAuthFoundationApi
class CredentialDataSource(
    private val storage: TokenStorage,
) {
    companion object {
        private var instance: CredentialDataSource? = null
        private val instanceMutex = Mutex()

        suspend fun getInstance(): CredentialDataSource {
            instanceMutex.withLock {
                return instance ?: run {
                    val tokenStorage = AuthFoundationDefaults.tokenStorageFactory()
                    val credentialDataSource = CredentialDataSource(tokenStorage)
                    instance = credentialDataSource
                    credentialDataSource
                }
            }
        }
    }

    private val jwtParser = JwtParser.create()

    private val credentialsCache = Collections.synchronizedMap(mutableMapOf<String, Credential>())

    suspend fun allIds() = storage.allIds()

    suspend fun metadata(id: String) = storage.metadata(id)

    suspend fun setMetadata(metadata: Token.Metadata) {
        storage.setMetadata(metadata)
        credentialsCache[metadata.id]?._tags = metadata.tags
    }

    suspend fun createCredential(
        token: Token,
        tags: Map<String, String> = emptyMap(),
        security: Credential.Security = Credential.Security.standard
    ): Credential {
        val idToken = token.idToken?.let { jwtParser.parse(it) }
        val credential =
            Credential(token, tags = tags)
        credentialsCache[token.id] = credential
        storage.add(
            token,
            Token.Metadata(
                token.id,
                tags,
                idToken
            ),
            security
        )
        AuthFoundationDefaults.eventCoordinator.sendEvent(CredentialCreatedEvent(credential))
        return credential
    }

    suspend fun replaceToken(token: Token) {
        storage.replace(token)
        val credential = credentialsCache[token.id] ?: throw IllegalStateException("Attempted replacing a non-existent Token")
        credential.replaceToken(token)
    }

    suspend fun getCredential(id: String, promptInfo: BiometricPrompt.PromptInfo? = Credential.Security.promptInfo): Credential? {
        return if (id in credentialsCache) credentialsCache[id]
        else {
            val metadata = metadata(id) ?: return null
            val token = try {
                storage.getToken(id, promptInfo)
            } catch (ex: KeyPermanentlyInvalidatedException) {
                return null
            }
            credentialsCache[id] = Credential(
                token,
                tags = metadata.tags
            )
            return credentialsCache[id]
        }
    }

    suspend fun findCredential(
        promptInfo: BiometricPrompt.PromptInfo? = Credential.Security.promptInfo,
        where: (Token.Metadata) -> Boolean
    ): List<Credential> {
        return allIds()
            .mapNotNull { metadata(it) }
            .filter { where(it) }
            .mapNotNull { metadata ->
                getCredential(metadata.id, promptInfo)
            }
    }

    suspend fun remove(credential: Credential) {
        credentialsCache.remove(credential.id)
        storage.remove(credential.id)
    }
}
