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

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.client.internal.performRequest
import com.okta.authfoundation.client.internal.performRequestNonJson
import com.okta.authfoundation.credential.Credential
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.RequestMatchers.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import java.lang.IllegalStateException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NetworkingTest {
    @get:Rule val oktaRule = OktaRule()

    @Test fun testPerformRequest(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/test"),
        ) { response ->
            response.setBody("""{"foo":"bar"}""")
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOidcClient().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }
        assertThat((result as OidcClientResult.Success<String>).result).isEqualTo("bar")
    }

    @Test fun testPerformRequestErrorStatusCodesCallMapper(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/test"),
        ) { response ->
            response.setBody("""{"foo":"bar"}""")
            response.setResponseCode(401)
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOidcClient().performRequest(JsonObject.serializer(), request, { true }) {
            it["foo"]!!.jsonPrimitive.content
        }
        assertThat((result as OidcClientResult.Success<String>).result).isEqualTo("bar")
    }

    @Test fun testPerformRequestExceptionInMapper(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/test"),
        ) { response ->
            response.setBody("""{"foo":"bar"}""")
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOidcClient().performRequest<JsonObject, String>(JsonObject.serializer(), request, { true }) {
            throw IllegalArgumentException("Test Exception From Mapper")
        }
        val message = (result as OidcClientResult.Error<String>).exception.message
        assertThat(message).isEqualTo("Test Exception From Mapper")
    }

    @Test fun testPerformRequestNetworkFailure(): Unit = runBlocking {
        oktaRule.enqueue(path("/test")) { response ->
            response.socketPolicy = SocketPolicy.DISCONNECT_AT_START
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOidcClient().performRequest(JsonObject.serializer(), request, { true }) {
            it["foo"]!!.jsonPrimitive.content
        }
        val message = (result as OidcClientResult.Error<String>).exception.message
        assertThat(message).contains("EOF")
    }

    @Test fun testPerformRequestErrorStatusReturnsError(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/test"),
        ) { response ->
            response.setBody("""{"foo":"bar"}""")
            response.setResponseCode(401)
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOidcClient().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }
        val exception = (result as OidcClientResult.Error<String>).exception
        assertThat(exception).isInstanceOf(OidcClientResult.Error.HttpResponseException::class.java)
        val httpResponseException = exception as OidcClientResult.Error.HttpResponseException
        assertThat(httpResponseException.responseCode).isEqualTo(401)
        assertThat(httpResponseException.error).isNull()
        assertThat(httpResponseException.errorDescription).isNull()
    }

    @Test fun testPerformRequestErrorStatusReturnsErrorDeserialized(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/test"),
        ) { response ->
            response.setBody("""{"error":"bar","error_description":"baz"}""")
            response.setResponseCode(401)
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOidcClient().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }
        val exception = (result as OidcClientResult.Error<String>).exception
        assertThat(exception).isInstanceOf(OidcClientResult.Error.HttpResponseException::class.java)
        val httpResponseException = exception as OidcClientResult.Error.HttpResponseException
        assertThat(httpResponseException.responseCode).isEqualTo(401)
        assertThat(httpResponseException.error).isEqualTo("bar")
        assertThat(httpResponseException.errorDescription).isEqualTo("baz")
    }

    @Test fun testPerformRequestNonJson(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/test"),
        ) { response ->
            response.setBody("")
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOidcClient().performRequestNonJson(request)
        val result2 = (result as OidcClientResult.Success<Unit>).result
        assertThat(result2).isEqualTo(Unit)
    }

    @Test fun testPerformRequestFailsWhileReadingResponse(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/test"),
        ) { response ->
            response.setBody("""{"foo}""") // Intentionally invalid json.
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOidcClient().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }
        val exception = (result as OidcClientResult.Error<String>).exception
        assertThat(exception).isInstanceOf(SerializationException::class.java)
    }

    @Test fun testPerformRequestFailsWhileReadingErrorResponse(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/test"),
        ) { response ->
            response.setBody("""{"foo}""") // Intentionally invalid json.
            response.setResponseCode(401)
            response.socketPolicy = SocketPolicy.DISCONNECT_AT_END
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOidcClient().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }
        val exception = (result as OidcClientResult.Error<String>).exception
        assertThat(exception).isInstanceOf(OidcClientResult.Error.HttpResponseException::class.java)
        assertThat(exception).hasMessageThat().isEqualTo("HTTP Error: status code - 401")
    }

    @Test fun testPerformRequestHasNoTagWithNoCredential(): Unit = runBlocking {
        val interceptor = RecordingInterceptor()
        val configuration = OidcConfiguration(
            clientId = "unit_test_client_id",
            defaultScope = "openid email profile offline_access",
            okHttpClientFactory = {
                val builder = oktaRule.okHttpClient.newBuilder()
                builder.addInterceptor(interceptor)
                builder.build()
            }
        )
        val oidcClient = OidcClient.create(configuration, oktaRule.createEndpoints())
        oktaRule.enqueue(
            path("/test"),
        ) { response ->
            response.setBody("""{"foo":"bar"}""")
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oidcClient.performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }
        assertThat((result as OidcClientResult.Success<String>).result).isEqualTo("bar")
        assertThat(interceptor.request?.tag(Credential::class.java)).isNull()
    }

    @Test fun testPerformRequestHasTagWhenCredentialIsAttachedToOidcClient(): Unit = runBlocking {
        val interceptor = RecordingInterceptor()
        val configuration = OidcConfiguration(
            clientId = "unit_test_client_id",
            defaultScope = "openid email profile offline_access",
            okHttpClientFactory = {
                val builder = oktaRule.okHttpClient.newBuilder()
                builder.addInterceptor(interceptor)
                builder.build()
            }
        )
        val credential = mock<Credential>()
        val oidcClient = OidcClient.create(configuration, oktaRule.createEndpoints())
            .withCredential(credential)
        oktaRule.enqueue(
            path("/test"),
        ) { response ->
            response.setBody("""{"foo":"bar"}""")
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oidcClient.performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }
        assertThat((result as OidcClientResult.Success<String>).result).isEqualTo("bar")
        assertThat(interceptor.request?.tag(Credential::class.java)).isNotNull()
        assertThat(interceptor.request?.tag(Credential::class.java)).isEqualTo(credential)
    }

    @Test fun testPerformRequestEnsuresTheCoroutineIsActiveBeforeMakingNetworkRequest(): Unit = runBlocking {
        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val startedCountDownLatch = CountDownLatch(1)
        val cancelledCountDownLatch = CountDownLatch(1)
        val job = async(Dispatchers.IO) {
            startedCountDownLatch.countDown()
            assertThat(cancelledCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            oktaRule.createOidcClient().performRequest(JsonObject.serializer(), request) {
                throw IllegalStateException("We got a response.")
            }
        }
        assertThat(startedCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        job.cancel()
        cancelledCountDownLatch.countDown()
        assertThat(oktaRule.mockWebServerDispatcher.numberRemainingInQueue()).isEqualTo(0)
    }
}

private class RecordingInterceptor : Interceptor {
    var request: Request? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        request = chain.request()
        return chain.proceed(chain.request())
    }
}
