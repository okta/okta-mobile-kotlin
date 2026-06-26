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
package com.okta.authfoundation.client.internal

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.api.http.ApiResponse
import com.okta.authfoundation.client.OAuth2ClientResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(InternalAuthFoundationApi::class)
class OAuth2HttpOperationsTest {
    @Serializable
    private data class TestResponse(
        val value: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun mockExecutor(
        statusCode: Int,
        body: String,
        headers: Map<String, List<String>> = emptyMap(),
    ): ApiExecutor =
        object : ApiExecutor {
            override suspend fun execute(request: ApiRequest): Result<ApiResponse> =
                Result.success(
                    object : ApiResponse {
                        override val statusCode: Int = statusCode
                        override val body: ByteArray = body.toByteArray()
                        override val headers: Map<String, List<String>> = headers
                        override val contentLength: Long = body.length.toLong()
                        override val contentType: String = "application/json"
                    }
                )
        }

    // region performJsonFormPost

    @Test
    fun formPost_success_ReturnsDeserializedResult() =
        runTest {
            val executor = mockExecutor(200, """{"value":"hello"}""")

            val result =
                performJsonFormPost(
                    apiExecutor = executor,
                    json = json,
                    url = "https://example.com/token",
                    formParams = mapOf("grant_type" to "password"),
                    deserializer = TestResponse.serializer()
                )

            assertIs<OAuth2ClientResult.Success<TestResponse>>(result)
            assertEquals("hello", result.result.value)
        }

    @Test
    fun formPost_nonSuccessWithOAuth2Error_ThrowsHttpResponseException() =
        runTest {
            val executor = mockExecutor(400, """{"error":"authorization_pending","error_description":"User has not yet approved"}""")

            val result =
                performJsonFormPost(
                    apiExecutor = executor,
                    json = json,
                    url = "https://example.com/token",
                    formParams = mapOf("grant_type" to "device_code"),
                    deserializer = TestResponse.serializer()
                )

            assertIs<OAuth2ClientResult.Error<TestResponse>>(result)
            val exception = assertIs<OAuth2ClientResult.Error.HttpResponseException>(result.exception)
            assertEquals(400, exception.responseCode)
            assertEquals("authorization_pending", exception.error)
            assertEquals("User has not yet approved", exception.errorDescription)
        }

    @Test
    fun formPost_nonSuccessWithUnparseableBody_ThrowsHttpResponseExceptionWithNullFields() =
        runTest {
            val executor = mockExecutor(500, "Internal Server Error")

            val result =
                performJsonFormPost(
                    apiExecutor = executor,
                    json = json,
                    url = "https://example.com/token",
                    formParams = emptyMap(),
                    deserializer = TestResponse.serializer()
                )

            assertIs<OAuth2ClientResult.Error<TestResponse>>(result)
            val exception = assertIs<OAuth2ClientResult.Error.HttpResponseException>(result.exception)
            assertEquals(500, exception.responseCode)
            assertNull(exception.error)
            assertNull(exception.errorDescription)
        }

    @Test
    fun formPost_http429_ThrowsAndInvokesRateLimitCallback() =
        runTest {
            val executor = mockExecutor(429, "", headers = mapOf("Retry-After" to listOf("60")))
            var callbackInvoked = false

            val result =
                performJsonFormPost(
                    apiExecutor = executor,
                    json = json,
                    url = "https://example.com/token",
                    formParams = emptyMap(),
                    deserializer = TestResponse.serializer(),
                    onRateLimitExceeded = { event ->
                        callbackInvoked = true
                        assertEquals(60L, event.retryAfterSeconds)
                    }
                )

            assertIs<OAuth2ClientResult.Error<TestResponse>>(result)
            assertTrue(callbackInvoked)
        }

    // endregion

    // region performJsonGetRequest

    @Test
    fun getRequest_success_ReturnsDeserializedResult() =
        runTest {
            val executor = mockExecutor(200, """{"value":"world"}""")

            val result =
                performJsonGetRequest(
                    apiExecutor = executor,
                    json = json,
                    url = "https://example.com/userinfo",
                    deserializer = TestResponse.serializer()
                )

            assertIs<OAuth2ClientResult.Success<TestResponse>>(result)
            assertEquals("world", result.result.value)
        }

    @Test
    fun getRequest_nonSuccessWithOAuth2Error_ThrowsHttpResponseException() =
        runTest {
            val executor = mockExecutor(401, """{"error":"invalid_token","error_description":"Token expired"}""")

            val result =
                performJsonGetRequest(
                    apiExecutor = executor,
                    json = json,
                    url = "https://example.com/userinfo",
                    deserializer = TestResponse.serializer()
                )

            assertIs<OAuth2ClientResult.Error<TestResponse>>(result)
            val exception = assertIs<OAuth2ClientResult.Error.HttpResponseException>(result.exception)
            assertEquals(401, exception.responseCode)
            assertEquals("invalid_token", exception.error)
            assertEquals("Token expired", exception.errorDescription)
        }

    // endregion

    // region performFormPost

    @Test
    fun formPostNoJson_success_ReturnsUnit() =
        runTest {
            val executor = mockExecutor(200, "")

            val result =
                performFormPost(
                    apiExecutor = executor,
                    url = "https://example.com/revoke",
                    formParams = mapOf("token" to "abc123")
                )

            assertIs<OAuth2ClientResult.Success<Unit>>(result)
        }

    @Test
    fun formPostNoJson_nonSuccess_ThrowsIllegalStateException() =
        runTest {
            val executor = mockExecutor(400, """{"error":"invalid_request"}""")

            val result =
                performFormPost(
                    apiExecutor = executor,
                    url = "https://example.com/revoke",
                    formParams = mapOf("token" to "abc123")
                )

            assertIs<OAuth2ClientResult.Error<Unit>>(result)
            assertIs<IllegalStateException>(result.exception)
            assertTrue(result.exception.message!!.contains("400"))
        }

    // endregion
}
