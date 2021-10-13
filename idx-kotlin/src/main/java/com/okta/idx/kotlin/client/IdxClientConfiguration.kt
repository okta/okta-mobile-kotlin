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
package com.okta.idx.kotlin.client

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import kotlin.coroutines.CoroutineContext

/**
 * Configuration options for an IdxClient.
 *
 * This class is used to define the configuration, as defined in your Okta application settings, that will be used to interact with the Okta Identity Engine API.
 */
class IdxClientConfiguration(
    /** The issuer URL. */
    val issuer: HttpUrl,

    /** The application's client ID. */
    val clientId: String,

    /** The access scopes required by the client. */
    val scopes: Set<String>,

    /** The application's redirect URI. */
    val redirectUri: String,

    /** The Call.Factory which makes calls to the okta server. */
    okHttpCallFactory: Call.Factory = defaultCallFactory(),

    /** The CoroutineDispatcher which should be used for IO bound tasks. */
    val ioDispatcher: CoroutineContext = Dispatchers.IO,

    /** The CoroutineDispatcher which should be used for CPU bound tasks. */
    val computationDispatcher: CoroutineContext = Dispatchers.Default,
) {
    /** The Call.Factory which makes calls to the okta server. */
    val okHttpCallFactory: Call.Factory = addInterceptor(okHttpCallFactory)

    /** The Json object to do the decoding from the okta server responses. */
    internal val json: Json = Json { ignoreUnknownKeys = true }

    companion object {
        private fun defaultCallFactory(): Call.Factory {
            return OkHttpClient.Builder()
                .build()
        }

        private fun addInterceptor(callFactory: Call.Factory): Call.Factory {
            if (callFactory is OkHttpClient) {
                return callFactory.newBuilder()
                    .addInterceptor(IdxUserAgentInterceptor)
                    .build()
            }
            return callFactory
        }
    }
}

private object IdxUserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("user-agent", "okta-idx-kotlin/1.0.0")
            .build()

        return chain.proceed(request)
    }
}
