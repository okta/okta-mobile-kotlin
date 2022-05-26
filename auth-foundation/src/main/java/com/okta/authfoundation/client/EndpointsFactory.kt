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
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl
import okhttp3.Request
import okio.buffer
import okio.source

internal object EndpointsFactory {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val prefix: String = "endpoints:"

    suspend fun get(configuration: OidcConfiguration, discoveryUrl: HttpUrl): OidcClientResult<OidcEndpoints> {
        val cacheKey = prefix + discoveryUrl.toString()
        val endpoints = withContext(configuration.computeDispatcher) {
            val result = configuration.cache.get(cacheKey) ?: return@withContext null
            return@withContext try {
                val serializableOidcEndpoints = configuration.json.decodeFromString(
                    SerializableOidcEndpoints.serializer(), result
                )
                OidcClientResult.Success(serializableOidcEndpoints.asOidcEndpoints())
            } catch (_: Exception) {
                null
            }
        }
        if (endpoints != null) {
            return endpoints
        }
        val request = Request.Builder()
            .url(discoveryUrl)
            .build()
        return configuration.internalPerformRequest(request, { it.isSuccessful }) { responseBody ->
            val buffer = responseBody.source().buffer()
            @OptIn(ExperimentalSerializationApi::class)
            val serializableOidcEndpoints = configuration.json.decodeFromStream(
                SerializableOidcEndpoints.serializer(), buffer.peek().inputStream()
            )
            configuration.cache.set(cacheKey, buffer.readUtf8())
            serializableOidcEndpoints.asOidcEndpoints()
        }
    }
}
