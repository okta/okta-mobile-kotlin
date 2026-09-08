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
package com.okta.idx.kotlin.kmp

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.api.http.ApiResponse
import com.okta.authfoundation.client.DeviceTokenProvider
import com.okta.authfoundation.client.OAuth2ClientBuilder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Confirms [InteractionCodeFlowImpl] works on the `android` target and that the real Android
 * [AndroidDeviceTokenCookieHook] (backed by [com.okta.authfoundation.client.DeviceTokenProvider]) actually
 * attaches a persisted device token as a `Cookie: DT=...` header — unlike the `jvm` no-op default verified
 * in `InteractionCodeFlowJvmTest`. The shared happy-path/redirect-handling behavior itself is exercised
 * once for all targets in `commonTest`'s `InteractionCodeFlowTest`, which also runs under this module's
 * `androidHostTest` task.
 */
@OptIn(InternalAuthFoundationApi::class)
@RunWith(AndroidJUnit4::class)
class InteractionCodeFlowAndroidTest {
    private val discovery =
        """
        {
            "issuer": "https://example.okta.com/oauth2/default",
            "authorization_endpoint": "https://example.okta.com/oauth2/default/v1/authorize",
            "token_endpoint": "https://example.okta.com/oauth2/default/v1/token"
        }
        """.trimIndent()

    private val interactResponse = """{"interaction_handle":"029ZAB"}"""

    private val identifyResponse =
        """
        {
            "expiresAt": "2021-05-21T16:41:22.000Z",
            "intent": "LOGIN",
            "remediation": { "value": [] }
        }
        """.trimIndent()

    private val persistedDeviceToken = "test-device-token-1234"

    @Before
    fun setup() {
        unmockkAll()
        mockkObject(DeviceTokenProvider.Companion)
        val fakeDeviceTokenProvider = mockk<DeviceTokenProvider>()
        coEvery { fakeDeviceTokenProvider.getDeviceToken() } returns persistedDeviceToken
        every { DeviceTokenProvider.instance } returns fakeDeviceTokenProvider
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun resume_WithRealAndroidDeviceTokenCookieHook_SendsPersistedDeviceTokenCookieHeader() =
        runTest {
            val allResponses = listOf(200 to discovery, 200 to interactResponse, 200 to identifyResponse)
            var callIndex = 0
            val observedHeaders = mutableListOf<Map<String, List<String>>>()
            val apiExecutor =
                object : ApiExecutor {
                    override suspend fun execute(request: ApiRequest): Result<ApiResponse> {
                        observedHeaders.add(request.headers())
                        val (statusCode, body) = allResponses[callIndex++]
                        return Result.success(
                            object : ApiResponse {
                                override val statusCode = statusCode
                                override val body = body.toByteArray()
                                override val headers: Map<String, List<String>> = emptyMap()
                                override val contentLength = body.length.toLong()
                                override val contentType = "application/json"
                            }
                        )
                    }
                }
            val client =
                OAuth2ClientBuilder
                    .create(
                        issuerUrl = "https://example.okta.com/oauth2/default",
                        clientId = "test-client-id",
                        scope = listOf("openid", "profile")
                    ) {
                        this.apiExecutor = apiExecutor
                    }.getOrThrow()
            val flow = InteractionCodeFlow(client)

            flow.start(redirectUri = "com.example.app:/callback").getOrThrow()
            val resumeResult = flow.resume()

            assertThat(resumeResult.isSuccess).isTrue()
            val introspectRequestHeaders = observedHeaders.last()
            val cookieHeader = introspectRequestHeaders["Cookie"]?.firstOrNull()
            assertThat(cookieHeader).isEqualTo("DT=$persistedDeviceToken")
        }
}
