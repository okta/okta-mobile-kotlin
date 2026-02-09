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

import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.KtorHttpExecutor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A builder used to create an instance of [AuthFoundationConfiguration] used by the Auth Foundation SDK.
 *
 * This class provides a fluent API for setting configuration options.
 * An instance of this builder should be created using the [create] factory method.
 *
 * Example usage:
 * ```kotlin
 * AuthFoundationBuilder.create() {
 *     clock = myCustomClock
 * }
 * ```
 */
class AuthFoundationBuilder private constructor() {
    /**
     * The ApiExecutor used for making network requests.
     *
     * If not specified, defaults to KtorHttpExecutor.
     */
    var apiExecutor: ApiExecutor = KtorHttpExecutor()

    /**
     * The CoroutineDispatcher which should be used for IO bound tasks.
     *
     * If not specified, defaults to Dispatchers.IO.
     */
    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /**
     * The CoroutineDispatcher which should be used for compute bound tasks.
     *
     * If not specified, defaults to Dispatchers.Default.
     */
    var computeDispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * The clock used for time-sensitive operations.
     *
     * If not specified, defaults to a clock that returns the current system time.
     */
    var clock: OidcClock = OidcClock { Clock.System.now().epochSeconds }

    /**
     * Cache used to store the well-known configuration endpoints.
     *
     * If not specified, defaults to in memory caching.
     */
    var authFoundationCache: AuthFoundationCache = InMemoryCache()

    /**
     * The wait time until the web login flow is canceled after receiving an empty redirect response.
     *
     * This can resolve some issues caused by older devices when invalid redirect results
     * are returned from the older browser. When this is set to a non-zero value, it introduces
     * a delay to all redirects when an error is received.
     *
     * If not specified, defaults to 0 seconds.
     */
    var loginCancellationDebounceTime: Duration = 0.seconds

    /**
     * The keyAlias for the encryption key used for encrypting stored Token objects.
     *
     * If not specified, defaults to "com.okta.authfoundation.rsakey".
     */
    var encryptionKeyAlias: String = "com.okta.authfoundation.rsakey"

    companion object {
        /**
         * Creates an [AuthFoundationConfiguration] using the builder pattern.
         *
         * @param buildAction A lambda with an [AuthFoundationBuilder] receiver to configure
         *                    the SDK's parameters.
         * @return A [Result] containing the configured [AuthFoundationConfiguration] on success,
         *         or an exception on failure.
         */
        suspend fun create(buildAction: (AuthFoundationBuilder.() -> Unit)? = null): Result<AuthFoundationConfiguration> =
            runCatching {
                val builder = AuthFoundationBuilder()
                buildAction?.invoke(builder)

                AuthFoundationConfiguration(
                    apiExecutor = builder.apiExecutor,
                    ioDispatcher = builder.ioDispatcher,
                    computeDispatcher = builder.computeDispatcher,
                    authFoundationCache = builder.authFoundationCache,
                    loginCancellationDebounceTime = builder.loginCancellationDebounceTime,
                    encryptionKeyAlias = builder.encryptionKeyAlias
                )
            }
    }
}
