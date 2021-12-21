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
import com.okta.idx.kotlin.client.IdxClientConfiguration
import com.okta.idx.kotlin.client.IdxClientContext
import com.okta.idx.kotlin.dto.createField
import com.okta.idx.kotlin.dto.createRemediation
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import org.junit.Test

class RequestMiddlewareTest {
    private val configuration = IdxClientConfiguration(
        issuer = "https://test.okta.com/oauth2/default".toHttpUrl(),
        clientId = "test",
        scopes = setOf("openid", "email", "profile", "offline_access"),
        redirectUri = "test.okta.com/login",
    )

    @Test fun testAsJsonRequest() {
        val remediation = createRemediation(listOf(
            createField(name = "first", value = "apple"),
            createField(name = "second", value = "orange"),
        ))
        val request = remediation.asJsonRequest(configuration)
        assertThat(request.url).isEqualTo("https://test.okta.com/idp/idx/identify".toHttpUrl())
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.headers["accept"]).isEqualTo("application/json; okta-version=1.0.0")
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        assertThat(buffer.readUtf8()).isEqualTo("""{"first":"apple","second":"orange"}""")
        assertThat(request.body?.contentType()).isEqualTo("application/ion+json; okta-version=1.0.0; charset=utf-8".toMediaType())
    }

    @Test fun testAsFormRequest() {
        val remediation = createRemediation(listOf(
            createField(name = "first", value = "apple"),
            createField(name = "second", value = "orange"),
        ))
        val request = remediation.asFormRequest()
        assertThat(request.url).isEqualTo("https://test.okta.com/idp/idx/identify".toHttpUrl())
        assertThat(request.method).isEqualTo("POST")
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        assertThat(buffer.readUtf8()).isEqualTo("first=apple&second=orange")
        assertThat(request.body?.contentType()).isEqualTo("application/x-www-form-urlencoded".toMediaType())
    }

    @Test fun testTokenRequestFromInteractionCode() {
        val clientContext = IdxClientContext(codeVerifier = "123456", interactionHandle = "234567", state = "345678")
        val request = tokenRequestFromInteractionCode(configuration, clientContext, "654321")
        assertThat(request.url).isEqualTo("https://test.okta.com/oauth2/default/v1/token".toHttpUrl())
        assertThat(request.method).isEqualTo("POST")
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        assertThat(buffer.readUtf8()).isEqualTo("grant_type=interaction_code&client_id=test&interaction_code=654321&code_verifier=123456")
        assertThat(request.body?.contentType()).isEqualTo("application/x-www-form-urlencoded".toMediaType())
    }

    @Test fun testIntrospectRequest() {
        val clientContext = IdxClientContext(codeVerifier = "123456", interactionHandle = "234567", state = "345678")
        val request = introspectRequest(configuration, clientContext)
        assertThat(request.url).isEqualTo("https://test.okta.com/idp/idx/introspect".toHttpUrl())
        assertThat(request.method).isEqualTo("POST")
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        assertThat(buffer.readUtf8()).isEqualTo("""{"interactionHandle":"234567"}""")
        assertThat(request.body?.contentType()).isEqualTo("application/ion+json; okta-version=1.0.0; charset=utf-8".toMediaType())
    }

    @Test fun testInteractContext() {
        val interactContext = InteractContext.create(configuration, codeVerifier = "asdfasdf", state = "randomGen")
        assertThat(interactContext.codeVerifier).isEqualTo("asdfasdf")
        assertThat(interactContext.state).isEqualTo("randomGen")
        val request = interactContext.request
        assertThat(request.url).isEqualTo("https://test.okta.com/oauth2/default/v1/interact".toHttpUrl())
        assertThat(request.method).isEqualTo("POST")
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        assertThat(buffer.readUtf8()).isEqualTo("client_id=test&scope=openid%20email%20profile%20offline_access&code_challenge=JBP7NwmwWTnwTPLpL30Il_wllvmtC4qeqFXHv-uq6JI&code_challenge_method=S256&redirect_uri=test.okta.com%2Flogin&state=randomGen")
        assertThat(request.body?.contentType()).isEqualTo("application/x-www-form-urlencoded".toMediaType())
    }

    @Test fun testInteractContextWithExtraParameters() {
        val extraParameters = mapOf(Pair("recovery_token", "secret"))
        val interactContext = InteractContext.create(configuration, extraParameters, codeVerifier = "asdfasdf", state = "randomGen")
        assertThat(interactContext.codeVerifier).isEqualTo("asdfasdf")
        assertThat(interactContext.state).isEqualTo("randomGen")
        val request = interactContext.request
        assertThat(request.url).isEqualTo("https://test.okta.com/oauth2/default/v1/interact".toHttpUrl())
        assertThat(request.method).isEqualTo("POST")
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        assertThat(buffer.readUtf8()).isEqualTo("client_id=test&scope=openid%20email%20profile%20offline_access&code_challenge=JBP7NwmwWTnwTPLpL30Il_wllvmtC4qeqFXHv-uq6JI&code_challenge_method=S256&redirect_uri=test.okta.com%2Flogin&state=randomGen&recovery_token=secret")
        assertThat(request.body?.contentType()).isEqualTo("application/x-www-form-urlencoded".toMediaType())
    }
}
