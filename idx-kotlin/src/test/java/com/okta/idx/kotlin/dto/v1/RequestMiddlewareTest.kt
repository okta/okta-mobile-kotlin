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
package com.okta.idx.kotlin.dto.v1

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.client.OidcClient
import com.okta.idx.kotlin.client.InteractionCodeFlowContext
import com.okta.idx.kotlin.dto.createField
import com.okta.idx.kotlin.dto.createRemediation
import com.okta.testing.network.NetworkRule
import com.okta.testing.network.RequestMatchers.path
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Rule
import org.junit.Test

class RequestMiddlewareTest {
    @get:Rule val networkRule = NetworkRule()

    @Test fun testAsJsonRequest() {
        val remediation = createRemediation(
            listOf(
                createField(name = "first", value = "apple"),
                createField(name = "second", value = "orange"),
            )
        )
        val request = remediation.asJsonRequest(networkRule.createOidcClient())
        assertThat(request.url).isEqualTo("https://test.okta.com/idp/idx/identify".toHttpUrl())
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.headers["accept"]).isEqualTo("application/json; okta-version=1.0.0")
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        assertThat(buffer.readUtf8()).isEqualTo("""{"first":"apple","second":"orange"}""")
        assertThat(request.body?.contentType()).isEqualTo("application/ion+json; okta-version=1.0.0; charset=utf-8".toMediaType())
    }

    @Test fun testAsFormRequest() {
        val remediation = createRemediation(
            listOf(
                createField(name = "first", value = "apple"),
                createField(name = "second", value = "orange"),
            )
        )
        val request = remediation.asFormRequest()
        assertThat(request.url).isEqualTo("https://test.okta.com/idp/idx/identify".toHttpUrl())
        assertThat(request.method).isEqualTo("POST")
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        assertThat(buffer.readUtf8()).isEqualTo("first=apple&second=orange")
        assertThat(request.body?.contentType()).isEqualTo("application/x-www-form-urlencoded".toMediaType())
    }

    @Test fun testTokenRequestFromInteractionCode(): Unit = runBlocking {
        val flowContext = InteractionCodeFlowContext(codeVerifier = "123456", interactionHandle = "234567", state = "345678", redirectUrl = "test.okta.com/login", nonce = "456789", maxAge = null)
        val request = tokenRequestFromInteractionCode(networkRule.createOidcClient(), flowContext, "654321")
        assertThat(request.url.toString()).endsWith("/oauth2/default/v1/token")
        assertThat(request.method).isEqualTo("POST")
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        assertThat(buffer.readUtf8()).isEqualTo("grant_type=interaction_code&client_id=test&interaction_code=654321&code_verifier=123456")
        assertThat(request.body?.contentType()).isEqualTo("application/x-www-form-urlencoded".toMediaType())
    }

    @Test fun testIntrospectRequest(): Unit = runBlocking {
        val flowContext = InteractionCodeFlowContext(codeVerifier = "123456", interactionHandle = "234567", state = "345678", redirectUrl = "test.okta.com/login", nonce = "456789", maxAge = null)
        val request = introspectRequest(networkRule.createOidcClient(), flowContext)
        assertThat(request.url.toString()).endsWith("/idp/idx/introspect")
        assertThat(request.method).isEqualTo("POST")
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        assertThat(buffer.readUtf8()).isEqualTo("""{"interactionHandle":"234567"}""")
        assertThat(request.body?.contentType()).isEqualTo("application/ion+json; okta-version=1.0.0; charset=utf-8".toMediaType())
    }

    @Test fun testInteractContext(): Unit = runBlocking {
        val interactContext = InteractContext.create(networkRule.createOidcClient(), "test.okta.com/login", codeVerifier = "asdfasdf", state = "randomGen", nonce = "exampleNonce")!!
        assertThat(interactContext.codeVerifier).isEqualTo("asdfasdf")
        assertThat(interactContext.state).isEqualTo("randomGen")
        assertThat(interactContext.nonce).isEqualTo("exampleNonce")
        assertThat(interactContext.maxAge).isNull()
        val request = interactContext.request
        assertThat(request.url.toString()).endsWith("/oauth2/default/v1/interact")
        assertThat(request.method).isEqualTo("POST")
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        assertThat(buffer.readUtf8()).isEqualTo("client_id=test&scope=openid%20email%20profile%20offline_access&code_challenge=JBP7NwmwWTnwTPLpL30Il_wllvmtC4qeqFXHv-uq6JI&code_challenge_method=S256&redirect_uri=test.okta.com%2Flogin&state=randomGen&nonce=exampleNonce")
        assertThat(request.body?.contentType()).isEqualTo("application/x-www-form-urlencoded".toMediaType())
    }

    @Test fun testInteractContextReturnsNullWhenNoEndpointsExist(): Unit = runBlocking {
        networkRule.enqueue(path(".well-known/openid-configuration")) { response ->
            response.socketPolicy = SocketPolicy.DISCONNECT_AT_START
        }
        val interactContext = InteractContext.create(
            OidcClient.createFromDiscoveryUrl(
                networkRule.configuration,
                networkRule.mockedUrl().newBuilder()
                    .addPathSegments(".well-known/openid-configuration").build(),
            ),
            "test.okta.com/login", codeVerifier = "asdfasdf", state = "randomGen"
        )
        assertThat(interactContext).isNull()
    }

    @Test fun testInteractContextWithExtraParameters(): Unit = runBlocking {
        val extraParameters = mapOf(Pair("recovery_token", "secret"))
        val interactContext = InteractContext.create(networkRule.createOidcClient(), "test.okta.com/login", extraParameters, codeVerifier = "asdfasdf", state = "randomGen", nonce = "exampleNonce")!!
        assertThat(interactContext.codeVerifier).isEqualTo("asdfasdf")
        assertThat(interactContext.state).isEqualTo("randomGen")
        val request = interactContext.request
        assertThat(request.url.toString()).endsWith("/oauth2/default/v1/interact")
        assertThat(request.method).isEqualTo("POST")
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        assertThat(buffer.readUtf8()).isEqualTo("client_id=test&scope=openid%20email%20profile%20offline_access&code_challenge=JBP7NwmwWTnwTPLpL30Il_wllvmtC4qeqFXHv-uq6JI&code_challenge_method=S256&redirect_uri=test.okta.com%2Flogin&state=randomGen&nonce=exampleNonce&recovery_token=secret")
        assertThat(request.body?.contentType()).isEqualTo("application/x-www-form-urlencoded".toMediaType())
    }

    @Test fun testInteractContextWithMaxAge(): Unit = runBlocking {
        val extraParameters = mapOf(Pair("max_age", "5"))
        val interactContext = InteractContext.create(networkRule.createOidcClient(), "test.okta.com/login", extraParameters, codeVerifier = "asdfasdf", state = "randomGen", nonce = "exampleNonce")!!
        assertThat(interactContext.maxAge).isEqualTo(5)
        val request = interactContext.request
        assertThat(request.url.toString()).endsWith("/oauth2/default/v1/interact")
        assertThat(request.method).isEqualTo("POST")
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        assertThat(buffer.readUtf8()).isEqualTo("client_id=test&scope=openid%20email%20profile%20offline_access&code_challenge=JBP7NwmwWTnwTPLpL30Il_wllvmtC4qeqFXHv-uq6JI&code_challenge_method=S256&redirect_uri=test.okta.com%2Flogin&state=randomGen&nonce=exampleNonce&max_age=5")
        assertThat(request.body?.contentType()).isEqualTo("application/x-www-form-urlencoded".toMediaType())
    }

    @Test fun testInteractContextWithMalformedMaxAge(): Unit = runBlocking {
        val extraParameters = mapOf(Pair("max_age", "abcd"))
        val interactContext = InteractContext.create(networkRule.createOidcClient(), "test.okta.com/login", extraParameters, codeVerifier = "asdfasdf", state = "randomGen", nonce = "exampleNonce")!!
        assertThat(interactContext.maxAge).isNull()
        val request = interactContext.request
        assertThat(request.url.toString()).endsWith("/oauth2/default/v1/interact")
        assertThat(request.method).isEqualTo("POST")
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        assertThat(buffer.readUtf8()).isEqualTo("client_id=test&scope=openid%20email%20profile%20offline_access&code_challenge=JBP7NwmwWTnwTPLpL30Il_wllvmtC4qeqFXHv-uq6JI&code_challenge_method=S256&redirect_uri=test.okta.com%2Flogin&state=randomGen&nonce=exampleNonce&max_age=abcd")
        assertThat(request.body?.contentType()).isEqualTo("application/x-www-form-urlencoded".toMediaType())
    }
}
