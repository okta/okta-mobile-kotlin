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

import com.okta.authfoundation.credential.events.NoAccessTokenAvailableEvent
import com.okta.authfoundation.events.EventCoordinator
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

internal class AccessTokenInterceptor(
    private val accessTokenProvider: suspend () -> String?,
    private val eventCoordinator: EventCoordinator,
    private val credential: Credential,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val accessToken = runBlocking {
            accessTokenProvider()
        }

        if (accessToken == null) {
            eventCoordinator.sendEvent(NoAccessTokenAvailableEvent(credential))
            return chain.proceed(chain.request())
        } else {
            val requestBuilder = chain.request().newBuilder()
            requestBuilder.addHeader("authorization", "Bearer $accessToken")
            return chain.proceed(requestBuilder.build())
        }
    }
}
