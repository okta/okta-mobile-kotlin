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
package com.okta.authfoundation.client

import androidx.annotation.VisibleForTesting
import com.okta.authfoundation.client.internal.internalPerformRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

internal object EndpointsFactory {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val prefix: String = "endpoints:"

    private val cacheMutex = Mutex()
    private var cacheInstance: Cache? = null

    suspend fun get(configuration: OidcConfiguration): OAuth2ClientResult<OidcEndpoints> {
        val cache = getOrCreateCache(configuration.cacheFactory)
        val cacheKey = prefix + configuration.discoveryUrl
        val endpoints = withContext(configuration.computeDispatcher) {
            val result = cache.get(cacheKey) ?: return@withContext null
            return@withContext try {
                val serializableOidcEndpoints = configuration.json.decodeFromString(
                    SerializableOidcEndpoints.serializer(), result
                )
                OAuth2ClientResult.Success(serializableOidcEndpoints.asOidcEndpoints())
            } catch (_: Exception) {
                null
            }
        }
        if (endpoints != null) {
            return endpoints
        }
        val request = Request.Builder()
            .url(configuration.discoveryUrl.toHttpUrl())
            .build()
        return configuration.internalPerformRequest(request, { it.isSuccessful }) { responseBody ->
            @OptIn(ExperimentalSerializationApi::class)
            val serializableOidcEndpoints = configuration.json.decodeFromBufferedSource(
                SerializableOidcEndpoints.serializer(), responseBody.peek()
            )
            cache.set(cacheKey, responseBody.readUtf8())
            serializableOidcEndpoints.asOidcEndpoints()
        }
    }

    private suspend fun getOrCreateCache(cacheFactory: suspend () -> Cache): Cache {
        cacheMutex.withLock {
            return cacheInstance ?: run {
                val cache = cacheFactory()
                cacheInstance = cache
                cache
            }
        }
    }

    internal fun reset() {
        cacheInstance = null
    }
}
