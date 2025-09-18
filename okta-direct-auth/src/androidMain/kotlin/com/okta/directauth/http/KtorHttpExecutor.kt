package com.okta.directauth.http

import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.ApiFormRequest
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.api.http.ApiRequestBody
import com.okta.authfoundation.api.http.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cookies.HttpCookies
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
 * is created using the `OkHttp` engine with `HttpTimeout`, `HttpCookies`, and `HttpCache`
 * plugins installed.
 *
 * The timeout values can be adjusted in the default client by modifying the values in the
 * `HttpTimeout` configuration block.
 *
 * The HttpCookies and HttpCache plugins are default in memory implementations
 * and can be replaced by configuring your own [HttpClient] instance.
 */
class KtorHttpExecutor(
    val httpClient: HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000L // 15 seconds
            connectTimeoutMillis = 10_000L // 10 seconds
        }
        install(HttpCookies)
        install(HttpCache)
    }
) : ApiExecutor {

    override suspend fun execute(request: ApiRequest): Result<ApiResponse> = runCatching {

        val response: HttpResponse = httpClient.request {
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
            override val body: ByteArray? = bodyBytes
            override val headers: Map<String, List<String>> = response.headers.toMap()
            override val contentLength: Long = response.contentLength() ?: -1L
            override val contentType: String = response.contentType()?.toString() ?: ""
        }
    }
}
