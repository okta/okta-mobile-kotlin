/*
 * Copyright 2021-Present Okta, Inc.
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
package com.okta.idx.kotlin.client

import com.google.common.truth.Truth.assertThat
import com.okta.idx.kotlin.dto.v1.InteractResponse
import com.okta.idx.kotlin.infrastructure.network.NetworkRule
import com.okta.idx.kotlin.infrastructure.network.RequestMatchers.body
import com.okta.idx.kotlin.infrastructure.network.RequestMatchers.header
import com.okta.idx.kotlin.infrastructure.network.RequestMatchers.path
import com.okta.idx.kotlin.infrastructure.testBodyFromFile
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Rule
import org.junit.Test

class NetworkingTest {
    @get:Rule val networkRule = NetworkRule()

    private fun getConfiguration() = IdxClientConfiguration(
        issuer = "https://test.okta.com/oauth2/default".toHttpUrl(),
        clientId = "test",
        scopes = setOf("openid", "email", "profile", "offline_access"),
        redirectUri = "test.okta.com/login",
        okHttpCallFactory = networkRule.okHttpClient(),
    )

    @Test fun testPerformRequest(): Unit = runBlocking {
        val body = "client_id=test&redirect_uri=test.okta.com%2Flogin"
        networkRule.enqueue(
            path("/oauth2/default/v1/interact"),
            body(body),
            header("user-agent", "okta-idx-kotlin/1.0.0"),
        ) { response ->
            response.testBodyFromFile("client/interactResponse.json")
        }

        val configuration = getConfiguration()

        val urlBuilder = configuration.issuer.newBuilder()
            .addPathSegments("v1/interact")
        val formBody = FormBody.Builder()
            .add("client_id", configuration.clientId)
            .add("redirect_uri", configuration.redirectUri)
            .build()
        val request = Request.Builder()
            .url(urlBuilder.build())
            .post(formBody)
            .build()

        val result = configuration.performRequest<InteractResponse, String>(request) {
            it.interactionHandle
        }
        assertThat((result as IdxClientResult.Success<String>).result).isEqualTo("029ZAB")
    }

    @Test fun testPerformRequestErrorStatusCodesCallMapper(): Unit = runBlocking {
        networkRule.enqueue(path("/oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("client/interactResponse.json")
            response.setResponseCode(401)
        }

        val configuration = getConfiguration()

        val urlBuilder = configuration.issuer.newBuilder()
            .addPathSegments("v1/interact")
        val request = Request.Builder()
            .url(urlBuilder.build())
            .build()

        val result = configuration.performRequest<InteractResponse, String>(request) {
            it.interactionHandle
        }
        assertThat((result as IdxClientResult.Success<String>).result).isEqualTo("029ZAB")
    }

    @Test fun testPerformRequestExceptionInMapper(): Unit = runBlocking {
        networkRule.enqueue(path("/oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("client/interactResponse.json")
        }

        val configuration = getConfiguration()

        val urlBuilder = configuration.issuer.newBuilder()
            .addPathSegments("v1/interact")
        val request = Request.Builder()
            .url(urlBuilder.build())
            .build()

        val result = configuration.performRequest<InteractResponse, String>(request) {
            throw IllegalArgumentException("Test Exception From Mapper")
        }
        val message = (result as IdxClientResult.Error<String>).exception.message
        assertThat(message).isEqualTo("Test Exception From Mapper")
    }

    @Test fun testPerformRequestNetworkFailure(): Unit = runBlocking {
        networkRule.enqueue(path("/oauth2/default/v1/interact")) { response ->
            response.socketPolicy = SocketPolicy.DISCONNECT_AT_START
        }

        val configuration = getConfiguration()

        val urlBuilder = configuration.issuer.newBuilder()
            .addPathSegments("v1/interact")
        val request = Request.Builder()
            .url(urlBuilder.build())
            .build()

        val result = configuration.performRequest<InteractResponse, String>(request) {
            it.interactionHandle
        }
        val message = (result as IdxClientResult.Error<String>).exception.message
        assertThat(message).contains("EOF")
    }
}
