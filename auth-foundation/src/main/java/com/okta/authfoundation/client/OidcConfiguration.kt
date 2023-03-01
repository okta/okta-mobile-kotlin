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

import com.okta.authfoundation.AuthFoundationDefaults
import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.client.internal.SdkVersionsRegistry
import com.okta.authfoundation.events.EventCoordinator
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import kotlin.coroutines.CoroutineContext

/**
 * Configuration options for an OidcClient.
 *
 * This class is used to define the configuration, as defined in your Okta application settings, that will be used to interact with the OIDC Authorization Server.
 */
class OidcConfiguration @InternalAuthFoundationApi constructor(
    /** The application's client ID. */
    @property:InternalAuthFoundationApi val clientId: String,

    /** The default access scopes required by the client, can be overridden when logging in. */
    @property:InternalAuthFoundationApi val defaultScope: String,

    /** The Call.Factory which makes calls to the okta server. */
    okHttpClientFactory: () -> Call.Factory,

    /** The CoroutineDispatcher which should be used for IO bound tasks. */
    @property:InternalAuthFoundationApi val ioDispatcher: CoroutineContext,

    /** The CoroutineDispatcher which should be used for compute bound tasks. */
    @property:InternalAuthFoundationApi val computeDispatcher: CoroutineContext,

    /** The OidcClock which is used for all time related functions in the SDK. */
    @property:InternalAuthFoundationApi val clock: OidcClock,

    /** The EventCoordinator which the OidcClient should emit events to. */
    @property:InternalAuthFoundationApi val eventCoordinator: EventCoordinator,

    /** The IdTokenValidator used to validate the Id Token Jwt when tokens are minted. */
    @property:InternalAuthFoundationApi val idTokenValidator: IdTokenValidator,

    /** The AccessTokenValidator used to validate the Access Token when tokens are minted. */
    @property:InternalAuthFoundationApi val accessTokenValidator: AccessTokenValidator,

    /** The DeviceSecretValidator used to validate the device secret when tokens are minted. */
    @property:InternalAuthFoundationApi
    val deviceSecretValidator: DeviceSecretValidator,

    /** The Cache used to optimize network calls by the SDK. */
    @property:InternalAuthFoundationApi
    val cache: Cache,

    /** The CookieJar used for the network calls used by the SDK. */
    @property:InternalAuthFoundationApi
    val cookieJar: CookieJar
) {
    /**
     * Used to create an OidcConfiguration.
     *
     * See [AuthFoundationDefaults] for further customization options.
     */
    constructor(
        /** The application's client ID. */
        clientId: String,
        /** The default access scopes required by the client, can be overridden when logging in. */
        defaultScope: String,
    ) : this(
        clientId = clientId,
        defaultScope = defaultScope,
        okHttpClientFactory = AuthFoundationDefaults.okHttpClientFactory,
        ioDispatcher = AuthFoundationDefaults.ioDispatcher,
        computeDispatcher = AuthFoundationDefaults.computeDispatcher,
        clock = AuthFoundationDefaults.clock,
        eventCoordinator = AuthFoundationDefaults.eventCoordinator,
        idTokenValidator = AuthFoundationDefaults.idTokenValidator,
        accessTokenValidator = AuthFoundationDefaults.accessTokenValidator,
        deviceSecretValidator = AuthFoundationDefaults.deviceSecretValidator,
        cache = AuthFoundationDefaults.cache,
        cookieJar = AuthFoundationDefaults.cookieJar,
    )

    /** The Call.Factory which makes calls to the okta server. */
    @property:InternalAuthFoundationApi val okHttpClient: Call.Factory by lazy {
        val callFactory = okHttpClientFactory()
        if (callFactory is OkHttpClient) {
            callFactory.newBuilder()
                .addInterceptor(OidcUserAgentInterceptor)
                .cookieJar(cookieJar)
                .build()
        } else {
            callFactory
        }
    }

    /** The Json object to do the decoding from the okta server responses. */
    @InternalAuthFoundationApi val json: Json = defaultJson()

    internal companion object {
        internal fun defaultJson(): Json = Json { ignoreUnknownKeys = true }
    }
}

private object OidcUserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("user-agent", SdkVersionsRegistry.userAgent)
            .build()

        return chain.proceed(request)
    }
}
