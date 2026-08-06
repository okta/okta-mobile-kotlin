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
package com.okta.oauth2.kmp

import com.okta.authfoundation.api.http.KtorHttpExecutor
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.oauth2.internal.parseQueryParameter
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthorizationCodeFlowParTest {
    @Test
    fun start_WhenParIsSupported_UsesRequestUriAuthorizationUrl() =
        runBlocking {
            val discoveryResponse = discoveryDocument(parEndpoint = true)
            val parResponse =
                """
                {
                    "request_uri": "urn:okta:request:abc123",
                    "expires_in": 90
                }
                """.trimIndent()

            val mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/oauth2/default/.well-known/openid-configuration" -> {
                            respond(
                                content = discoveryResponse,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }

                        "/oauth2/default/v1/par" -> {
                            respond(
                                content = parResponse,
                                status = HttpStatusCode.Created,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }

                        else -> {
                            error("Unexpected request: ${request.method} ${request.url}")
                        }
                    }
                }
            val flow = createFlow(mockEngine)

            val result =
                flow.start(
                    redirectUrl = "com.example.app:/callback",
                    scope = listOf("openid"),
                    extraRequestParameters = mapOf("prompt" to "login", "max_age" to "300")
                )

            assertTrue(result.isSuccess)
            val context = result.getOrThrow()
            assertTrue(context.usedPushedAuthorizationRequest)
            assertEquals("urn:okta:request:abc123", context.pushedAuthorizationRequestUri)
            assertEquals("urn:okta:request:abc123", parseQueryParameter(context.url, "request_uri"))
            assertEquals("test-client-id", parseQueryParameter(context.url, "client_id"))
            assertNull(parseQueryParameter(context.url, "response_type"))

            val discoveryRequest = mockEngine.requestHistory[0]
            assertEquals(HttpMethod.Get, discoveryRequest.method)
            assertEquals("https://example.okta.com/oauth2/default/.well-known/openid-configuration", discoveryRequest.url.toString())

            val parRequest = mockEngine.requestHistory[1]
            assertEquals(HttpMethod.Post, parRequest.method)
            assertEquals("https://example.okta.com/oauth2/default/v1/par", parRequest.url.toString())
            val parBody = parRequest.body.toByteArray().toString(Charsets.UTF_8)
            assertTrue(parBody.contains("client_id=test-client-id"))
            assertTrue(parBody.contains("redirect_uri=com.example.app%3A%2Fcallback"))
            assertTrue(parBody.contains("response_type=code"))
            assertTrue(parBody.contains("scope=openid"))
            assertTrue(parBody.contains("prompt=login"))
            assertTrue(parBody.contains("max_age=300"))
            assertTrue(parBody.contains("code_challenge="))
            assertTrue(parBody.contains("code_challenge_method=S256"))
            assertNotNull(context.state)
            assertNotNull(context.nonce)
            assertTrue(parBody.contains("state=${context.state}"))
            assertTrue(parBody.contains("nonce=${context.nonce}"))
        }

    @Test
    fun start_WhenParUsesClientSecret_IncludesClientSecretInParRequest() =
        runBlocking {
            val discoveryResponse = discoveryDocument(parEndpoint = true)
            val mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/oauth2/default/.well-known/openid-configuration" -> {
                            respond(
                                content = discoveryResponse,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }

                        "/oauth2/default/v1/par" -> {
                            respond(
                                content = """{"request_uri":"urn:okta:request:abc123","expires_in":90}""",
                                status = HttpStatusCode.Created,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }

                        else -> {
                            error("Unexpected request: ${request.method} ${request.url}")
                        }
                    }
                }
            val flow =
                createFlow(mockEngine) {
                    clientSecret = "test-client-secret"
                }

            val result =
                flow.start(
                    redirectUrl = "com.example.app:/callback",
                    scope = listOf("openid")
                )

            assertTrue(result.isSuccess)
            val parBody =
                mockEngine.requestHistory[1]
                    .body
                    .toByteArray()
                    .toString(Charsets.UTF_8)
            assertTrue(parBody.contains("client_secret=test-client-secret"))
            assertTrue(parBody.contains("client_id=test-client-id"))
        }

    @Test
    fun start_WhenParUsesClientAssertion_IncludesAssertionFieldsInParRequest() =
        runBlocking {
            val discoveryResponse = discoveryDocument(parEndpoint = true)
            val clientAssertionType = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
            val clientAssertion = "test-signed-jwt"
            val mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/oauth2/default/.well-known/openid-configuration" -> {
                            respond(
                                content = discoveryResponse,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }

                        "/oauth2/default/v1/par" -> {
                            respond(
                                content = """{"request_uri":"urn:okta:request:abc123","expires_in":90}""",
                                status = HttpStatusCode.Created,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }

                        else -> {
                            error("Unexpected request: ${request.method} ${request.url}")
                        }
                    }
                }
            val flow =
                createFlow(mockEngine) {
                    this.clientAssertionType = clientAssertionType
                    this.clientAssertion = clientAssertion
                }

            val result =
                flow.start(
                    redirectUrl = "com.example.app:/callback",
                    scope = listOf("openid")
                )

            assertTrue(result.isSuccess)
            val parBody =
                mockEngine.requestHistory[1]
                    .body
                    .toByteArray()
                    .toString(Charsets.UTF_8)
            assertTrue(
                parBody.contains(
                    "client_assertion_type=urn%3Aietf%3Aparams%3Aoauth%3Aclient-assertion-type%3Ajwt-bearer"
                )
            )
            assertTrue(parBody.contains("client_assertion=$clientAssertion"))
            assertTrue(parBody.contains("client_id=test-client-id"))
        }

    @Test
    fun start_WhenParIsRequiredButFails_ThrowsPushedAuthorizationRequiredException() =
        runBlocking {
            val discoveryResponse = discoveryDocument(parEndpoint = true, requirePar = true)

            val mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/oauth2/default/.well-known/openid-configuration" -> {
                            respond(
                                content = discoveryResponse,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }

                        "/oauth2/default/v1/par" -> {
                            respond(
                                content = "",
                                status = HttpStatusCode.InternalServerError,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }

                        else -> {
                            error("Unexpected request: ${request.method} ${request.url}")
                        }
                    }
                }
            val flow = createFlow(mockEngine)

            val result =
                flow.start(
                    redirectUrl = "com.example.app:/callback",
                    scope = listOf("openid")
                )

            assertTrue(result.isFailure)
            val exception = assertIs<AuthorizationCodeFlow.PushedAuthorizationRequiredException>(result.exceptionOrNull())
            assertIs<IllegalStateException>(exception.cause)
            assertEquals("PAR request failed with HTTP 500.", exception.cause?.message)
        }

    @Test
    fun start_WhenParIsRequiredAndParErrorResponseHasDescription_UsesDescriptionInCause() =
        runBlocking {
            val discoveryResponse = discoveryDocument(parEndpoint = true, requirePar = true)
            val parErrorResponse =
                """
                {
                    "error": "invalid_request",
                    "error_description": "PAR rejected by server"
                }
                """.trimIndent()

            val mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/oauth2/default/.well-known/openid-configuration" -> {
                            respond(
                                content = discoveryResponse,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }

                        "/oauth2/default/v1/par" -> {
                            respond(
                                content = parErrorResponse,
                                status = HttpStatusCode.BadRequest,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }

                        else -> {
                            error("Unexpected request: ${request.method} ${request.url}")
                        }
                    }
                }
            val flow = createFlow(mockEngine)

            val result =
                flow.start(
                    redirectUrl = "com.example.app:/callback",
                    scope = listOf("openid")
                )

            assertTrue(result.isFailure)
            val exception = assertIs<AuthorizationCodeFlow.PushedAuthorizationRequiredException>(result.exceptionOrNull())
            assertEquals("PAR rejected by server", exception.cause?.message)
        }

    @Test
    fun start_WhenParResponseIsMissingRequestUri_ThrowsPushedAuthorizationRequiredException() =
        runBlocking {
            val discoveryResponse = discoveryDocument(parEndpoint = true, requirePar = true)
            val parResponse =
                """
                {
                    "request_uri": "",
                    "expires_in": 90
                }
                """.trimIndent()

            val mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/oauth2/default/.well-known/openid-configuration" -> {
                            respond(
                                content = discoveryResponse,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }

                        "/oauth2/default/v1/par" -> {
                            respond(
                                content = parResponse,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }

                        else -> {
                            error("Unexpected request: ${request.method} ${request.url}")
                        }
                    }
                }
            val flow = createFlow(mockEngine)

            val result =
                flow.start(
                    redirectUrl = "com.example.app:/callback",
                    scope = listOf("openid")
                )

            assertTrue(result.isFailure)
            val exception = assertIs<AuthorizationCodeFlow.PushedAuthorizationRequiredException>(result.exceptionOrNull())
            assertEquals("PAR response did not include request_uri.", exception.cause?.message)
        }

    @Test
    fun start_WhenParResponseIsMalformed_ThrowsPushedAuthorizationRequiredException() =
        runBlocking {
            val discoveryResponse = discoveryDocument(parEndpoint = true, requirePar = true)
            val malformedParResponse = """{"request_uri":"""

            val mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/oauth2/default/.well-known/openid-configuration" -> {
                            respond(
                                content = discoveryResponse,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }

                        "/oauth2/default/v1/par" -> {
                            respond(
                                content = malformedParResponse,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }

                        else -> {
                            error("Unexpected request: ${request.method} ${request.url}")
                        }
                    }
                }
            val flow = createFlow(mockEngine)

            val result =
                flow.start(
                    redirectUrl = "com.example.app:/callback",
                    scope = listOf("openid")
                )

            assertTrue(result.isFailure)
            val exception = assertIs<AuthorizationCodeFlow.PushedAuthorizationRequiredException>(result.exceptionOrNull())
            assertEquals("Failed to parse PAR response.", exception.cause?.message)
        }

    private fun discoveryDocument(
        parEndpoint: Boolean,
        requirePar: Boolean = false,
    ): String =
        buildList {
            add("    \"issuer\": \"https://example.okta.com/oauth2/default\"")
            add("    \"authorization_endpoint\": \"https://example.okta.com/oauth2/default/v1/authorize\"")
            add("    \"token_endpoint\": \"https://example.okta.com/oauth2/default/v1/token\"")
            if (parEndpoint) {
                add("    \"pushed_authorization_request_endpoint\": \"https://example.okta.com/oauth2/default/v1/par\"")
            }
            if (requirePar) {
                add("    \"require_pushed_authorization_requests\": true")
            }
        }.joinToString(
            separator = ",\n",
            prefix = "{\n",
            postfix = "\n}"
        )

    private fun createFlow(
        mockEngine: MockEngine,
        buildAction: OAuth2ClientBuilder.() -> Unit = {},
    ): AuthorizationCodeFlow {
        val client =
            OAuth2ClientBuilder
                .create(
                    issuerUrl = "https://example.okta.com",
                    clientId = "test-client-id",
                    scope = listOf("openid")
                ) {
                    authorizationServerId = "default"
                    apiExecutor = KtorHttpExecutor(HttpClient(mockEngine))
                    buildAction.invoke(this)
                }.getOrThrow()
        return AuthorizationCodeFlow(client)
    }
}
