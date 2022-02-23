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
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.RequestMatchers.path
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Rule
import org.junit.Test
import java.io.IOException

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

        val result = oktaRule.configuration.performRequest(JsonObject.serializer(), request) {
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

        val result = oktaRule.configuration.performRequest(JsonObject.serializer(), request, { true }) {
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

        val result = oktaRule.configuration.performRequest<JsonObject, String>(JsonObject.serializer(), request, { true }) {
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

        val result = oktaRule.configuration.performRequest(JsonObject.serializer(), request, { true }) {
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

        val result = oktaRule.configuration.performRequest(JsonObject.serializer(), request) {
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

        val result = oktaRule.configuration.performRequest(JsonObject.serializer(), request) {
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

        val result = oktaRule.configuration.performRequestNonJson(request)
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

        val result = oktaRule.configuration.performRequest(JsonObject.serializer(), request) {
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

        val result = oktaRule.configuration.performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }
        val exception = (result as OidcClientResult.Error<String>).exception
        assertThat(exception).isInstanceOf(IOException::class.java)
        assertThat(exception).hasMessageThat().isEqualTo("Request failed.")
    }
}
