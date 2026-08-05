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
package com.okta.oauth2.internal

import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.api.http.ApiRequestMethod

/**
 * Executes a GET request and returns the `Location` response header without following redirects.
 *
 * Used by [com.okta.oauth2.kmp.SessionTokenFlowImpl] to capture the authorization code redirect.
 *
 * @param apiExecutor the HTTP executor.
 * @param url the URL to GET.
 * @return [Result.success] with the Location header value, or [Result.failure] if not present.
 */
internal suspend fun performGetCaptureLocationHeader(
    apiExecutor: ApiExecutor,
    url: String,
): Result<String> =
    runCatching {
        val request =
            object : ApiRequest {
                override fun method(): ApiRequestMethod = ApiRequestMethod.GET

                override fun headers(): Map<String, List<String>> = emptyMap()

                override fun url(): String = url
            }
        val response = apiExecutor.execute(request).getOrThrow()
        response.headers["location"]?.firstOrNull()
            ?: response.headers["Location"]?.firstOrNull()
            ?: throw IllegalStateException("No location header in response.")
    }
