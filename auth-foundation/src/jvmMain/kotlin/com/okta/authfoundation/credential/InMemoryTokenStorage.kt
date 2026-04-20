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

import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.credential.kmp.TokenStorage
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of [TokenStorage] for JVM applications.
 *
 * **Warning**: Tokens stored in this implementation do **not** persist across process restarts
 * and are **not encrypted**. For production deployments requiring encryption at rest,
 * implement [TokenStorage] with your preferred encryption and configure it during
 * client initialization.
 */
class InMemoryTokenStorage : TokenStorage {
    private val tokens = ConcurrentHashMap<String, TokenInfo>()
    private val metadata = ConcurrentHashMap<String, TokenMetadata>()

    override suspend fun allIds(): List<String> = tokens.keys().toList()

    override suspend fun metadata(id: String): TokenMetadata? = metadata[id]

    override suspend fun setMetadata(metadata: TokenMetadata) {
        this.metadata[metadata.id] = metadata
    }

    override suspend fun add(
        token: TokenInfo,
        metadata: TokenMetadata,
    ) {
        tokens[token.id] = token
        this.metadata[token.id] = metadata
    }

    override suspend fun remove(id: String) {
        tokens.remove(id)
        metadata.remove(id)
    }

    override suspend fun replace(token: TokenInfo) {
        if (!tokens.containsKey(token.id)) {
            throw NoSuchElementException("No token with id ${token.id} exists.")
        }
        tokens[token.id] = token
    }

    override suspend fun getToken(id: String): TokenInfo = tokens[id] ?: throw NoSuchElementException("No token with id $id exists.")
}
