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

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.room.Room
import com.okta.authfoundation.AuthFoundationDefaults
import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.client.ApplicationContextHolder
import com.okta.authfoundation.client.EncryptionTokenProvider
import com.okta.authfoundation.credential.events.BiometricKeyInvalidatedEvent
import com.okta.authfoundation.credential.events.BiometricTokenInvalidatedEvent
import com.okta.authfoundation.credential.storage.TokenDatabase
import com.okta.authfoundation.credential.storage.TokenEntity
import com.okta.authfoundation.credential.storage.migration.V1ToV2StorageMigrator
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
            val sqlCipherPasswordResult = EncryptionTokenProvider.instance.getEncryptionToken()
            if (sqlCipherPasswordResult is EncryptionTokenProvider.Result.NewToken) context.resetDatabase()
            val sqlCipherPassword = sqlCipherPasswordResult.token

            System.loadLibrary("sqlcipher")
            val tokenDatabase =
                Room.databaseBuilder(
                    context,
                    TokenDatabase::class.java,
                    TokenDatabase.DB_NAME
                )
                    .openHelperFactory(SupportOpenHelperFactory(sqlCipherPassword.toByteArray()))
                    .build()
            val tokenStorage = RoomTokenStorage(tokenDatabase, AuthFoundationDefaults.tokenEncryptionHandler)
            V1ToV2StorageMigrator(tokenStorage).migrateIfNeeded()
            return tokenStorage
        }

        private fun Context.resetDatabase() = deleteDatabase(TokenDatabase.DB_NAME)
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
        if (token.id != metadata.id) {
            throw IllegalStateException("TokenStorage.add called with different token.id and metadata.id")
        }

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
                biometricTimeout = security.biometricTimeout(),
                encryptionExtras = encryptionResult.encryptionExtras
            )
        )
    }

    override suspend fun remove(id: String) {
        tokenDao.getById(id)?.let { tokenEntity ->
            tokenDao.deleteTokenEntity(tokenEntity)
        }
    }

    override suspend fun replace(token: Token) {
        val tokenEntity = tokenDao.getById(token.id) ?: throw NoSuchElementException()
        val encryptionResult = tokenEncryptionHandler.encrypt(
            token, tokenEntity.security
        )

        val updatedTokenEntity = tokenEntity.copy(
            encryptedToken = encryptionResult.encryptedToken,
            encryptionExtras = encryptionResult.encryptionExtras
        )

        tokenDao.updateTokenEntity(updatedTokenEntity)
    }

    override suspend fun getToken(id: String, promptInfo: PromptInfo?): Token {
        val tokenEntity = tokenDao.getById(id) ?: throw NoSuchElementException()
        return getTokenFromEntity(tokenEntity, promptInfo)
    }

    private suspend fun getTokenFromEntity(tokenEntity: TokenEntity, promptInfo: PromptInfo?): Token {
        return try {
            tokenEncryptionHandler.decrypt(
                tokenEntity.encryptedToken,
                tokenEntity.encryptionExtras,
                tokenEntity.security,
                promptInfo
            )
        } catch (ex: KeyPermanentlyInvalidatedException) {
            val eventCoordinator = AuthFoundationDefaults.eventCoordinator
            eventCoordinator.sendEvent(BiometricKeyInvalidatedEvent(tokenEntity.keyAlias))
            tokenDao.allEntries().forEach {
                if (it.security == tokenEntity.security) {
                    val biometricTokenInvalidatedEvent = BiometricTokenInvalidatedEvent(tokenEntity.id)
                    AuthFoundationDefaults.eventCoordinator.sendEvent(biometricTokenInvalidatedEvent)
                    if (biometricTokenInvalidatedEvent.deleteInvalidatedToken) {
                        tokenDao.deleteTokenEntity(it)
                    }
                }
            }
            throw ex
        }
    }

    private fun Credential.Security.biometricTimeout(): Int? {
        return when (this) {
            is Credential.BiometricSecurity -> this.userAuthenticationTimeout
            else -> null
        }
    }
}
