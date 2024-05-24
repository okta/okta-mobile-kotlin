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
package com.okta.authfoundation.client.internal

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.client.OAuth2Client
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.client.events.RateLimitExceededEvent
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource
import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@InternalAuthFoundationApi
@OptIn(ExperimentalSerializationApi::class)
suspend fun <Raw, Dto> OAuth2Client.performRequest(
    deserializationStrategy: DeserializationStrategy<Raw>,
    request: Request,
    shouldAttemptJsonDeserialization: (Response) -> Boolean = { it.isSuccessful },
    responseMapper: (Raw) -> Dto,
): OAuth2ClientResult<Dto> {
    val jsonRequest = if (request.header("accept") == null) {
        request.newBuilder()
            .addHeader("accept", "application/json")
            .build()
    } else {
        request
    }
    return internalPerformRequest(jsonRequest, shouldAttemptJsonDeserialization) { responseBody ->
        val rawResponse = configuration.json.decodeFromBufferedSource(deserializationStrategy, responseBody)
        responseMapper(rawResponse)
    }
}

@InternalAuthFoundationApi
suspend fun OAuth2Client.performRequest(
    request: Request,
): OAuth2ClientResult<Response> {
    currentCoroutineContext().ensureActive()
    return withContext(configuration.ioDispatcher) {
        try {
            val response = configuration.executeRequest(request)
            OAuth2ClientResult.Success(response)
        } catch (e: Exception) {
            OAuth2ClientResult.Error(e)
        }
    }
}

internal suspend fun OAuth2Client.performRequestNonJson(
    request: Request
): OAuth2ClientResult<Unit> {
    return internalPerformRequest(request) { }
}

internal suspend fun <T> OAuth2Client.internalPerformRequest(
    request: Request,
    shouldAttemptJsonDeserialization: (Response) -> Boolean = { it.isSuccessful },
    responseHandler: OidcConfiguration.(BufferedSource) -> T,
): OAuth2ClientResult<T> {
    return configuration.internalPerformRequest(request, shouldAttemptJsonDeserialization) {
        configuration.run { responseHandler(it) }
    }
}

internal suspend fun <T> OidcConfiguration.internalPerformRequest(
    request: Request,
    shouldAttemptJsonDeserialization: (Response) -> Boolean,
    responseHandler: (BufferedSource) -> T,
): OAuth2ClientResult<T> {
    currentCoroutineContext().ensureActive()
    return withContext(ioDispatcher) {
        try {
            val okHttpResponse = executeRequest(request, ignoreRateLimit = shouldAttemptJsonDeserialization)
            okHttpResponse.use { responseBody ->
                // Body is always non-null when returned here. See related OkHttp documentation.
                val body = responseBody.body!!.source()
                if (shouldAttemptJsonDeserialization(okHttpResponse)) {
                    OAuth2ClientResult.Success(responseHandler(body))
                } else {
                    okHttpResponse.toOAuth2ClientResultError(this@internalPerformRequest, body)
                }
            }
        } catch (e: Exception) {
            OAuth2ClientResult.Error(e)
        }
    }
}

private suspend fun OidcConfiguration.executeRequest(
    request: Request,
    ignoreRateLimit: (Response) -> Boolean = { false },
): Response {
    currentCoroutineContext().ensureActive()
    val rateLimitErrorCode = 429
    var retryCount = 0
    var response: Response
    var requestToExecute = request
    var delaySeconds = 0L

    do {
        delay(delaySeconds.seconds)
        response = okHttpClient.newCall(requestToExecute).await()

        if (response.code != rateLimitErrorCode || ignoreRateLimit(response)) return response

        val requestId = response.header("X-Okta-Request-Id")
        val responseTimeSeconds = response.headers.getDate("Date")?.time?.milliseconds?.inWholeSeconds
        val retryTimeSeconds = response.header("X-Rate-Limit-Reset")?.toLongOrNull()
        if (responseTimeSeconds == null || retryTimeSeconds == null) {
            return response
        }
        val timeToResetSeconds = retryTimeSeconds - responseTimeSeconds

        val event = RateLimitExceededEvent(requestToExecute, response, retryCount)
        eventCoordinator.sendEvent(event)
        retryCount++

        if (retryCount <= event.maxRetries) response.close()

        requestToExecute = request.newBuilder().apply {
            requestId?.let { addHeader("X-Okta-Retry-For", requestId) }
            addHeader("X-Okta-Retry-Count", retryCount.toString())
        }.build()

        delaySeconds = max(timeToResetSeconds, event.minDelaySeconds)
    } while (retryCount <= event.maxRetries)

    return response
}

@Serializable
internal class ErrorResponse(
    @SerialName("error") val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
private fun <T> Response.toOAuth2ClientResultError(
    configuration: OidcConfiguration,
    responseBody: BufferedSource,
): OAuth2ClientResult<T> {
    val errorResponse = try {
        responseBody.let { configuration.json.decodeFromBufferedSource<ErrorResponse>(it) }
    } catch (e: Exception) {
        null
    }
    return OAuth2ClientResult.Error(
        OAuth2ClientResult.Error.HttpResponseException(
            responseCode = code,
            error = errorResponse?.error,
            errorDescription = errorResponse?.errorDescription,
        )
    )
}

private suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        val callback = ContinuationCallback(this, continuation)
        enqueue(callback)
        continuation.invokeOnCancellation(callback)
    }
}

private class ContinuationCallback(
    private val call: Call,
    private val continuation: CancellableContinuation<Response>
) : Callback, CompletionHandler {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onResponse(call: Call, response: Response) {
        continuation.resume(response, this)
    }

    override fun onFailure(call: Call, e: IOException) {
        if (!call.isCanceled()) {
            continuation.resumeWithException(e)
        }
    }

    override fun invoke(cause: Throwable?) {
        try {
            call.cancel()
        } catch (_: Throwable) {
        }
    }
}
