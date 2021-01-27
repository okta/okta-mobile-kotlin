/*
 * Copyright 2014 Stormpath, Inc.
 * Modifications Copyright 2018 Okta, Inc.
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

package com.okta.idx.android.network

import com.okta.commons.http.DefaultResponse
import com.okta.commons.http.HttpException
import com.okta.commons.http.HttpHeaders
import com.okta.commons.http.HttpMethod
import com.okta.commons.http.RequestExecutor
import com.okta.commons.http.authc.RequestAuthenticator
import com.okta.commons.http.config.HttpClientConfiguration
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.internal.closeQuietly
import okio.BufferedSink
import okio.Source
import okio.source
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketException
import java.net.SocketTimeoutException
import com.okta.commons.http.MediaType as OktaMediaType
import com.okta.commons.http.Request as OktaRequest
import com.okta.commons.http.Response as OktaResponse
import okhttp3.MediaType as OkHttpMediaType
import okhttp3.Request as OkHttpRequest
import okhttp3.Response as OkHttpResponse

internal class OkHttpRequestExecutor constructor(
    httpClientConfiguration: HttpClientConfiguration,
    private val client: OkHttpClient
) : RequestExecutor {
    private val requestAuthenticator: RequestAuthenticator =
        httpClientConfiguration.requestAuthenticator

    @Throws(HttpException::class)
    override fun executeRequest(request: OktaRequest): OktaResponse {

        // Sign the request
        requestAuthenticator.authenticate(request)
        val urlBuilder = request.resourceUrl.toHttpUrlOrNull()!!.newBuilder()

        // query parms
        request.queryString.forEach { name: String?, value: String? ->
            urlBuilder.addQueryParameter(
                name!!,
                value
            )
        }
        val okRequestBuilder = OkHttpRequest.Builder()
            .url(urlBuilder.build())

        // headers
        request.headers.toSingleValueMap()
            .forEach { (name: String?, value: String?) ->
                okRequestBuilder.addHeader(
                    name!!,
                    value!!
                )
            }
        val method = request.method
        when (method) {
            HttpMethod.DELETE -> okRequestBuilder.delete()
            HttpMethod.GET -> okRequestBuilder.get()
            HttpMethod.HEAD -> okRequestBuilder.head()
            HttpMethod.POST -> okRequestBuilder.post(
                InputStreamRequestBody(
                    request.body,
                    request.headers.contentType
                )
            )
            HttpMethod.PUT ->
                okRequestBuilder.put(
                    InputStreamRequestBody(
                        request.body,
                        request.headers.contentType
                    )
                )
            else -> throw IllegalArgumentException("Unrecognized HttpMethod: $method")
        }
        return try {
            val okResponse = client.newCall(okRequestBuilder.build()).execute()
            toSdkResponse(okResponse)
        } catch (e: SocketException) {
            throw HttpException(
                "Unable to execute HTTP request - retryable exception: " + e.message,
                e,
                true
            )
        } catch (e: SocketTimeoutException) {
            throw HttpException(
                "Unable to execute HTTP request - retryable exception: " + e.message,
                e,
                true
            )
        } catch (e: IOException) {
            throw HttpException(e.message, e)
        }
    }

    @Throws(IOException::class)
    private fun toSdkResponse(okResponse: OkHttpResponse): OktaResponse {
        val httpStatus = okResponse.code
        val headers = HttpHeaders()
        headers.putAll(okResponse.headers.toMultimap())
        val mediaType = headers.contentType
        val body = okResponse.body
        var bodyInputStream: InputStream? = null
        val contentLength: Long

        //ensure that the content has been fully acquired before closing the http stream
        if (body != null) {
            contentLength = body.contentLength()
            bodyInputStream = ByteArrayInputStream(body.bytes())
        } else {
            contentLength = 0 // force 0 content length when there is no body
        }
        val response: OktaResponse =
            DefaultResponse(httpStatus, mediaType, bodyInputStream, contentLength)
        response.headers.putAll(headers)
        return response
    }

    private class InputStreamRequestBody constructor(
        private val inputStream: InputStream,
        contentType: OktaMediaType
    ) : RequestBody() {
        private val okContentType: OkHttpMediaType? = contentType.toString().toMediaTypeOrNull()

        override fun contentType(): OkHttpMediaType? {
            return okContentType
        }

        @Throws(IOException::class)
        override fun writeTo(sink: BufferedSink) {
            var source: Source? = null
            try {
                source = inputStream.source()
                sink.writeAll(source)
            } finally {
                source?.closeQuietly()
            }
        }
    }
}
