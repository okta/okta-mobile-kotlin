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
package com.okta.authfoundation.api.http

/**
 * A simple abstraction for executing network requests defined by [ApiRequest] and returning [ApiResponse].
 *
 * Implementations of this interface are responsible for performing the actual network operations,
 * handling responses, retries, and error handling.
 */
interface ApiExecutor {
    /**
     * Executes the given network request asynchronously.
     *
     * Implementations of this method should be thread-safe, and should not block the calling thread.
     * All exceptions thrown during the execution of the request should be caught and returned as a [Result.Failure].
     *
     * @param request The [ApiRequest] to be executed.
     * @return A [Result] containing either the successful [ApiResponse] or an [Exception].
     */
    suspend fun execute(request: ApiRequest): Result<ApiResponse>
}
