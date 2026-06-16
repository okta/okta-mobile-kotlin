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
    const val PREFIX: String = "endpoints:"

    private val cacheMutex = Mutex()
    private var cacheInstance: Cache? = null

    suspend fun get(configuration: OidcConfiguration): OAuth2ClientResult<OidcEndpoints> {
        val overrides = configuration.endpointOverrides

        // If all override fields are provided, skip discovery entirely.
        if (overrides != null && overrides.allFieldsNonNull()) {
            return OAuth2ClientResult.Success(overrides.asOidcEndpoints())
        }

        val cache = getOrCreateCache(configuration.cacheFactory)
        val cacheKey = PREFIX + configuration.discoveryUrl
        val cached =
            withContext(configuration.computeDispatcher) {
                val result = cache.get(cacheKey) ?: return@withContext null
                return@withContext try {
                    val serializableOidcEndpoints =
                        configuration.json.decodeFromString(
                            SerializableOidcEndpoints.serializer(),
                            result
                        )
                    OAuth2ClientResult.Success(serializableOidcEndpoints.asOidcEndpoints())
                } catch (_: Exception) {
                    null
                }
            }
        val discovered =
            if (cached != null) {
                cached
            } else {
                val request =
                    Request
                        .Builder()
                        .url(configuration.discoveryUrl.toHttpUrl())
                        .build()
                configuration.internalPerformRequest(request, { it.isSuccessful }) { responseBody ->
                    @OptIn(ExperimentalSerializationApi::class)
                    val serializableOidcEndpoints =
                        configuration.json.decodeFromBufferedSource(
                            SerializableOidcEndpoints.serializer(),
                            responseBody.peek()
                        )
                    cache.set(cacheKey, responseBody.readUtf8())
                    serializableOidcEndpoints.asOidcEndpoints()
                }
            }

        // Merge partial overrides on top of the discovered endpoints.
        return if (overrides == null) {
            discovered
        } else {
            when (discovered) {
                is OAuth2ClientResult.Success -> OAuth2ClientResult.Success(discovered.result.merge(overrides))
                is OAuth2ClientResult.Error -> discovered
            }
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

    private fun OAuth2EndpointOverrides.allFieldsNonNull(): Boolean =
        authorizationEndpoint != null &&
            tokenEndpoint != null &&
            userInfoEndpoint != null &&
            jwksUri != null &&
            introspectionEndpoint != null &&
            revocationEndpoint != null &&
            endSessionEndpoint != null &&
            deviceAuthorizationEndpoint != null

    private fun OAuth2EndpointOverrides.asOidcEndpoints(): OidcEndpoints =
        OidcEndpoints(
            issuer = authorizationEndpoint!!.toHttpUrl(), // use authorizationEndpoint as a proxy issuer
            authorizationEndpoint = authorizationEndpoint.toHttpUrl(),
            tokenEndpoint = tokenEndpoint!!.toHttpUrl(),
            userInfoEndpoint = userInfoEndpoint!!.toHttpUrl(),
            jwksUri = jwksUri!!.toHttpUrl(),
            introspectionEndpoint = introspectionEndpoint!!.toHttpUrl(),
            revocationEndpoint = revocationEndpoint!!.toHttpUrl(),
            endSessionEndpoint = endSessionEndpoint!!.toHttpUrl(),
            deviceAuthorizationEndpoint = deviceAuthorizationEndpoint!!.toHttpUrl()
        )

    private fun OidcEndpoints.merge(overrides: OAuth2EndpointOverrides): OidcEndpoints =
        OidcEndpoints(
            issuer = issuer,
            authorizationEndpoint = overrides.authorizationEndpoint?.toHttpUrl() ?: authorizationEndpoint,
            tokenEndpoint = overrides.tokenEndpoint?.toHttpUrl() ?: tokenEndpoint,
            userInfoEndpoint = overrides.userInfoEndpoint?.toHttpUrl() ?: userInfoEndpoint,
            jwksUri = overrides.jwksUri?.toHttpUrl() ?: jwksUri,
            introspectionEndpoint = overrides.introspectionEndpoint?.toHttpUrl() ?: introspectionEndpoint,
            revocationEndpoint = overrides.revocationEndpoint?.toHttpUrl() ?: revocationEndpoint,
            endSessionEndpoint = overrides.endSessionEndpoint?.toHttpUrl() ?: endSessionEndpoint,
            deviceAuthorizationEndpoint = overrides.deviceAuthorizationEndpoint?.toHttpUrl() ?: deviceAuthorizationEndpoint
        )
}
