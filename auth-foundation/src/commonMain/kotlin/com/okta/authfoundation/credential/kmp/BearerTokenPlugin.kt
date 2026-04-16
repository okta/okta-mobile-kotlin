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
package com.okta.authfoundation.credential.kmp

import com.okta.authfoundation.credential.events.NoAccessTokenAvailableEvent
import com.okta.authfoundation.events.Event
import io.ktor.client.plugins.api.createClientPlugin
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Configuration for the [BearerTokenPlugin].
 *
 * Provide an [accessTokenProvider] that returns the access token to attach, or `null` if
 * no valid token is available. The provider is invoked on every request.
 *
 * Optionally set [eventsFlow] so the plugin can emit [NoAccessTokenAvailableEvent] when
 * the provider returns `null`.
 */
class BearerTokenPluginConfig {
    /**
     * A suspend lambda that returns the current valid access token, or `null` if none is available.
     *
     * Typical usage with a [Credential]:
     * ```kotlin
     * accessTokenProvider = { credential.refreshIfExpired().getOrNull()?.token?.accessToken }
     * ```
     */
    var accessTokenProvider: suspend () -> String? = { null }

    /**
     * Optional events flow for emitting [NoAccessTokenAvailableEvent].
     * When set, a [NoAccessTokenAvailableEvent] is emitted whenever [accessTokenProvider] returns `null`.
     */
    var eventsFlow: MutableSharedFlow<Event>? = null

    /**
     * Optional credential identifier for [NoAccessTokenAvailableEvent].
     */
    var credential: Credential? = null
}

/**
 * A Ktor HttpClient plugin that attaches Bearer tokens to outgoing requests.
 *
 * On each request, calls the configured [BearerTokenPluginConfig.accessTokenProvider] and,
 * if a non-empty token is returned, adds an `Authorization: Bearer {token}` header.
 * If no token is available and [BearerTokenPluginConfig.eventsFlow] is configured, emits
 * a [NoAccessTokenAvailableEvent].
 *
 * This is the KMP equivalent of the Android-only `AccessTokenInterceptor` for OkHttp.
 *
 * Example usage with a [Credential]:
 * ```kotlin
 * val client = HttpClient {
 *     install(BearerTokenPlugin) {
 *         credential = myCredential
 *         eventsFlow = myEventsFlow
 *         accessTokenProvider = {
 *             myCredential.refreshIfExpired().getOrNull()?.token?.accessToken
 *         }
 *     }
 * }
 * ```
 *
 * Example usage with a fixed token:
 * ```kotlin
 * val client = HttpClient {
 *     install(BearerTokenPlugin) {
 *         accessTokenProvider = { "my-access-token" }
 *     }
 * }
 * ```
 */
val BearerTokenPlugin =
    createClientPlugin("BearerTokenPlugin", ::BearerTokenPluginConfig) {
        val provider = pluginConfig.accessTokenProvider
        val events = pluginConfig.eventsFlow
        val cred = pluginConfig.credential

        onRequest { request, _ ->
            val token = provider()
            if (!token.isNullOrEmpty()) {
                request.headers.append("Authorization", "Bearer $token")
            } else if (cred != null) {
                events?.tryEmit(NoAccessTokenAvailableEvent(cred))
            }
        }
    }
