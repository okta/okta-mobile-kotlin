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

import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.room.Room
import com.okta.authfoundation.AuthFoundationDefaults
import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.client.ApplicationContextHolder
import com.okta.authfoundation.client.DeviceTokenProvider
import com.okta.authfoundation.credential.storage.TokenDatabase
import com.okta.authfoundation.credential.storage.TokenEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@InternalAuthFoundationApi
class RoomTokenStorage(
    tokenDatabase: TokenDatabase,
    private val tokenEncryptionHandler: TokenEncryptionHandler
) : TokenStorage {
    companion object {
        private var instance: RoomTokenStorage? = null
        private val instanceMutex = Mutex()

        suspend fun getInstance(): RoomTokenStorage {
            instanceMutex.withLock {
                return instance ?: run {
                    val tokenStorage = createInstance()
                    instance = tokenStorage
                    tokenStorage
                }
            }
        }

        private suspend fun createInstance(): RoomTokenStorage {
            val context = ApplicationContextHolder.appContext
            val sqlCipherPassword = DeviceTokenProvider.instance.getDeviceToken()
            System.loadLibrary("sqlcipher")
            val tokenDatabase =
                Room.databaseBuilder(
                    context,
                    TokenDatabase::class.java,
                    TokenDatabase.DB_NAME
                )
                    .openHelperFactory(SupportOpenHelperFactory(sqlCipherPassword.toByteArray()))
                    .build()
            return RoomTokenStorage(tokenDatabase, AuthFoundationDefaults.tokenEncryptionHandler)
        }
    }

    private val tokenDao = tokenDatabase.tokenDao()

    override suspend fun allIds(): List<String> = tokenDao.allEntries().map { it.id }

    override suspend fun metadata(id: String): Token.Metadata? {
        return tokenDao.getById(id)?.let {
            Token.Metadata(
                it.id,
                it.tags,
                it.payloadData
            )
        }
    }

    override suspend fun setMetadata(metadata: Token.Metadata) {
        val tokenEntity = tokenDao.getById(id = metadata.id) ?: throw NoSuchElementException()
        tokenDao.updateTokenEntity(
            tokenEntity.copy(tags = metadata.tags, payloadData = metadata.payloadData)
        )
    }

    override suspend fun add(
        token: Token,
        metadata: Token.Metadata,
        security: Credential.Security
    ) {
        tokenEncryptionHandler.generateKey(security)
        val encryptionResult = tokenEncryptionHandler.encrypt(token, security)


        tokenDao.insertTokenEntity(
            TokenEntity(
                id = metadata.id,
                encryptionResult.encryptedToken,
                tags = metadata.tags,
                payloadData = metadata.payloadData,
                keyAlias = security.keyAlias,
                tokenEncryptionType = TokenEntity.EncryptionType.fromSecurity(security),
                encryptionExtras = encryptionResult.encryptionExtras
            )
        )
    }

    override suspend fun remove(id: String) {
        tokenDao.getById(id)?.let { tokenEntity ->
            tokenDao.deleteTokenEntity(tokenEntity)
        }
    }

    override suspend fun replace(
        id: String,
        token: Token,
        metadata: Token.Metadata?,
        security: Credential.Security?,
    ) {
        val tokenEntity = tokenDao.getById(id) ?: throw NoSuchElementException()

        security?.let { tokenEncryptionHandler.generateKey(it) }
        val encryptionResult = tokenEncryptionHandler.encrypt(
            token, security ?: tokenEntity.security
        )

        val updatedTokenEntity = tokenEntity.copy(
            encryptedToken = encryptionResult.encryptedToken,
            tags = metadata?.tags ?: tokenEntity.tags,
            payloadData = metadata?.payloadData ?: tokenEntity.payloadData,
            keyAlias = security?.keyAlias ?: tokenEntity.keyAlias,
            tokenEncryptionType = security?.let {
                TokenEntity.EncryptionType.fromSecurity(it)
            } ?: tokenEntity.tokenEncryptionType,
            encryptionExtras = encryptionResult.encryptionExtras
        )

        tokenDao.updateTokenEntity(updatedTokenEntity)
    }

    override suspend fun getToken(id: String, promptInfo: PromptInfo?): Token {
        val tokenEntity = tokenDao.getById(id) ?: throw NoSuchElementException()
        return getTokenFromEntity(tokenEntity, promptInfo)
    }

    private suspend fun getTokenFromEntity(tokenEntity: TokenEntity, promptInfo: PromptInfo?): Token {
        return tokenEncryptionHandler.decrypt(
            tokenEntity.encryptedToken,
            tokenEntity.encryptionExtras,
            tokenEntity.security,
            promptInfo
        )
    }
}
