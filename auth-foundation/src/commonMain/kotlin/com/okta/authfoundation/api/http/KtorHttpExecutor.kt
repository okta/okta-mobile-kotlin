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
package com.okta.authfoundation.api.http

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.util.appendAll
import io.ktor.util.toMap

/**
 * The default timeout value for HTTP requests in milliseconds.
 */
const val REQUEST_TIMEOUT_MILLIS = 15_000L // 15 seconds

/**
 * The default timeout value for connecting to the server in milliseconds.
 */
const val CONNECT_TIMEOUT_MILLIS = 10_000L // 10 seconds

/**
 * Returns a platform-specific [HttpClient] engine.
 */
expect fun getHttpClientEngine(): HttpClient

/**
 * An [ApiExecutor] implementation that uses Ktor for making HTTP requests.
 *
 * This executor can be configured with different timeout values. For more advanced
 * customization, such as adding custom interceptors or plugins, you can create a
 * pre-configured Ktor `HttpClient` and pass it to this class's constructor. The
 * resulting `KtorHttpExecutor` instance can then be assigned to the
 * `DirectAuthenticationFlowBuilder.apiExecutor` property.
 *
 * See the Ktor Client documentation for more
 * information on configuring Ktor clients.
 *
 * @param httpClient The Ktor [HttpClient] to use for network requests. By default, a client
 * is created using the `expect method getHttpClientEngine()` with `OkHttp` engine with `HttpTimeout`, `HttpCookies`, and `HttpCache`
 * plugins installed for Android.
 *
 * The timeout values can be adjusted in the default client by modifying the values in the
 * `HttpTimeout` configuration block.
 *
 * The HttpCookies and HttpCache plugins are default in memory implementations
 * and can be replaced by configuring your own [HttpClient] instance.
 */
class KtorHttpExecutor(
    val httpClient: HttpClient = getHttpClientEngine(),
) : ApiExecutor {
    override suspend fun execute(request: ApiRequest): Result<ApiResponse> =
        runCatching {
            val response: HttpResponse =
                httpClient.request {
                    method = HttpMethod.parse(request.method().name)

                    url {
                        takeFrom(request.url())
                        request.query()?.forEach { (key, value) -> parameters.append(key, value) }
                    }

                    headers.appendAll(request.headers())

                    when (request) {
                        is ApiFormRequest -> {
                            contentType(ContentType.parse(request.contentType()))
                            setBody(FormDataContent(Parameters.build { appendAll(request.formParameters()) }))
                        }

                        is ApiRequestBody -> {
                            contentType(ContentType.parse(request.contentType()))
                            setBody(request.body())
                        }
                    }
                }

            val bodyBytes = response.bodyAsBytes()

            object : ApiResponse {
                override val statusCode: Int = response.status.value
                override val body: ByteArray = bodyBytes
                override val headers: Map<String, List<String>> = response.headers.toMap()
                override val contentLength: Long = response.contentLength() ?: -1L
                override val contentType: String = response.contentType()?.toString() ?: ""
            }
        }
}
