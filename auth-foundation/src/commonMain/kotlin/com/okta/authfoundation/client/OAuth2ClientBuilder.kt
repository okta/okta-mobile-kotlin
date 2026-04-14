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

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.KtorHttpExecutor
import com.okta.authfoundation.client.internal.EndpointDiscovery
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.authfoundation.util.CoalescingOrchestrator
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/**
 * Builder for creating a [OAuth2Client].
 *
 * Use the [create] factory method to construct an instance.
 *
 * ```kotlin
 * val client = OAuth2ClientBuilder.create(
 *     issuerUrl = "https://your-okta-domain.okta.com/oauth2/default",
 *     clientId = "your-client-id",
 *     scope = "openid profile offline_access",
 * ) {
 *     clock = OidcClock { customTimeSource() }
 * }.getOrThrow()
 * ```
 */
class OAuth2ClientBuilder private constructor(
    private val issuerUrl: String,
    private val clientId: String,
    private val scope: String,
) {
    /** The HTTP executor used for all network requests. */
    var apiExecutor: ApiExecutor = KtorHttpExecutor()

    /** The clock used for time-related operations. */
    var clock: OidcClock = OidcClock { Clock.System.now().epochSeconds }

    /** The dispatcher for IO-bound operations. */
    var ioDispatcher: CoroutineContext = Dispatchers.IO

    /** The dispatcher for compute-bound operations. */
    var computeDispatcher: CoroutineContext = Dispatchers.Default

    /** The JSON serializer for encoding/decoding responses. */
    var json: Json = Json { ignoreUnknownKeys = true }

    /** The cache for optimizing network calls. */
    var cache: Cache = NoOpCache()

    /** Optional authorization server ID. */
    var authorizationServerId: String? = null

    /** Optional client secret for confidential clients. */
    var clientSecret: String? = null

    /** Optional ACR values. */
    var acrValues: String? = null

    companion object {
        /**
         * Creates a [OAuth2Client] with the given parameters and optional customization.
         *
         * @param issuerUrl the Authorization Server URL (must use HTTPS).
         * @param clientId the application's client ID (must not be blank).
         * @param scope the default access scopes (must not be blank).
         * @param buildAction optional configuration block for customizing builder properties.
         * @return [Result] containing the built [OAuth2Client], or a failure with
         *   [IllegalArgumentException] if validation fails.
         */
        fun create(
            issuerUrl: String,
            clientId: String,
            scope: List<String>,
            buildAction: (OAuth2ClientBuilder.() -> Unit)? = null,
        ): Result<OAuth2Client> =
            runCatching {
                require(
                    runCatching {
                        val url = Url(issuerUrl)
                        url.protocol == URLProtocol.HTTPS && url.host.isNotBlank()
                    }.getOrDefault(false)
                ) { "issuerUrl must be a valid https URL." }

                require(clientId.isNotBlank()) { "clientId must be set and not empty." }
                require(scope.isNotEmpty()) { "scope must be set and not empty." }

                val builder = OAuth2ClientBuilder(issuerUrl, clientId, scope.joinToString(" "))
                buildAction?.invoke(builder)
                val config = builder.build()
                val discovery = EndpointDiscovery(config)
                @OptIn(InternalAuthFoundationApi::class)
                OAuth2Client(
                    config,
                    CoalescingOrchestrator(
                        factory = { discovery.discover() },
                        keepDataInMemory = { it is OAuth2ClientResult.Success }
                    )
                )
            }
    }

    private fun build(): OAuth2ClientConfiguration =
        OAuth2ClientConfiguration(
            clientId = clientId,
            defaultScope = scope,
            issuerUrl = issuerUrl.trimEnd('/'),
            apiExecutor = apiExecutor,
            clock = clock,
            json = json,
            cache = cache,
            authorizationServerId = authorizationServerId,
            clientSecret = clientSecret,
            acrValues = acrValues
        )
}
