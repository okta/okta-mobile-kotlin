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
import com.okta.authfoundation.credential.storage.TokenDatabase
import com.okta.authfoundation.credential.storage.TokenEntity

internal class RoomTokenStorage(
    tokenDatabase: TokenDatabase,
    private val tokenEncryptionHandler: TokenEncryptionHandler
) : TokenStorage {
    private val tokenDao = tokenDatabase.tokenDao()

    override suspend fun allIds(): List<String> = tokenDao.allEntries().map { it.id }

    override suspend fun metadata(id: String): Token.Metadata? {
        return tokenDao.getById(id)?.let {
            Token.Metadata(
                it.id,
                it.tags,
                it.payloadData,
                it.isDefault
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
        val (encryptedToken, encryptionExtras) = tokenEncryptionHandler.encrypt(token, security)

        if (metadata.isDefault) {
            tokenDao.allEntries().firstOrNull { it.isDefault }?.let {
                tokenDao.updateTokenEntity(
                    it.copy(isDefault = false)
                )
            }
        }

        tokenDao.insertTokenEntity(
            TokenEntity(
                id = metadata.id,
                encryptedToken,
                tags = metadata.tags,
                payloadData = metadata.payloadData,
                keyAlias = security.keyAlias,
                tokenEncryptionType = TokenEntity.EncryptionType.fromSecurity(security),
                isDefault = metadata.isDefault,
                encryptionExtras = encryptionExtras
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

        val previousDefault = tokenDao.allEntries()
            .firstOrNull { (it.id != id) and it.isDefault }

        val (encryptedToken, encryptionExtras) = tokenEncryptionHandler.encrypt(
            token, security ?: tokenEntity.security
        )

        val updatedTokenEntity = tokenEntity.copy(
            encryptedToken = encryptedToken,
            tags = metadata?.tags ?: tokenEntity.tags,
            payloadData = metadata?.payloadData ?: tokenEntity.payloadData,
            keyAlias = security?.keyAlias ?: tokenEntity.keyAlias,
            tokenEncryptionType = security?.let {
                TokenEntity.EncryptionType.fromSecurity(it)
            } ?: tokenEntity.tokenEncryptionType,
            isDefault = metadata?.isDefault ?: tokenEntity.isDefault,
            encryptionExtras = encryptionExtras
        )

        if (metadata?.isDefault == true && previousDefault != null) {
            tokenDao.updateTokenEntity(updatedTokenEntity, previousDefault.copy(isDefault = false))
        } else {
            tokenDao.updateTokenEntity(updatedTokenEntity)
        }
    }

    override suspend fun getToken(id: String, promptInfo: PromptInfo?): Token {
        val tokenEntity = tokenDao.getById(id) ?: throw NoSuchElementException()
        return getTokenFromEntity(tokenEntity, promptInfo)
    }

    override suspend fun getDefaultToken(promptInfo: PromptInfo?): Token? {
        return tokenDao.allEntries().firstOrNull { it.isDefault }?.let {
            getTokenFromEntity(it, promptInfo)
        }
    }

    private fun getTokenFromEntity(tokenEntity: TokenEntity, promptInfo: PromptInfo?): Token {
        return tokenEncryptionHandler.decrypt(
            tokenEntity.encryptedToken,
            tokenEntity.encryptionExtras,
            tokenEntity.security,
            promptInfo
        )
    }
}