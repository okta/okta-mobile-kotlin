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
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.time.Duration

/**
 * Immutable configuration for the Auth Foundation SDK.
 *
 * This class holds all the primitive configuration options for the SDK. Instances are created
 * using [AuthFoundationBuilder].
 *
 * @see AuthFoundationBuilder
 */
class AuthFoundationConfiguration internal constructor(
    val apiExecutor: ApiExecutor,
    val ioDispatcher: CoroutineDispatcher,
    val computeDispatcher: CoroutineDispatcher,
    val authFoundationCache: AuthFoundationCache,
    val loginCancellationDebounceTime: Duration,
    val encryptionKeyAlias: String,
)
