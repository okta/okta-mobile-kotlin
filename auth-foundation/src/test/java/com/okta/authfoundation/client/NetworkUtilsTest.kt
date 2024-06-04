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
import com.okta.authfoundation.client.events.RateLimitExceededEvent
import com.okta.authfoundation.client.internal.performRequest
import com.okta.authfoundation.client.internal.performRequestNonJson
import com.okta.authfoundation.credential.Credential
import com.okta.authfoundation.events.Event
import com.okta.authfoundation.events.EventHandler
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.RequestMatchers.doesNotContainHeader
import com.okta.testhelpers.RequestMatchers.doesNotContainHeaderWithValue
import com.okta.testhelpers.RequestMatchers.header
import com.okta.testhelpers.RequestMatchers.path
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkingTest {
    @get:Rule val oktaRule = OktaRule()

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test fun testPerformRequest(): Unit = runTest {
        oktaRule.enqueue(
            path("/test"),
            header("accept", "application/json")
        ) { response ->
            response.setBody("""{"foo":"bar"}""")
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }
        assertThat((result as OAuth2ClientResult.Success<String>).result).isEqualTo("bar")
    }

    @Test fun testPerformRequestErrorStatusCodesCallMapper(): Unit = runTest {
        oktaRule.enqueue(
            path("/test"),
        ) { response ->
            response.setBody("""{"foo":"bar"}""")
            response.setResponseCode(401)
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request, { true }) {
            it["foo"]!!.jsonPrimitive.content
        }
        assertThat((result as OAuth2ClientResult.Success<String>).result).isEqualTo("bar")
    }

    @Test fun testPerformRequestExceptionInMapper(): Unit = runTest {
        oktaRule.enqueue(
            path("/test"),
        ) { response ->
            response.setBody("""{"foo":"bar"}""")
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequest<JsonObject, String>(JsonObject.serializer(), request, { true }) {
            throw IllegalArgumentException("Test Exception From Mapper")
        }
        val message = (result as OAuth2ClientResult.Error<String>).exception.message
        assertThat(message).isEqualTo("Test Exception From Mapper")
    }

    @Test fun testPerformRequestNetworkFailure(): Unit = runTest {
        oktaRule.enqueue(path("/test")) { response ->
            response.socketPolicy = SocketPolicy.DISCONNECT_AT_START
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request, { true }) {
            it["foo"]!!.jsonPrimitive.content
        }
        val message = (result as OAuth2ClientResult.Error<String>).exception.message
        assertThat(message).contains("EOF")
    }

    @Test fun testPerformRequestErrorStatusReturnsError(): Unit = runTest {
        oktaRule.enqueue(
            path("/test"),
        ) { response ->
            response.setBody("""{"foo":"bar"}""")
            response.setResponseCode(401)
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }
        val exception = (result as OAuth2ClientResult.Error<String>).exception
        assertThat(exception).isInstanceOf(OAuth2ClientResult.Error.HttpResponseException::class.java)
        val httpResponseException = exception as OAuth2ClientResult.Error.HttpResponseException
        assertThat(httpResponseException.responseCode).isEqualTo(401)
        assertThat(httpResponseException.error).isNull()
        assertThat(httpResponseException.errorDescription).isNull()
    }

    @Test fun testPerformRequestErrorStatusReturnsErrorDeserialized(): Unit = runTest {
        oktaRule.enqueue(
            path("/test"),
        ) { response ->
            response.setBody("""{"error":"bar","error_description":"baz"}""")
            response.setResponseCode(401)
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }
        val exception = (result as OAuth2ClientResult.Error<String>).exception
        assertThat(exception).isInstanceOf(OAuth2ClientResult.Error.HttpResponseException::class.java)
        val httpResponseException = exception as OAuth2ClientResult.Error.HttpResponseException
        assertThat(httpResponseException.responseCode).isEqualTo(401)
        assertThat(httpResponseException.error).isEqualTo("bar")
        assertThat(httpResponseException.errorDescription).isEqualTo("baz")
    }

    @Test fun testPerformRequestNonJson(): Unit = runTest {
        oktaRule.enqueue(
            path("/test"),
        ) { response ->
            response.setBody("")
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequestNonJson(request)
        val result2 = (result as OAuth2ClientResult.Success<Unit>).result
        assertThat(result2).isEqualTo(Unit)
    }

    @Test fun testPerformRequestFailsWhileReadingResponse(): Unit = runTest {
        oktaRule.enqueue(
            path("/test"),
        ) { response ->
            response.setBody("""{"foo}""") // Intentionally invalid json.
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }
        val exception = (result as OAuth2ClientResult.Error<String>).exception
        assertThat(exception).isInstanceOf(SerializationException::class.java)
    }

    @Test fun testPerformRequestFailsWhileReadingErrorResponse(): Unit = runTest {
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

        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }
        val exception = (result as OAuth2ClientResult.Error<String>).exception
        assertThat(exception).isInstanceOf(OAuth2ClientResult.Error.HttpResponseException::class.java)
        assertThat(exception).hasMessageThat().isEqualTo("HTTP Error: status code - 401")
    }

    @Test fun testPerformRequestHasNoTagWithNoCredential(): Unit = runTest {
        val interceptor = RecordingInterceptor()
        val builder = oktaRule.okHttpClient.newBuilder()
        builder.addInterceptor(interceptor)
        val configuration = oktaRule.createConfiguration(builder.build())
        val OAuth2Client = OAuth2Client.create(configuration, oktaRule.createEndpoints())
        oktaRule.enqueue(
            path("/test"),
        ) { response ->
            response.setBody("""{"foo":"bar"}""")
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = OAuth2Client.performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }
        assertThat((result as OAuth2ClientResult.Success<String>).result).isEqualTo("bar")
        assertThat(interceptor.request?.tag(Credential::class.java)).isNull()
    }

    @Test fun testPerformRequestEnsuresTheCoroutineIsActiveBeforeMakingNetworkRequest(): Unit = runTest {
        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val startedCountDownLatch = CountDownLatch(1)
        val cancelledCountDownLatch = CountDownLatch(1)
        val job = async(Dispatchers.IO) {
            startedCountDownLatch.countDown()
            assertThat(cancelledCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
                throw IllegalStateException("We got a response.")
            }
        }
        assertThat(startedCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        job.cancel()
        cancelledCountDownLatch.countDown()
        assertThat(oktaRule.mockWebServerDispatcher.numberRemainingInQueue()).isEqualTo(0)
    }

    @Test fun testPerformRequestRawResponseEnsuresTheCoroutineIsActiveBeforeMakingNetworkRequest(): Unit = runTest {
        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val startedCountDownLatch = CountDownLatch(1)
        val cancelledCountDownLatch = CountDownLatch(1)
        val job = async(Dispatchers.IO) {
            startedCountDownLatch.countDown()
            assertThat(cancelledCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
            oktaRule.createOAuth2Client().performRequest(request)
            throw IllegalStateException("We got a response.")
        }
        assertThat(startedCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        job.cancel()
        cancelledCountDownLatch.countDown()
        assertThat(oktaRule.mockWebServerDispatcher.numberRemainingInQueue()).isEqualTo(0)
    }

    @Test fun testPerformRequestRawResponse(): Unit = runTest {
        oktaRule.enqueue(
            path("/test"),
        ) { response ->
            response.setBody("")
            response.setResponseCode(302)
            response.addHeader("location", "example:/callback")
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val response = oktaRule.createOAuth2Client().performRequest(request)
        val successResponse = response as OAuth2ClientResult.Success<Response>
        assertThat(successResponse.result.code).isEqualTo(302)
        assertThat(successResponse.result.header("location")).isEqualTo("example:/callback")
    }

    @Test fun testPerformRequestRawResponseNetworkFailure(): Unit = runTest {
        oktaRule.enqueue(path("/test")) { response ->
            response.socketPolicy = SocketPolicy.DISCONNECT_AFTER_REQUEST
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequest(request)
        val message = (result as OAuth2ClientResult.Error<Response>).exception.message
        assertThat(message).contains("Failed to connect to")
    }

    @Test fun testPerformRequestWithAlternateAcceptHeader(): Unit = runTest {
        oktaRule.enqueue(
            path("/test"),
            header("accept", "application/ion+json; okta-version=1.0.0"),
            doesNotContainHeaderWithValue("accept", "application/json"),
        ) { response ->
            response.setBody("""{"foo":"bar"}""")
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .addHeader("accept", "application/ion+json; okta-version=1.0.0")
            .build()

        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }
        assertThat((result as OAuth2ClientResult.Success<String>).result).isEqualTo("bar")
    }

    @Test fun testPerformRequestWithNon429ErrorHasNoRetry(): Unit = runTest {
        oktaRule.enqueue(path("/test")) { response ->
            response.setResponseCode(401)
            response.setBody("""{"foo":"bar"}""")
        }
        oktaRule.enqueue(path("/test")) { _ ->
            throw IllegalStateException("Retried request after getting non-429 response.")
        }
        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }

        val exception = (result as OAuth2ClientResult.Error<String>).exception
        assertThat(exception).isInstanceOf(OAuth2ClientResult.Error.HttpResponseException::class.java)
        val httpResponseException = exception as OAuth2ClientResult.Error.HttpResponseException
        assertThat(httpResponseException.responseCode).isEqualTo(401)

        assertThat(oktaRule.mockWebServerDispatcher.numberRemainingInQueue()).isEqualTo(1)
        oktaRule.mockWebServerDispatcher.clear()
    }

    @Test fun `do not retry request when verbatim response is expected`() = runTest {
        oktaRule.enqueue(path("/test")) { response ->
            response.setResponseCode(429)
            response.setBody("""{"foo":"bar"}""")
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request, shouldAttemptJsonDeserialization = { true }) {
            it["foo"]!!.jsonPrimitive.content
        }
        assertThat((result as OAuth2ClientResult.Success<String>).result).isEqualTo("bar")
    }

    @Test fun testPerformRequestWithNon429ErrorAndRateLimitHeadersDoesNotRetry() = runTest {
        oktaRule.enqueue(path("/test")) { response ->
            response.setResponseCode(401)
            setRateLimitHeaders(response)
            response.setBody("""{"foo":"bar"}""")
        }
        oktaRule.enqueue(path("/test")) { _ ->
            throw IllegalStateException("Retried request after getting non-429 response.")
        }
        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }

        val exception = (result as OAuth2ClientResult.Error<String>).exception
        assertThat(exception).isInstanceOf(OAuth2ClientResult.Error.HttpResponseException::class.java)
        val httpResponseException = exception as OAuth2ClientResult.Error.HttpResponseException
        assertThat(httpResponseException.responseCode).isEqualTo(401)

        assertThat(oktaRule.mockWebServerDispatcher.numberRemainingInQueue()).isEqualTo(1)
        oktaRule.mockWebServerDispatcher.clear()
    }

    @Test fun testPerformRequestDoesNotRetryOnNetworkFailure(): Unit = runTest {
        oktaRule.enqueue(path("/test")) { response ->
            response.socketPolicy = SocketPolicy.DISCONNECT_AT_START
        }
        oktaRule.enqueue(path("/test")) { _ ->
            throw IllegalStateException("Retried request after network failure.")
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request, { true }) {
            it["foo"]!!.jsonPrimitive.content
        }
        val message = (result as OAuth2ClientResult.Error<String>).exception.message
        assertThat(message).contains("EOF")

        assertThat(oktaRule.mockWebServerDispatcher.numberRemainingInQueue()).isEqualTo(1)
        oktaRule.mockWebServerDispatcher.clear()
    }

    @Test fun testPerformRequestRetryOn429ErrorAndStopOnSuccess(): Unit = runTest {
        oktaRule.enqueue(path("/test")) { response ->
            response.setResponseCode(429)
            setRateLimitHeaders(response)
            response.setBody("""{"foo":"bar"}""")
        }
        oktaRule.enqueue(path("/test")) { response ->
            setRateLimitHeaders(response)
            response.setBody("""{"foo":"bar"}""")
        }
        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }

        assertThat((result as OAuth2ClientResult.Success<String>).result).isEqualTo("bar")
    }

    @Test fun testPerformRequestRetryOn429FollowedByDifferentError(): Unit = runTest {
        oktaRule.enqueue(path("/test")) { response ->
            response.setResponseCode(429)
            setRateLimitHeaders(response)
            response.setBody("""{"foo":"bar"}""")
        }
        oktaRule.enqueue(path("/test")) { response ->
            response.setResponseCode(404)
            setRateLimitHeaders(response)
            response.setBody("""{"foo":"bar"}""")
        }
        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }

        val exception = (result as OAuth2ClientResult.Error<String>).exception
        assertThat(exception).isInstanceOf(OAuth2ClientResult.Error.HttpResponseException::class.java)
        val httpResponseException = exception as OAuth2ClientResult.Error.HttpResponseException
        assertThat(httpResponseException.responseCode).isEqualTo(404)
    }

    @Test fun testPerformRequestRetryContainsRetryForHeader(): Unit = runTest {
        val requestId = "requestId1234"
        oktaRule.enqueue(path("/test")) { response ->
            response.setResponseCode(429)
            setRateLimitHeaders(response, requestId = requestId)
            response.setBody("""{"foo":"bar"}""")
        }
        oktaRule.enqueue(
            path("/test"),
            header("x-okta-retry-for", requestId)
        ) { response ->
            setRateLimitHeaders(response)
            response.setBody("""{"foo":"bar"}""")
        }
        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }

        assertThat((result as OAuth2ClientResult.Success<String>).result).isEqualTo("bar")
    }

    @Test fun testPerformRequestRetryDoesNotContainRetryForHeaderIfRequestIdMissing(): Unit = runTest {
        oktaRule.enqueue(
            path("/test"),
            doesNotContainHeader("x-okta-retry-for")
        ) { response ->
            response.setResponseCode(429)
            setRateLimitHeaders(response, requestId = null)
            response.setBody("""{"foo":"bar"}""")
        }
        oktaRule.enqueue(
            path("/test"),
            doesNotContainHeader("x-okta-retry-for")
        ) { response ->
            setRateLimitHeaders(response)
            response.setBody("""{"foo":"bar"}""")
        }
        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }

        assertThat((result as OAuth2ClientResult.Success<String>).result).isEqualTo("bar")
    }

    @Test fun testPerformRequestRetryContainsRetryCountHeader(): Unit = runTest {
        val retries = 3
        oktaRule.enqueue(
            path("/test"),
            doesNotContainHeader("x-okta-retry-count")
        ) { response ->
            response.setResponseCode(429)
            setRateLimitHeaders(response)
            response.setBody("""{"foo":"bar"}""")
        }

        for (i in 1 until retries) {
            oktaRule.enqueue(
                path("/test"),
                header("x-okta-retry-count", i.toString())
            ) { response ->
                response.setResponseCode(429)
                setRateLimitHeaders(response)
                response.setBody("""{"foo":"bar"}""")
            }
        }

        oktaRule.enqueue(
            path("/test"),
            header("x-okta-retry-count", retries.toString())
        ) { response ->
            setRateLimitHeaders(response)
            response.setBody("""{"foo":"bar"}""")
        }
        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }

        assertThat((result as OAuth2ClientResult.Success<String>).result).isEqualTo("bar")
    }

    @Test fun testPerformRequestRetryAfterMinimumDelay(): Unit = runTest {
        val minDelaySeconds = 20L
        val rateLimitEventHandler = getRateLimitExceededEventHandler(minDelaySeconds = minDelaySeconds)
        oktaRule.eventHandler.registerEventHandler(rateLimitEventHandler)

        oktaRule.enqueue(path("/test")) { response ->
            response.setResponseCode(429)
            setRateLimitHeaders(
                response,
                rateLimitResetEpochTime = TIME_EPOCH_SECS - 500
            )
            response.setBody("""{"foo":"bar"}""")
        }
        oktaRule.enqueue(path("/test")) { response ->
            setRateLimitHeaders(response)
            response.setBody("""{"foo":"bar"}""")
        }
        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val startTime = currentTime
        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }
        val elapsedTime = currentTime - startTime

        assertThat((result as OAuth2ClientResult.Success<String>).result).isEqualTo("bar")
        assertThat(elapsedTime).isEqualTo(minDelaySeconds.seconds.inWholeMilliseconds)
    }

    @Test fun testPerformRequestRetryAtResetTime(): Unit = runTest {
        val rateLimitResetAfterSeconds = 500L

        oktaRule.enqueue(path("/test")) { response ->
            response.setResponseCode(429)
            setRateLimitHeaders(
                response,
                rateLimitResetEpochTime = TIME_EPOCH_SECS + rateLimitResetAfterSeconds,
            )
            response.setBody("""{"foo":"bar"}""")
        }
        oktaRule.enqueue(path("/test")) { response ->
            setRateLimitHeaders(response)
            response.setBody("""{"foo":"bar"}""")
        }
        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val startTime = currentTime
        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }
        val elapsedTime = currentTime - startTime

        assertThat((result as OAuth2ClientResult.Success<String>).result).isEqualTo("bar")
        assertThat(elapsedTime).isEqualTo(rateLimitResetAfterSeconds.seconds.inWholeMilliseconds)
    }

    @Test fun testPerformRequestRetryUpToMaxRetriesOn429ErrorResponse(): Unit = runTest {
        val maxRetries = 5
        val rateLimitEventHandler = getRateLimitExceededEventHandler(maxRetries = maxRetries)
        oktaRule.eventHandler.registerEventHandler(rateLimitEventHandler)

        for (i in 0 until maxRetries + 1) { // One try + maxRetries retries
            oktaRule.enqueue(path("/test")) { response ->
                response.setResponseCode(429)
                setRateLimitHeaders(response)
                response.setBody("""{"foo":"bar"}""")
            }
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }

        val exception = (result as OAuth2ClientResult.Error<String>).exception
        assertThat(exception).isInstanceOf(OAuth2ClientResult.Error.HttpResponseException::class.java)
        val httpResponseException = exception as OAuth2ClientResult.Error.HttpResponseException
        assertThat(httpResponseException.responseCode).isEqualTo(429)
    }

    @Test fun testPerformRequestRetryCancelsOnCoroutineCancellation(): Unit = runTest {
        val fetchedFirstRequestCountDownLatch = CountDownLatch(1)

        oktaRule.enqueue(path("/test")) { response ->
            response.setResponseCode(429)
            setRateLimitHeaders(
                response,
                rateLimitResetEpochTime = TIME_EPOCH_SECS - 500
            )
            response.setBody("""{"foo":"bar"}""")
            fetchedFirstRequestCountDownLatch.countDown()
        }
        oktaRule.enqueue(path("/test")) { _ ->
            throw IllegalStateException("Retried the request after coroutine cancellation")
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val job = launch(Dispatchers.IO) {
            oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
                throw IllegalStateException("We got a response.")
            }
        }
        assertThat(fetchedFirstRequestCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        job.cancelAndJoin()
        assertThat(oktaRule.mockWebServerDispatcher.numberRemainingInQueue()).isEqualTo(1)
        oktaRule.mockWebServerDispatcher.clear()
    }

    @Test fun testPerformRequestRetryWithInvalidDateHeaderShouldReturn429Response(): Unit = runTest {
        oktaRule.enqueue(path("/test")) { response ->
            response.setResponseCode(429)
            setRateLimitHeaders(response, responseHumanReadableDate = "abcde")
            response.setBody("""{"foo":"bar"}""")
        }
        oktaRule.enqueue(path("/test")) { _ ->
            throw IllegalStateException("Retried request after getting invalid 429 date header.")
        }
        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }

        val exception = (result as OAuth2ClientResult.Error<String>).exception
        assertThat(exception).isInstanceOf(OAuth2ClientResult.Error.HttpResponseException::class.java)
        val httpResponseException = exception as OAuth2ClientResult.Error.HttpResponseException
        assertThat(httpResponseException.responseCode).isEqualTo(429)

        assertThat(oktaRule.mockWebServerDispatcher.numberRemainingInQueue()).isEqualTo(1)
        oktaRule.mockWebServerDispatcher.clear()
    }

    @Test fun testPerformRequestRetryWithInvalidRateLimitResetHeaderShouldReturn429Response(): Unit = runTest {
        oktaRule.enqueue(path("/test")) { response ->
            response.setResponseCode(429)
            setRateLimitHeaders(response)
            response.setHeader("x-rate-limit-reset", "abcde")
            response.setBody("""{"foo":"bar"}""")
        }
        oktaRule.enqueue(path("/test")) { _ ->
            throw IllegalStateException("Retried request after getting invalid 429 x-rate-limit-reset header.")
        }
        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val result = oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }

        val exception = (result as OAuth2ClientResult.Error<String>).exception
        assertThat(exception).isInstanceOf(OAuth2ClientResult.Error.HttpResponseException::class.java)
        val httpResponseException = exception as OAuth2ClientResult.Error.HttpResponseException
        assertThat(httpResponseException.responseCode).isEqualTo(429)

        assertThat(oktaRule.mockWebServerDispatcher.numberRemainingInQueue()).isEqualTo(1)
        oktaRule.mockWebServerDispatcher.clear()
    }

    @Test fun testPerformRequestRetrySendsAnEventForEach429Response(): Unit = runTest {
        val maxRetries = 10
        val events = mutableListOf<RateLimitExceededEvent>()
        val rateLimitEventHandler = object : EventHandler {
            override fun onEvent(event: Event) {
                val errorEvent = event as RateLimitExceededEvent
                errorEvent.maxRetries = maxRetries
                events.add(event)
            }
        }
        oktaRule.eventHandler.registerEventHandler(rateLimitEventHandler)

        for (i in 0 until maxRetries + 1) { // One try + maxRetries retries
            oktaRule.enqueue(path("/test")) { response ->
                response.setResponseCode(429)
                setRateLimitHeaders(response)
            }
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()
        oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }

        assertThat(events.size).isEqualTo(11)
        events.mapIndexed { index, event ->
            assertThat(event.retryCount).isEqualTo(index)
        }
    }

    @Test fun testPerformRequestRetryChangesDelayBasedOnHandledEvent(): Unit = runTest {
        val exponentBase = 2.0
        val events = mutableListOf<RateLimitExceededEvent>()
        val rateLimitEventHandler = object : EventHandler {
            override fun onEvent(event: Event) {
                val errorEvent = event as RateLimitExceededEvent
                events.add(event)
                errorEvent.minDelaySeconds = exponentBase.pow(errorEvent.retryCount).toLong()
            }
        }
        oktaRule.eventHandler.registerEventHandler(rateLimitEventHandler)

        for (i in 0 until 4) {
            oktaRule.enqueue(path("/test")) { response ->
                response.setResponseCode(429)
                setRateLimitHeaders(
                    response,
                    rateLimitResetEpochTime = TIME_EPOCH_SECS - 500,
                )
            }
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val startTime = currentTime
        oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }
        val elapsedTime = currentTime - startTime

        assertThat(events.size).isEqualTo(4)
        assertThat(elapsedTime).isEqualTo((1 + 2 + 4).seconds.inWholeMilliseconds)
    }

    @Test fun testPerformRequestRetryChangesMaxRetriesBasedOnHandledEvent(): Unit = runTest {
        val events = mutableListOf<RateLimitExceededEvent>()
        val rateLimitEventHandler = object : EventHandler {
            override fun onEvent(event: Event) {
                val errorEvent = event as RateLimitExceededEvent
                events.add(event)
                // maxRetries should be changing on each iteration. By setting the event's maxRetries
                // to 10 on the 10th retry, there should be no further retries
                errorEvent.maxRetries = if (errorEvent.retryCount == 10) 10 else 1000
            }
        }
        oktaRule.eventHandler.registerEventHandler(rateLimitEventHandler)

        for (i in 0 until 11) { // One try + 10 retries
            oktaRule.enqueue(path("/test")) { response ->
                response.setResponseCode(429)
                setRateLimitHeaders(
                    response,
                    rateLimitResetEpochTime = TIME_EPOCH_SECS - 500,
                )
            }
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        oktaRule.createOAuth2Client().performRequest(JsonObject.serializer(), request) {
            it["foo"]!!.jsonPrimitive.content
        }

        assertThat(events.size).isEqualTo(11)
        assertThat(events.last().retryCount).isEqualTo(10)
    }

    @Test fun testPerformRequestRetryClosesUnusedResponsesAfterEmittingEvent(): Unit = runTest {
        val events = mutableListOf<RateLimitExceededEvent>()
        val rateLimitEventHandler = object : EventHandler {
            override fun onEvent(event: Event) {
                val errorEvent = event as RateLimitExceededEvent
                errorEvent.minDelaySeconds = 0L
                events.add(errorEvent)
                assertThat(event.response.peekBody(Long.MAX_VALUE).string()).isEqualTo("")
            }
        }
        oktaRule.eventHandler.registerEventHandler(rateLimitEventHandler)

        for (i in 0 until 4) { // One try + 3 retries
            oktaRule.enqueue(path("/test")) { response ->
                response.setResponseCode(429)
                setRateLimitHeaders(response, rateLimitResetEpochTime = TIME_EPOCH_SECS - 500)
            }
        }

        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val response = oktaRule.createOAuth2Client().performRequest(request)

        assertThat(events.size).isEqualTo(4)
        val lastRetryEvent = events.removeLast()
        events.forEach { event ->
            val exception = assertFailsWith<IllegalStateException> {
                event.response.peekBody(Long.MAX_VALUE).string()
            }
            assertThat(exception.message).isEqualTo("closed")
        }
        // We already have retried up to maxRetries, and response is the same as last unsuccessful retry.
        // So, don't close it
        assertThat(response.getOrThrow().peekBody(Long.MAX_VALUE).string()).isEqualTo("")
        assertThat(lastRetryEvent.response.peekBody(Long.MAX_VALUE).string()).isEqualTo("")
    }

    @Test fun `prioritize custom okhttp interceptors when making requests`() = runTest {
        var lastCalledInterceptor: String? = null

        val customInterceptor = Interceptor {
            lastCalledInterceptor = "customInterceptor"
            it.proceed(it.request())
        }

        val okHttpClient = oktaRule.okHttpClient.newBuilder()
            .addInterceptor(customInterceptor)
            .build()

        mockkObject(OidcUserAgentInterceptor)
        val oidcRequestSlot = slot<Interceptor.Chain>()
        every { OidcUserAgentInterceptor.intercept(capture(oidcRequestSlot)) } answers {
            lastCalledInterceptor = "oidcInterceptor"
            with(oidcRequestSlot.captured) {
                proceed(request())
            }
        }

        val oidcConfiguration = oktaRule.createConfiguration(okHttpClient)
        oktaRule.enqueue(
            path("/test"),
        ) { response ->
            response.setBody("response")
        }
        val request = Request.Builder()
            .url(oktaRule.baseUrl.newBuilder().addPathSegments("test").build())
            .build()

        val OAuth2Client = OAuth2Client.create(oidcConfiguration, oktaRule.createEndpoints())

        OAuth2Client.performRequest(String.serializer(), request) { /** Ignore response */ }
        assertThat(lastCalledInterceptor).isEqualTo("customInterceptor")
    }

    private fun setRateLimitHeaders(
        response: MockResponse,
        rateLimitRemaining: Int = 0,
        rateLimitLimit: Int = 300,
        rateLimitResetEpochTime: Long = TIME_EPOCH_SECS + 50,
        responseHumanReadableDate: String = TIME_HTTP_DATE,
        requestId: String? = REQUEST_ID,
    ) {
        response.setHeader("x-rate-limit-remaining", rateLimitRemaining)
        response.setHeader("x-rate-limit-limit", rateLimitLimit)
        response.setHeader("x-rate-limit-reset", rateLimitResetEpochTime)
        response.setHeader("date", responseHumanReadableDate)
        requestId?.let {
            response.setHeader("x-okta-request-id", requestId)
        }
    }

    private fun getRateLimitExceededEventHandler(
        maxRetries: Int = 3,
        minDelaySeconds: Long = 5L,
    ): EventHandler {
        return object : EventHandler {
            override fun onEvent(event: Event) {
                val errorEvent = event as RateLimitExceededEvent
                errorEvent.maxRetries = maxRetries
                errorEvent.minDelaySeconds = minDelaySeconds
            }
        }
    }

    companion object {
        private const val TIME_HTTP_DATE = "Fri, 09 Sep 2022 00:00:00 GMT"
        private const val TIME_EPOCH_SECS = 1662681600L // TIME_HTTP_DATE in epoch time
        private const val REQUEST_ID = "requestId"
    }
}

private class RecordingInterceptor : Interceptor {
    var request: Request? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        request = chain.request()
        return chain.proceed(chain.request())
    }
}
