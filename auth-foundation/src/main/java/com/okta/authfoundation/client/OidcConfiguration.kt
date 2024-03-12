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

import androidx.annotation.VisibleForTesting
import com.okta.authfoundation.AuthFoundationDefaults
import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.client.internal.SdkVersionsRegistry
import com.okta.authfoundation.events.EventCoordinator
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
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
@Serializable
class OidcConfiguration private constructor(
    /** The application's client ID. */
    @property:InternalAuthFoundationApi val clientId: String,

    /** The default access scopes required by the client, can be overridden when logging in. */
    @property:InternalAuthFoundationApi val defaultScope: String,

    /** The `.well-known/openid-configuration` endpoint associated with the Authorization Server. This is used to fetch the [OidcEndpoints]. */
    @property:InternalAuthFoundationApi val discoveryUrl: String,

    /** The Call.Factory which makes calls to the okta server. */
    @property:InternalAuthFoundationApi val okHttpClientFactory: () -> Call.Factory = AuthFoundationDefaults.okHttpClientFactory,

    /** The CoroutineDispatcher which should be used for IO bound tasks. */
    @property:InternalAuthFoundationApi val ioDispatcher: CoroutineContext = AuthFoundationDefaults.ioDispatcher,

    /** The CoroutineDispatcher which should be used for compute bound tasks. */
    @property:InternalAuthFoundationApi val computeDispatcher: CoroutineContext = AuthFoundationDefaults.computeDispatcher,

    /** The OidcClock which is used for all time related functions in the SDK. */
    @property:InternalAuthFoundationApi val clock: OidcClock = AuthFoundationDefaults.clock,

    /** The EventCoordinator which the OidcClient should emit events to. */
    @Transient @property:InternalAuthFoundationApi val eventCoordinator: EventCoordinator = AuthFoundationDefaults.eventCoordinator,

    /** The IdTokenValidator used to validate the Id Token Jwt when tokens are minted. */
    @property:InternalAuthFoundationApi val idTokenValidator: IdTokenValidator = AuthFoundationDefaults.idTokenValidator,

    /** The AccessTokenValidator used to validate the Access Token when tokens are minted. */
    @property:InternalAuthFoundationApi val accessTokenValidator: AccessTokenValidator = AuthFoundationDefaults.accessTokenValidator,

    /** The DeviceSecretValidator used to validate the device secret when tokens are minted. */
    @property:InternalAuthFoundationApi
    val deviceSecretValidator: DeviceSecretValidator = AuthFoundationDefaults.deviceSecretValidator,

    /** The Cache used to optimize network calls by the SDK. */
    @property:InternalAuthFoundationApi
    val cache: Cache = AuthFoundationDefaults.cache,

    /** The CookieJar used for the network calls used by the SDK. */
    @property:InternalAuthFoundationApi
    val cookieJar: CookieJar = AuthFoundationDefaults.cookieJar
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
        /** The `.well-known/openid-configuration` endpoint associated with the Authorization Server. This is
         * used to fetch the [OidcEndpoints]. */
        discoveryUrl: String,
    ) : this(
        clientId = clientId,
        defaultScope = defaultScope,
        discoveryUrl = discoveryUrl,
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
            val userInterceptors = callFactory.interceptors
            callFactory.newBuilder()
                .apply { interceptors().clear() }
                .addInterceptor(OidcUserAgentInterceptor)
                .apply {
                    // Add user interceptors last to prioritize user defined behavior over SDK
                    interceptors().addAll(userInterceptors)
                }
                .cookieJar(cookieJar)
                .build()
        } else {
            callFactory
        }
    }

    /** The Json object to do the decoding from the okta server responses. */
    @Transient @InternalAuthFoundationApi val json: Json = defaultJson()

    internal companion object {
        internal fun defaultJson(): Json = Json { ignoreUnknownKeys = true }
    }
}

@VisibleForTesting
internal object OidcUserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("user-agent", SdkVersionsRegistry.userAgent)
            .build()

        return chain.proceed(request)
    }
}
