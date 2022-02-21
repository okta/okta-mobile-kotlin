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
package com.okta.authfoundation.client

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.OktaSdkDefaults
import com.okta.authfoundation.events.EventCoordinator
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
 * This class is used to define the configuration, as defined in your Okta application settings, that will be used to interact with the OIDC Authorization Server.
 */
class OidcConfiguration(
    /** The application's client ID. */
    val clientId: String,

    /** The default access scopes required by the client, can be overridden when logging in. */
    val defaultScopes: Set<String>,

    /** The application's redirect URI. */
    val signInRedirectUri: String = "",

    /** The application's end session redirect URI. */
    val signOutRedirectUri: String = "",

    /** The Call.Factory which makes calls to the okta server. */
    okHttpClientFactory: () -> Call.Factory = OktaSdkDefaults.okHttpClientFactory,

    /** The CoroutineDispatcher which should be used for IO bound tasks. */
    val ioDispatcher: CoroutineContext = Dispatchers.IO,

    /** The CoroutineDispatcher which should be used for compute bound tasks. */
    val computeDispatcher: CoroutineContext = Dispatchers.Default,

    /** The OidcClock which is used for all time related functions in the SDK. */
    val clock: OidcClock = OktaSdkDefaults.clock,

    /** The EventCoordinator which the OidcClient should emit events to. */
    val eventCoordinator: EventCoordinator = OktaSdkDefaults.eventCoordinator,

    /** The IdTokenValidator used to validate the Id Token Jwt when tokens are minted. */
    val idTokenValidator: IdTokenValidator = OktaSdkDefaults.idTokenValidator,
) {
    /** The Call.Factory which makes calls to the okta server. */
    val okHttpClient: Call.Factory by lazy {
        addInterceptor(okHttpClientFactory())
    }

    /** The Json object to do the decoding from the okta server responses. */
    @InternalAuthFoundationApi val json: Json = Json { ignoreUnknownKeys = true }

    companion object {
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
