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
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.client.OidcConfiguration
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.resumeWithException

@InternalAuthFoundationApi
@OptIn(ExperimentalSerializationApi::class)
suspend fun <Raw, Dto> OidcConfiguration.performRequest(
    deserializationStrategy: DeserializationStrategy<Raw>,
    request: Request,
    shouldAttemptJsonDeserialization: (Response) -> Boolean = { it.isSuccessful },
    responseMapper: (Raw) -> Dto,
): OidcClientResult<Dto> {
    return internalPerformRequest(request, shouldAttemptJsonDeserialization) { responseBody ->
        val rawResponse = json.decodeFromStream(deserializationStrategy, responseBody)
        responseMapper(rawResponse)
    }
}

@InternalAuthFoundationApi
@OptIn(ExperimentalSerializationApi::class)
suspend fun <Raw> OidcConfiguration.performRequest(
    deserializationStrategy: DeserializationStrategy<Raw>,
    request: Request,
): OidcClientResult<Raw> {
    return internalPerformRequest(request) { responseBody ->
        json.decodeFromStream(deserializationStrategy, responseBody)
    }
}

internal suspend fun OidcConfiguration.performRequestNonJson(
    request: Request
): OidcClientResult<Unit> {
    return internalPerformRequest(request) { }
}

internal suspend fun <T> OidcConfiguration.internalPerformRequest(
    request: Request,
    shouldAttemptJsonDeserialization: (Response) -> Boolean = { it.isSuccessful },
    responseHandler: (InputStream) -> T,
): OidcClientResult<T> {
    return withContext(ioDispatcher) {
        try {
            val okHttpResponse = okHttpClient.newCall(request).await()
            okHttpResponse.use { responseBody ->
                // Body is always non-null when returned here. See related OkHttp documentation.
                val body = responseBody.body!!.byteStream()
                if (shouldAttemptJsonDeserialization(okHttpResponse)) {
                    OidcClientResult.Success(responseHandler(body))
                } else {
                    okHttpResponse.toOidcClientResultError(this@internalPerformRequest, body)
                }
            }
        } catch (e: Exception) {
            OidcClientResult.Error(e)
        }
    }
}

@Serializable
internal class ErrorResponse(
    @SerialName("error") val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
private fun <T> Response.toOidcClientResultError(
    configuration: OidcConfiguration,
    responseBody: InputStream,
): OidcClientResult<T> {
    return try {
        val errorResponse = responseBody.let { configuration.json.decodeFromStream<ErrorResponse>(it) }
        OidcClientResult.Error(
            OidcClientResult.Error.HttpResponseException(
                responseCode = code,
                error = errorResponse.error,
                errorDescription = errorResponse.errorDescription,
            )
        )
    } catch (e: Exception) {
        OidcClientResult.Error(IOException("Request failed."))
    }
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
