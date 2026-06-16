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
package com.okta.oauth2.kmp.jvm

import com.okta.authfoundation.client.TokenInfo
import com.okta.oauth2.kmp.DeviceAuthorizationFlowContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import com.okta.authfoundation.client.kmp.OAuth2Client as KmpOAuth2Client
import com.okta.oauth2.kmp.DeviceAuthorizationFlow as KotlinDeviceAuthorizationFlow

/**
 * A Java-friendly wrapper around the Kotlin [KotlinDeviceAuthorizationFlow].
 *
 * This class exposes async methods returning [CompletableFuture] so Java consumers
 * can use the Device Authorization flow without dealing with Kotlin coroutines.
 *
 * Typical Java usage:
 * ```java
 * DeviceAuthorizationFlow flow = new DeviceAuthorizationFlow(kmpClient);
 * DeviceAuthorizationFlowContext ctx = flow.start(scope).get();
 * TokenInfo token = flow.resume(ctx).get();
 * flow.close();
 * ```
 *
 * Must be [closed][close] when no longer needed to release coroutine resources.
 *
 * @param delegate the underlying Kotlin [KotlinDeviceAuthorizationFlow] instance.
 */
class DeviceAuthorizationFlow(
    private val delegate: KotlinDeviceAuthorizationFlow,
) : Closeable {
    /**
     * Creates a [DeviceAuthorizationFlow] backed by the given [KmpOAuth2Client].
     *
     * @param client the KMP OAuth2 client to use for the Device Authorization flow.
     */
    constructor(client: KmpOAuth2Client) : this(KotlinDeviceAuthorizationFlow(client))

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Initiates the device authorization flow asynchronously.
     *
     * @param scope the scopes to request. Defaults to the client's configured default scope when null.
     * @return a [CompletableFuture] that completes with [DeviceAuthorizationFlowContext] on success,
     *   or completes exceptionally on failure.
     */
    fun start(scope: String? = null): CompletableFuture<DeviceAuthorizationFlowContext> =
        coroutineScope.future {
            delegate.start(scope).getOrThrow()
        }

    /**
     * Polls for authorization completion asynchronously.
     *
     * @param flowContext the context returned from [start].
     * @return a [CompletableFuture] that completes with [TokenInfo] on success,
     *   or completes exceptionally on failure or timeout.
     */
    fun resume(flowContext: DeviceAuthorizationFlowContext): CompletableFuture<TokenInfo> =
        coroutineScope.future {
            delegate.resume(flowContext).getOrThrow()
        }

    override fun close() {
        coroutineScope.cancel()
    }
}
