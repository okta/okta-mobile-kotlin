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
package com.okta.oauth2.kmp.internal

import com.okta.authfoundation.api.http.ApiFormRequest
import com.okta.authfoundation.api.http.ApiRequestMethod

internal class ParAuthorizationRequest(
    private val endpoint: String,
    private val formParams: Map<String, String>,
) : ApiFormRequest {
    override fun method(): ApiRequestMethod = ApiRequestMethod.POST

    override fun headers(): Map<String, List<String>> = mapOf("Accept" to listOf("application/json"))

    override fun url(): String = endpoint

    override fun contentType(): String = "application/x-www-form-urlencoded"

    override fun formParameters(): Map<String, List<String>> = formParams.mapValues { (_, value) -> listOf(value) }
}
