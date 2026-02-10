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
package com.okta.api.http

import com.okta.authfoundation.api.http.ApiFormRequest
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.api.http.ApiRequestBody
import com.okta.authfoundation.api.http.ApiRequestMethod
import com.okta.authfoundation.api.http.KtorHttpExecutor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondRedirect
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Test

class KtorHttpExecutorTest {
    private var ktorHttpExecutor = KtorHttpExecutor(HttpClient { install(HttpTimeout) })

    @After
    fun tearDown() {
        ktorHttpExecutor.httpClient.close()
        runBlocking {
            ktorHttpExecutor.httpClient.coroutineContext.job
                .join()
        }
    }

    @Test
    fun testGetRequest() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel("""{"key":"value"}"""),
                        status = HttpStatusCode.OK,
                        headers = headersOf("X-Test-Header", "Test Value")
                    )
                }
            ktorHttpExecutor = KtorHttpExecutor(HttpClient(mockEngine) { install(HttpTimeout) })

            val request =
                object : ApiRequest {
                    override fun method(): ApiRequestMethod = ApiRequestMethod.GET

                    override fun headers(): Map<String, List<String>> = mapOf("X-Custom-Header" to listOf("Custom Value"))

                    override fun url(): String = "/test?q=search"
                }

            val result = ktorHttpExecutor.execute(request).getOrThrow()

            val recordedRequest = mockEngine.requestHistory.first()

            assertThat(result.statusCode, equalTo(200))
            assertThat(result.body?.toString(Charsets.UTF_8), equalTo("""{"key":"value"}"""))
            assertThat(result.headers["X-Test-Header"], hasItem("Test Value"))
            assertThat(recordedRequest.method, equalTo(HttpMethod.Get))
            assertThat(recordedRequest.url.toString(), equalTo("http://localhost/test?q=search"))
            assertThat(recordedRequest.headers["X-Custom-Header"], equalTo("Custom Value"))
        }

    @Test
    fun testPostWithFormParameters() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(""),
                        status = HttpStatusCode.Created
                    )
                }
            ktorHttpExecutor = KtorHttpExecutor(HttpClient(mockEngine) { install(HttpTimeout) })

            val request =
                object : ApiFormRequest {
                    override fun method(): ApiRequestMethod = ApiRequestMethod.POST

                    override fun headers(): Map<String, List<String>> =
                        mapOf(
                            "grant_type" to listOf("password"),
                            "username" to listOf("user@example.com")
                        )

                    override fun url(): String = "/submit"

                    override fun contentType(): String = "application/x-www-form-urlencoded"

                    override fun formParameters(): Map<String, List<String>> =
                        mapOf(
                            "grant_type" to listOf("password"),
                            "username" to listOf("user@example.com")
                        )
                }

            val result = ktorHttpExecutor.execute(request)
            assertThat(result.isSuccess, `is`(true))
            assertThat(result.getOrThrow().statusCode, equalTo(201))

            val recordedRequest = mockEngine.requestHistory.first()
            assertThat(recordedRequest.method, equalTo(HttpMethod.Post))
            assertThat(recordedRequest.url.toString(), equalTo("http://localhost/submit"))
            assertThat(recordedRequest.body.contentType.toString(), equalTo("application/x-www-form-urlencoded; charset=UTF-8"))
            assertThat(recordedRequest.body.toByteArray().toString(Charsets.UTF_8), equalTo("grant_type=password&username=user%40example.com"))
        }

    @Test
    fun testPostWithRawBody() =
        runTest {
            // arrange
            val mockEngine = MockEngine { respond(content = "", status = HttpStatusCode.OK) }
            ktorHttpExecutor = KtorHttpExecutor(HttpClient(mockEngine) { install(HttpTimeout) })

            val jsonBody = """{"id":123}"""
            val request =
                object : ApiRequestBody {
                    override fun method(): ApiRequestMethod = ApiRequestMethod.POST

                    override fun headers(): Map<String, List<String>> = emptyMap()

                    override fun url(): String = "/data"

                    override fun contentType(): String = "application/json"

                    override fun body(): ByteArray = jsonBody.toByteArray()
                }

            // act
            ktorHttpExecutor.execute(request).getOrThrow()

            // assert
            val recordedRequest = mockEngine.requestHistory.first()
            assertThat(recordedRequest.body.contentType?.toString(), equalTo("application/json"))
            assertThat(recordedRequest.body.toByteArray().toString(Charsets.UTF_8), equalTo(jsonBody))
        }

    @Test
    fun testFollowRedirectsTrue() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    if (request.url.encodedPath == "/old-location") {
                        respondRedirect("/new-location")
                    } else {
                        respond(content = "Success", status = HttpStatusCode.OK)
                    }
                }
            ktorHttpExecutor = KtorHttpExecutor(HttpClient(mockEngine) { install(HttpTimeout) })

            val request =
                object : ApiRequest {
                    override fun method(): ApiRequestMethod = ApiRequestMethod.GET

                    override fun headers(): Map<String, List<String>> = emptyMap()

                    override fun url(): String = "/old-location"
                }

            val result = ktorHttpExecutor.execute(request)
            assertThat(result.isSuccess, `is`(true))
            val response = result.getOrThrow()

            assertThat(response.statusCode, equalTo(200))
            assertThat(response.body?.toString(Charsets.UTF_8), equalTo("Success"))
            assertThat(mockEngine.requestHistory.size, equalTo(2))
            assertThat(
                mockEngine.requestHistory
                    .first()
                    .url.encodedPath,
                equalTo("/old-location")
            )
            assertThat(
                mockEngine.requestHistory
                    .last()
                    .url.encodedPath,
                equalTo("/new-location")
            )
        }

    @Test
    fun testFollowRedirectsFalse() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/new-location"))
                }
            ktorHttpExecutor =
                KtorHttpExecutor(
                    HttpClient(mockEngine) {
                        install(HttpTimeout)
                        followRedirects = false
                    }
                )

            val request =
                object : ApiRequest {
                    override fun method(): ApiRequestMethod = ApiRequestMethod.GET

                    override fun headers(): Map<String, List<String>> = emptyMap()

                    override fun url(): String = "/old-location"
                }

            val result = ktorHttpExecutor.execute(request).getOrThrow()

            assertThat(result.statusCode, equalTo(HttpStatusCode.Found.value))
            assertThat(result.headers["Location"], hasItem("/new-location"))
            assertThat(mockEngine.requestHistory.size, equalTo(1))
        }

    @Test
    fun testServerErrorReturnsSuccessResultWithErrorResponse() =
        runTest {
            // arrange
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel("Internal Error"),
                        status = HttpStatusCode.InternalServerError
                    )
                }
            ktorHttpExecutor = KtorHttpExecutor(HttpClient(mockEngine) { install(HttpTimeout) })

            val request =
                object : ApiRequest {
                    override fun method(): ApiRequestMethod = ApiRequestMethod.GET

                    override fun headers(): Map<String, List<String>> = emptyMap()

                    override fun url(): String = "/error"
                }

            // act
            val result = ktorHttpExecutor.execute(request).getOrThrow()

            // assert
            assertThat(result.statusCode, equalTo(HttpStatusCode.InternalServerError.value))
            assertThat(result.body?.toString(Charsets.UTF_8), equalTo("Internal Error"))
        }

    @Test
    fun testNetworkErrorReturnsFailureResult() =
        runTest {
            // arrange
            val mockEngine =
                MockEngine {
                    error("Connection refused")
                }
            ktorHttpExecutor = KtorHttpExecutor(HttpClient(mockEngine) { install(HttpTimeout) })

            val request =
                object : ApiRequest {
                    override fun method(): ApiRequestMethod = ApiRequestMethod.GET

                    override fun headers(): Map<String, List<String>> = emptyMap()

                    override fun url(): String = "/"
                }

            // act
            val result = ktorHttpExecutor.execute(request)

            // assert
            assertThat(result.isFailure, `is`(true))
            assertThat(result.exceptionOrNull(), instanceOf(IllegalStateException::class.java))
        }

    @Test
    fun testTimeoutReturnsFailureResult() =
        runTest {
            // arrange
            val mockEngine =
                MockEngine {
                    delay(2000)
                    respond("", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            ktorHttpExecutor =
                KtorHttpExecutor(
                    httpClient =
                        HttpClient(mockEngine) {
                            install(HttpTimeout) {
                                requestTimeoutMillis = 1000L
                            }
                        }
                )

            val request =
                object : ApiRequest {
                    override fun method(): ApiRequestMethod = ApiRequestMethod.GET

                    override fun headers(): Map<String, List<String>> = emptyMap()

                    override fun url(): String = "/timeout"
                }

            // act
            val result = ktorHttpExecutor.execute(request)

            // assert
            assertThat(result.isFailure, `is`(true))
            assertThat(result.exceptionOrNull(), instanceOf(HttpRequestTimeoutException::class.java))
        }

    @Test
    fun testResponseWithPayloadIsHandledCorrectly() =
        runTest {
            // arrange
            val responsePayload = """{"user_id":"12345","status":"ACTIVE"}"""
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(responsePayload),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            ktorHttpExecutor = KtorHttpExecutor(HttpClient(mockEngine) { install(HttpTimeout) })

            val request =
                object : ApiRequest {
                    override fun method(): ApiRequestMethod = ApiRequestMethod.GET

                    override fun headers(): Map<String, List<String>> = emptyMap()

                    override fun url(): String = "/user/profile"
                }

            // act
            val result = ktorHttpExecutor.execute(request).getOrThrow()

            // assert
            assertThat(result.statusCode, equalTo(HttpStatusCode.OK.value))
            assertThat(result.body?.toString(Charsets.UTF_8), equalTo(responsePayload))
            assertThat(result.contentType, equalTo("application/json"))
        }

    @Test
    fun testPutRequest() =
        runTest {
            // arrange
            val mockEngine =
                MockEngine {
                    respond(content = """{"status":"updated"}""", status = HttpStatusCode.OK)
                }
            ktorHttpExecutor = KtorHttpExecutor(HttpClient(mockEngine) { install(HttpTimeout) })

            val requestBody = """{"field":"new_value"}"""
            val request =
                object : ApiRequestBody {
                    override fun method(): ApiRequestMethod = ApiRequestMethod.PUT

                    override fun headers(): Map<String, List<String>> = emptyMap()

                    override fun url(): String = "/resource/1"

                    override fun contentType(): String = "application/json"

                    override fun body(): ByteArray = requestBody.toByteArray()
                }

            // act
            val result = ktorHttpExecutor.execute(request).getOrThrow()

            // assert
            val recordedRequest = mockEngine.requestHistory.first()
            assertThat(result.statusCode, equalTo(HttpStatusCode.OK.value))
            assertThat(result.body?.toString(Charsets.UTF_8), equalTo("""{"status":"updated"}"""))
            assertThat(recordedRequest.method, equalTo(HttpMethod.Put))
            assertThat(recordedRequest.url.toString(), equalTo("http://localhost/resource/1"))
            assertThat(recordedRequest.body.toByteArray().toString(Charsets.UTF_8), equalTo(requestBody))
            assertThat(recordedRequest.body.contentType.toString(), equalTo("application/json"))
        }

    @Test
    fun testDeleteRequest() =
        runTest {
            // arrange
            val mockEngine =
                MockEngine {
                    respond(content = "", status = HttpStatusCode.NoContent)
                }
            ktorHttpExecutor = KtorHttpExecutor(HttpClient(mockEngine) { install(HttpTimeout) })

            val request =
                object : ApiRequest {
                    override fun method(): ApiRequestMethod = ApiRequestMethod.DELETE

                    override fun headers(): Map<String, List<String>> = emptyMap()

                    override fun url(): String = "/resource/1"
                }

            // act
            val result = ktorHttpExecutor.execute(request).getOrThrow()

            // assert
            val recordedRequest = mockEngine.requestHistory.first()
            assertThat(result.statusCode, equalTo(HttpStatusCode.NoContent.value))
            assertThat(recordedRequest.method, equalTo(HttpMethod.Delete))
            assertThat(recordedRequest.url.toString(), equalTo("http://localhost/resource/1"))
        }

    @Test
    fun testPatchRequest() =
        runTest {
            // arrange
            val mockEngine =
                MockEngine {
                    respond(content = """{"status":"patched"}""", status = HttpStatusCode.OK)
                }
            ktorHttpExecutor = KtorHttpExecutor(HttpClient(mockEngine) { install(HttpTimeout) })

            val requestBody = """{"field":"patched_value"}"""
            val request =
                object : ApiRequestBody {
                    override fun method(): ApiRequestMethod = ApiRequestMethod.PATCH

                    override fun headers(): Map<String, List<String>> = emptyMap()

                    override fun url(): String = "/resource/1"

                    override fun contentType(): String = "application/json"

                    override fun body(): ByteArray = requestBody.toByteArray()
                }

            // act
            val result = ktorHttpExecutor.execute(request).getOrThrow()

            // assert
            val recordedRequest = mockEngine.requestHistory.first()
            assertThat(result.statusCode, equalTo(HttpStatusCode.OK.value))
            assertThat(result.body?.toString(Charsets.UTF_8), equalTo("""{"status":"patched"}"""))
            assertThat(recordedRequest.method, equalTo(HttpMethod.Patch))
            assertThat(recordedRequest.url.toString(), equalTo("http://localhost/resource/1"))
            assertThat(recordedRequest.body.toByteArray().toString(Charsets.UTF_8), equalTo(requestBody))
            assertThat(recordedRequest.body.contentType.toString(), equalTo("application/json"))
        }

    @Test
    fun testSetCookieHeaderIsReceived() =
        runTest {
            // arrange
            val mockEngine =
                MockEngine { request ->
                    // Respond with a Set-Cookie header.
                    if (request.url.encodedPath.contains("login")) {
                        respond(
                            content = "Session Created",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.SetCookie, "session_id=abcde12345; Path=/; HttpOnly")
                        )
                    } else {
                        assertThat(request.headers[HttpHeaders.Cookie], equalTo("session_id=abcde12345"))
                        respond(
                            content = "Logged In",
                            status = HttpStatusCode.OK
                        )
                    }
                }
            ktorHttpExecutor =
                KtorHttpExecutor(
                    HttpClient(mockEngine) {
                        install(HttpTimeout)
                        install(HttpCookies)
                    }
                )

            val request =
                object : ApiRequest {
                    override fun method(): ApiRequestMethod = ApiRequestMethod.GET

                    override fun headers(): Map<String, List<String>> = emptyMap()

                    override fun url(): String = "/login"
                }

            val request2 =
                object : ApiRequest {
                    override fun method(): ApiRequestMethod = ApiRequestMethod.GET

                    override fun headers(): Map<String, List<String>> = emptyMap()

                    override fun url(): String = "/profile"
                }

            // act
            val result = ktorHttpExecutor.execute(request).getOrThrow()
            val result2 = ktorHttpExecutor.execute(request2).getOrThrow()

            // assert
            assertThat(result.statusCode, equalTo(HttpStatusCode.OK.value))
            assertThat(result.body?.toString(Charsets.UTF_8), equalTo("Session Created"))
            assertThat(result.headers[HttpHeaders.SetCookie], hasItem("session_id=abcde12345; Path=/; HttpOnly"))
            assertThat(result2.statusCode, equalTo(HttpStatusCode.OK.value))
            assertThat(result2.body?.toString(Charsets.UTF_8), equalTo("Logged In"))
        }

    @Test
    fun testHttpCachePlugin() =
        runTest {
            // arrange
            val mockEngine =
                MockEngine {
                    respond(
                        content = "Cached Content",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.CacheControl, "public, max-age=3600")
                    )
                }
            ktorHttpExecutor =
                KtorHttpExecutor(
                    HttpClient(mockEngine) {
                        install(HttpCache)
                    }
                )

            val request =
                object : ApiRequest {
                    override fun method(): ApiRequestMethod = ApiRequestMethod.GET

                    override fun headers(): Map<String, List<String>> = emptyMap()

                    override fun url(): String = "/cacheable-resource"
                }

            // act
            val result1 = ktorHttpExecutor.execute(request).getOrThrow()
            val result2 = ktorHttpExecutor.execute(request).getOrThrow()

            // assert
            // The mock engine should only be called once, as the second response is served from the cache.
            assertThat(mockEngine.requestHistory.size, equalTo(1))

            // Both results should be successful and have the same content.
            assertThat(result1.statusCode, equalTo(HttpStatusCode.OK.value))
            assertThat(result1.body?.toString(Charsets.UTF_8), equalTo("Cached Content"))
            assertThat(result2.statusCode, equalTo(HttpStatusCode.OK.value))
            assertThat(result2.body?.toString(Charsets.UTF_8), equalTo("Cached Content"))
        }
}
