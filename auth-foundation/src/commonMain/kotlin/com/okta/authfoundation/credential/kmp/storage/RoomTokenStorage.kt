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
package com.okta.authfoundation.credential.kmp.storage

import com.okta.authfoundation.client.OAuth2ClientConfiguration
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.credential.TokenMetadata
import com.okta.authfoundation.credential.kmp.TokenData
import com.okta.authfoundation.credential.kmp.TokenEncryptionHandler
import com.okta.authfoundation.credential.kmp.TokenStorage

/**
 * Cross-platform [TokenStorage] implementation backed by Room.
 *
 * The [encryptionHandler] encrypts/decrypts the access token before persistence.
 * The [configuration] is used to reconstruct [TokenData] when reading tokens.
 *
 * @param database the [TokenDatabase] instance (created via platform-specific builders).
 * @param encryptionHandler the encryption strategy for token data.
 * @param configuration the [OAuth2ClientConfiguration] used to reconstruct tokens.
 */
class RoomTokenStorage(
    database: TokenDatabase,
    private val encryptionHandler: TokenEncryptionHandler,
    private val configuration: OAuth2ClientConfiguration,
) : TokenStorage {
    private val tokenDao = database.tokenDao()

    override suspend fun allIds(): Result<List<String>> =
        runCatching {
            tokenDao.allEntries().map { it.id }
        }

    override suspend fun metadata(id: String): Result<TokenMetadata?> =
        runCatching {
            tokenDao.getById(id)?.let { entity ->
                TokenMetadata(
                    id = entity.id,
                    tags = entity.tags,
                    payloadData = entity.payloadData
                )
            }
        }

    override suspend fun setMetadata(metadata: TokenMetadata): Result<Unit> =
        runCatching {
            val entity =
                tokenDao.getById(metadata.id)
                    ?: throw NoSuchElementException("No token with id ${metadata.id} exists.")
            tokenDao.updateTokenEntity(
                entity.copy(
                    tags = metadata.tags,
                    payloadData = metadata.payloadData
                )
            )
        }

    override suspend fun add(
        token: TokenInfo,
        metadata: TokenMetadata,
    ): Result<Unit> =
        runCatching {
            val encryptionResult = encryptionHandler.encrypt(token.accessToken.encodeToByteArray())
            val issuedAt = (token as? TokenData)?.issuedAt ?: 0L

            val entity =
                TokenEntity(
                    id = token.id,
                    clientId = token.clientId,
                    issuerUrl = token.issuerUrl,
                    tokenType = token.tokenType,
                    expiresIn = token.expiresIn,
                    accessToken = encryptionResult.ciphertext,
                    scope = token.scope,
                    refreshToken = token.refreshToken,
                    idToken = token.idToken,
                    deviceSecret = token.deviceSecret,
                    issuedTokenType = token.issuedTokenType,
                    issuedAt = issuedAt,
                    tags = metadata.tags,
                    payloadData = metadata.payloadData,
                    encryptionExtras = encryptionResult.encryptionExtras
                )
            tokenDao.insertTokenEntity(entity)
        }

    override suspend fun remove(id: String): Result<Unit> =
        runCatching {
            tokenDao.getById(id)?.let { entity ->
                tokenDao.deleteTokenEntity(entity)
            }
        }

    override suspend fun replace(token: TokenInfo): Result<Unit> =
        runCatching {
            val existingEntity =
                tokenDao.getById(token.id)
                    ?: throw NoSuchElementException("No token with id ${token.id} exists.")

            val encryptionResult = encryptionHandler.encrypt(token.accessToken.encodeToByteArray())
            val issuedAt = (token as? TokenData)?.issuedAt ?: existingEntity.issuedAt

            val updatedEntity =
                existingEntity.copy(
                    tokenType = token.tokenType,
                    expiresIn = token.expiresIn,
                    accessToken = encryptionResult.ciphertext,
                    scope = token.scope,
                    refreshToken = token.refreshToken,
                    idToken = token.idToken,
                    deviceSecret = token.deviceSecret,
                    issuedTokenType = token.issuedTokenType,
                    issuedAt = issuedAt,
                    encryptionExtras = encryptionResult.encryptionExtras
                )
            tokenDao.updateTokenEntity(updatedEntity)
        }

    override suspend fun getToken(id: String): Result<TokenInfo> =
        runCatching {
            val entity =
                tokenDao.getById(id)
                    ?: throw NoSuchElementException("No token with id $id exists.")

            val decryptedAccessToken =
                encryptionHandler
                    .decrypt(
                        entity.accessToken,
                        entity.encryptionExtras
                    ).decodeToString()

            TokenData(
                id = entity.id,
                tokenType = entity.tokenType,
                expiresIn = entity.expiresIn,
                accessToken = decryptedAccessToken,
                scope = entity.scope,
                refreshToken = entity.refreshToken,
                idToken = entity.idToken,
                deviceSecret = entity.deviceSecret,
                issuedTokenType = entity.issuedTokenType,
                configuration = configuration,
                issuedAt = entity.issuedAt
            )
        }
}
