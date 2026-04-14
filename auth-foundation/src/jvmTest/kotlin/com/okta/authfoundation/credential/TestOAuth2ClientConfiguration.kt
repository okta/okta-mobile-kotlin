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
package com.okta.authfoundation.credential

import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.api.http.ApiResponse
import com.okta.authfoundation.client.Cache
import com.okta.authfoundation.client.OAuth2ClientConfiguration
import com.okta.authfoundation.client.OidcClock

object TestOAuth2ClientConfiguration {
    fun create(
        clientId: String = "test-client-id",
        issuerUrl: String = "https://test.okta.com",
    ): OAuth2ClientConfiguration =
        OAuth2ClientConfiguration(
            clientId = clientId,
            defaultScope = "openid profile",
            issuerUrl = issuerUrl,
            apiExecutor = NoOpApiExecutor,
            clock = TestClock(),
            json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
            cache = NoOpCache,
            authorizationServerId = null,
            clientSecret = null,
            acrValues = null
        )

    private object NoOpApiExecutor : ApiExecutor {
        override suspend fun execute(request: ApiRequest): Result<ApiResponse> = Result.failure(UnsupportedOperationException("Test ApiExecutor should not be called"))
    }

    private object NoOpCache : Cache {
        override fun get(key: String): String? = null

        override fun set(
            key: String,
            value: String,
        ) {}
    }

    private class TestClock : OidcClock {
        override fun currentTimeEpochSecond(): Long = 1000000L
    }
}
