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

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.credential.events.NoAccessTokenAvailableEvent
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.RequestMatchers.doesNotContainHeader
import com.okta.testhelpers.RequestMatchers.header
import com.okta.testhelpers.RequestMatchers.method
import com.okta.testhelpers.RequestMatchers.path
import okhttp3.Request
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.concurrent.atomic.AtomicInteger

class AccessTokenInterceptorTest {
    @get:Rule val oktaRule = OktaRule()

    @Test fun testInterceptorWithNullToken() {
        oktaRule.enqueue(
            method("GET"),
            doesNotContainHeader("authorization"),
            path("/customers"),
        ) { response ->
            response.setBody("[]")
        }

        val credential = mock<Credential>()
        val accessTokenProvider = { null }
        val interceptor = AccessTokenInterceptor(accessTokenProvider, oktaRule.configuration.eventCoordinator, credential)
        val okHttpClient = oktaRule.okHttpClient.newBuilder().addInterceptor(interceptor).build()
        val url = oktaRule.baseUrl.newBuilder().addPathSegment("customers").build()
        val call = okHttpClient.newCall(Request.Builder().url(url).build())
        val response = call.execute()
        assertThat(response.body!!.string()).isEqualTo("[]")
        assertThat(oktaRule.eventHandler).hasSize(1)
        assertThat(oktaRule.eventHandler[0]).isInstanceOf(NoAccessTokenAvailableEvent::class.java)
        val event = oktaRule.eventHandler[0] as NoAccessTokenAvailableEvent
        assertThat(event.credential).isEqualTo(credential)
    }

    @Test fun testInterceptorWithAccessToken() {
        oktaRule.enqueue(
            method("GET"),
            header("authorization", "Bearer Token"),
            path("/customers"),
        ) { response ->
            response.setBody("[]")
        }

        val credential = mock<Credential>()
        val accessTokenProvider = { "Token" }
        val interceptor = AccessTokenInterceptor(accessTokenProvider, oktaRule.configuration.eventCoordinator, credential)
        val okHttpClient = oktaRule.okHttpClient.newBuilder().addInterceptor(interceptor).build()
        val url = oktaRule.baseUrl.newBuilder().addPathSegment("customers").build()
        val call = okHttpClient.newCall(Request.Builder().url(url).build())
        val response = call.execute()
        assertThat(response.body!!.string()).isEqualTo("[]")
        assertThat(oktaRule.eventHandler).isEmpty()
    }

    @Test fun testInterceptorProviderGetsCalledOnEveryRequest() {
        val providerCallCount = AtomicInteger(0)

        val credential = mock<Credential>()
        val accessTokenProvider = {
            providerCallCount.getAndIncrement()
            "Token"
        }
        val interceptor = AccessTokenInterceptor(accessTokenProvider, oktaRule.configuration.eventCoordinator, credential)
        val okHttpClient = oktaRule.okHttpClient.newBuilder().addInterceptor(interceptor).build()
        val url = oktaRule.baseUrl.newBuilder().addPathSegment("customers").build()

        repeat(5) {
            oktaRule.enqueue(
                method("GET"),
                header("authorization", "Bearer Token"),
                path("/customers"),
            ) { response ->
                response.setBody("[]")
            }

            val call = okHttpClient.newCall(Request.Builder().url(url).build())
            val response = call.execute()
            assertThat(response.body!!.string()).isEqualTo("[]")
            assertThat(oktaRule.eventHandler).isEmpty()
        }

        assertThat(providerCallCount.get()).isEqualTo(5)
    }
}
