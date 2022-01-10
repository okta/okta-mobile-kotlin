/*
 * Copyright 2021-Present Okta, Inc.
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
package com.okta.oidc.kotlin.client

import com.okta.oidc.OktaSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import kotlin.coroutines.CoroutineContext

/**
 * Configuration options for an OidcClient.
 *
 * This class is used to define the configuration, as defined in your Okta application settings, that will be used to interact with the OIDC Authorization server.
 */
class OidcConfiguration(
    /** The application's client ID. */
    val clientId: String,

    /** The access scopes required by the client. */
    val scopes: Set<String>,

    /** The Call.Factory which makes calls to the okta server. */
    okHttpCallFactory: Call.Factory = OktaSdk.okHttpClient,

    /** The CoroutineDispatcher which should be used for IO bound tasks. */
    val ioDispatcher: CoroutineContext = Dispatchers.IO,

    /** The CoroutineDispatcher which should be used for storage access tasks. */
    val storageDispatcher: CoroutineContext = Dispatchers.Default,

    /** The OidcClock which is used for all time related functions in the SDK. */
    val clock: OidcClock = defaultClock(),

    /** The OidcStorage which is used for encryption/storage or tokens. */
    val storage: OidcStorage = defaultStorage(),

    // TODO: Logging?
) {
    /** The Call.Factory which makes calls to the okta server. */
    val okHttpCallFactory: Call.Factory = addInterceptor(okHttpCallFactory)

    /** The Json object to do the decoding from the okta server responses. */
    val json: Json = Json { ignoreUnknownKeys = true }

    companion object {
        private fun defaultClock(): OidcClock {
            return object : OidcClock {
                override fun currentTimeMillis(): Long {
                    return System.currentTimeMillis()
                }
            }
        }

        private fun defaultStorage(): OidcStorage {
            val map = mutableMapOf<String, String>()
            return object : OidcStorage {
                override suspend fun save(key: String, value: String) {
                    map[key] = value
                }

                override suspend fun get(key: String): String? {
                    return map[key]
                }

                override suspend fun delete(key: String) {
                    map.remove(key)
                }
            }
        }

        private fun addInterceptor(callFactory: Call.Factory): Call.Factory {
            if (callFactory is OkHttpClient) {
                return callFactory.newBuilder()
                    .addInterceptor(OidcUserAgentInterceptor)
                    .build()
            }
            return callFactory
        }
    }
}

private object OidcUserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("user-agent", "okta-oidc-kotlin/1.0.0")
            .build()

        return chain.proceed(request)
    }
}
