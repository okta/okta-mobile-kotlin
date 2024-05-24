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
package com.okta.oauth2

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.OidcEndpoints
import com.okta.authfoundation.credential.Token
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.RequestMatchers.body
import com.okta.testhelpers.RequestMatchers.bodyPart
import com.okta.testhelpers.RequestMatchers.method
import com.okta.testhelpers.RequestMatchers.path
import com.okta.testhelpers.testBodyFromFile
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.concurrent.atomic.AtomicInteger

class DeviceAuthorizationFlowTest {
    private val mockPrefix = "test_responses"
    private val successBody = """
    {
        "device_code": "1a521d9f-0922-4e6d-8db9-8b654297435a",
        "user_code": "GDLMZQCT",
        "verification_uri": "https://example.okta.com/activate",
        "verification_uri_complete": "https://example.okta.com/activate?user_code=GDLMZQCT",
        "expires_in": 600,
        "interval": 10
    }
    """.trimIndent()

    @get:Rule val oktaRule = OktaRule()

    @Test fun testStartWithMinimalJson(): Unit = runBlocking {
        val minimalBody = """
        {
            "device_code": "1a521d9f-0922-4e6d-8db9-8b654297435a",
            "user_code": "GDLMZQCT",
            "verification_uri": "https://example.okta.com/activate",
            "expires_in": 600
        }
        """.trimIndent()
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/device/authorize"),
            bodyPart("client_id", "unit_test_client_id"),
            bodyPart("scope", "openid%20email%20profile%20offline_access"),
        ) { response ->
            response.setBody(minimalBody)
        }

        val flow = DeviceAuthorizationFlow()
        val startResult = (flow.start() as OAuth2ClientResult.Success<DeviceAuthorizationFlow.Context>).result

        assertThat(startResult.deviceCode).isEqualTo("1a521d9f-0922-4e6d-8db9-8b654297435a")
        assertThat(startResult.expiresIn).isEqualTo(600)
        assertThat(startResult.interval).isEqualTo(5)
        assertThat(startResult.verificationUri).isEqualTo("https://example.okta.com/activate")
        assertThat(startResult.verificationUriComplete).isNull()
        assertThat(startResult.userCode).isEqualTo("GDLMZQCT")
    }

    @Test fun testStartWithNoEndpoints(): Unit = runBlocking {
        oktaRule.enqueue(path("/.well-known/openid-configuration")) { response ->
            response.setResponseCode(503)
        }

        val flow = DeviceAuthorizationFlow(oktaRule.configuration)
        val startResult = flow.start() as OAuth2ClientResult.Error<DeviceAuthorizationFlow.Context>
        assertThat(startResult.exception).isInstanceOf(OAuth2ClientResult.Error.OidcEndpointsNotAvailableException::class.java)
        assertThat(startResult.exception).hasMessageThat().isEqualTo("OIDC Endpoints not available.")
    }

    @Test fun testStartWithNoDeviceAuthorizationEndpoints(): Unit = runBlocking {
        val client = oktaRule.createOAuth2Client(oktaRule.createEndpoints().copy(deviceAuthorizationEndpoint = null))

        val flow = DeviceAuthorizationFlow(client)
        val startResult = flow.start() as OAuth2ClientResult.Error<DeviceAuthorizationFlow.Context>
        assertThat(startResult.exception).isInstanceOf(OAuth2ClientResult.Error.OidcEndpointsNotAvailableException::class.java)
    }

    @Test fun testStart(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/device/authorize"),
            bodyPart("client_id", "unit_test_client_id"),
            bodyPart("scope", "openid%20email%20profile%20offline_access"),
        ) { response ->
            response.setBody(successBody)
        }

        val flow = DeviceAuthorizationFlow()
        val startResult = (flow.start() as OAuth2ClientResult.Success<DeviceAuthorizationFlow.Context>).result

        assertThat(startResult.deviceCode).isEqualTo("1a521d9f-0922-4e6d-8db9-8b654297435a")
        assertThat(startResult.expiresIn).isEqualTo(600)
        assertThat(startResult.interval).isEqualTo(10)
        assertThat(startResult.verificationUri).isEqualTo("https://example.okta.com/activate")
        assertThat(startResult.verificationUriComplete).isEqualTo("https://example.okta.com/activate?user_code=GDLMZQCT")
        assertThat(startResult.userCode).isEqualTo("GDLMZQCT")
    }

    @Test fun testStartWithExtraParameters(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/device/authorize"),
            bodyPart("client_id", "unit_test_client_id"),
            bodyPart("scope", "openid%20email%20profile%20offline_access%20custom%3Aread"),
            bodyPart("foo", "bar"),
        ) { response ->
            response.setBody(successBody)
        }

        val flow = DeviceAuthorizationFlow()
        val startResult = (
            flow.start(
                extraRequestParameters = mapOf(Pair("foo", "bar")),
                scope = "openid email profile offline_access custom:read",
            ) as OAuth2ClientResult.Success<DeviceAuthorizationFlow.Context>
            ).result

        assertThat(startResult.deviceCode).isEqualTo("1a521d9f-0922-4e6d-8db9-8b654297435a")
    }

    @Test fun testStartError(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/device/authorize"),
            body("client_id=unit_test_client_id&scope=openid%20email%20profile%20offline_access")
        ) { response ->
            response.setResponseCode(500)
        }

        val flow = DeviceAuthorizationFlow()
        val startResult = flow.start() as OAuth2ClientResult.Error<DeviceAuthorizationFlow.Context>

        assertThat(startResult.exception).isInstanceOf(OAuth2ClientResult.Error.HttpResponseException::class.java)
        assertThat(startResult.exception).hasMessageThat().isEqualTo("HTTP Error: status code - 500")
    }

    @Test fun testResumeWithNoEndpoints(): Unit = runBlocking {
        oktaRule.enqueue(path("/.well-known/openid-configuration")) { response ->
            response.setResponseCode(503)
        }

        val flow = DeviceAuthorizationFlow(oktaRule.configuration)
        val context = mock<DeviceAuthorizationFlow.Context>()
        val resumeResult = flow.resume(context) as OAuth2ClientResult.Error<Token>

        assertThat(resumeResult.exception).isInstanceOf(OAuth2ClientResult.Error.OidcEndpointsNotAvailableException::class.java)
        assertThat(resumeResult.exception).hasMessageThat().isEqualTo("OIDC Endpoints not available.")
    }

    @Test fun testResumeWithNoPolling(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
            body("client_id=unit_test_client_id&device_code=1a521d9f-0922-4e6d-8db9-8b654297435a&grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code")
        ) { response ->
            response.testBodyFromFile("$mockPrefix/token.json")
        }

        val flow = DeviceAuthorizationFlow()
        val context = DeviceAuthorizationFlow.Context(
            deviceCode = "1a521d9f-0922-4e6d-8db9-8b654297435a",
            interval = 5,
            expiresIn = 600,
            verificationUri = "https://example.okta.com/activate",
            verificationUriComplete = "https://example.okta.com/activate?user_code=GDLMZQCT",
            userCode = "GDLMZQCT",
        )
        val delayFunctionExecutedCount = AtomicInteger(0)
        flow.delayFunction = { delay ->
            assertThat(delay).isEqualTo(5000)
            delayFunctionExecutedCount.incrementAndGet()
        }
        val resumeResult = flow.resume(context) as OAuth2ClientResult.Success<Token>
        assertThat(resumeResult.result.accessToken).isEqualTo("exampleAccessToken")
        assertThat(delayFunctionExecutedCount.get()).isEqualTo(1)
    }

    @Test fun testResumeWithPolling(): Unit = runBlocking {
        repeat(4) {
            oktaRule.enqueue(
                method("POST"),
                path("/oauth2/default/v1/token"),
                body("client_id=unit_test_client_id&device_code=1a521d9f-0922-4e6d-8db9-8b654297435a&grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code")
            ) { response ->
                response.setBody("""{"error": "authorization_pending","error_description": "The device authorization is pending. Please try again later."}""")
                response.setResponseCode(400)
            }
        }

        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
            body("client_id=unit_test_client_id&device_code=1a521d9f-0922-4e6d-8db9-8b654297435a&grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code")
        ) { response ->
            response.testBodyFromFile("$mockPrefix/token.json")
        }

        val flow = DeviceAuthorizationFlow()
        val context = DeviceAuthorizationFlow.Context(
            deviceCode = "1a521d9f-0922-4e6d-8db9-8b654297435a",
            interval = 5,
            expiresIn = 600,
            verificationUri = "https://example.okta.com/activate",
            verificationUriComplete = "https://example.okta.com/activate?user_code=GDLMZQCT",
            userCode = "GDLMZQCT",
        )
        val delayFunctionExecutedCount = AtomicInteger(0)
        flow.delayFunction = { delay ->
            assertThat(delay).isEqualTo(5000)
            delayFunctionExecutedCount.incrementAndGet()
        }
        val resumeResult = flow.resume(context) as OAuth2ClientResult.Success<Token>
        assertThat(resumeResult.result.accessToken).isEqualTo("exampleAccessToken")
        assertThat(delayFunctionExecutedCount.get()).isEqualTo(5)
    }

    @Test fun testResumeWithSlowDown(): Unit = runBlocking {
        repeat(4) {
            oktaRule.enqueue(
                method("POST"),
                path("/oauth2/default/v1/token"),
                body("client_id=unit_test_client_id&device_code=1a521d9f-0922-4e6d-8db9-8b654297435a&grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code")
            ) { response ->
                response.setBody("""{"error": "slow_down","error_description": "The device authorization is pending. Please try again later."}""")
                response.setResponseCode(400)
            }
        }

        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
            body("client_id=unit_test_client_id&device_code=1a521d9f-0922-4e6d-8db9-8b654297435a&grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code")
        ) { response ->
            response.testBodyFromFile("$mockPrefix/token.json")
        }

        val flow = DeviceAuthorizationFlow()
        val context = DeviceAuthorizationFlow.Context(
            deviceCode = "1a521d9f-0922-4e6d-8db9-8b654297435a",
            interval = 5,
            expiresIn = 600,
            verificationUri = "https://example.okta.com/activate",
            verificationUriComplete = "https://example.okta.com/activate?user_code=GDLMZQCT",
            userCode = "GDLMZQCT",
        )
        val delayFunctionExecutedCount = AtomicInteger(0)
        val delayAmounts = mutableListOf<Long>()
        flow.delayFunction = { delay ->
            delayAmounts += delay
            delayFunctionExecutedCount.incrementAndGet()
        }
        val resumeResult = flow.resume(context) as OAuth2ClientResult.Success<Token>
        assertThat(resumeResult.result.accessToken).isEqualTo("exampleAccessToken")
        assertThat(delayFunctionExecutedCount.get()).isEqualTo(5)
        assertThat(delayAmounts).containsExactly(5000L, 10_000L, 15_000L, 20_000L, 25_000L)
    }

    @Test fun testResumeTimeout(): Unit = runBlocking {
        repeat(4) {
            oktaRule.enqueue(
                method("POST"),
                path("/oauth2/default/v1/token"),
                body("client_id=unit_test_client_id&device_code=1a521d9f-0922-4e6d-8db9-8b654297435a&grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code")
            ) { response ->
                response.setBody("""{"error": "authorization_pending","error_description": "The device authorization is pending. Please try again later."}""")
                response.setResponseCode(400)
            }
        }

        val flow = DeviceAuthorizationFlow()
        val context = DeviceAuthorizationFlow.Context(
            deviceCode = "1a521d9f-0922-4e6d-8db9-8b654297435a",
            interval = 5,
            expiresIn = 20,
            verificationUri = "https://example.okta.com/activate",
            verificationUriComplete = "https://example.okta.com/activate?user_code=GDLMZQCT",
            userCode = "GDLMZQCT",
        )
        val delayFunctionExecutedCount = AtomicInteger(0)
        flow.delayFunction = { delay ->
            assertThat(delay).isEqualTo(5000)
            delayFunctionExecutedCount.incrementAndGet()
        }
        val resumeResult = flow.resume(context) as OAuth2ClientResult.Error
        assertThat(resumeResult.exception).isInstanceOf(DeviceAuthorizationFlow.TimeoutException::class.java)
        assertThat(delayFunctionExecutedCount.get()).isEqualTo(4)
    }

    @Test fun testResumeNetworkError(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
            body("client_id=unit_test_client_id&device_code=1a521d9f-0922-4e6d-8db9-8b654297435a&grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code")
        ) { response ->
            response.setResponseCode(400)
        }

        val flow = DeviceAuthorizationFlow()
        val context = DeviceAuthorizationFlow.Context(
            deviceCode = "1a521d9f-0922-4e6d-8db9-8b654297435a",
            interval = 5,
            expiresIn = 20,
            verificationUri = "https://example.okta.com/activate",
            verificationUriComplete = "https://example.okta.com/activate?user_code=GDLMZQCT",
            userCode = "GDLMZQCT",
        )
        val resumeResult = flow.resume(context) as OAuth2ClientResult.Error<Token>

        assertThat(resumeResult.exception).isInstanceOf(OAuth2ClientResult.Error.HttpResponseException::class.java)
        assertThat(resumeResult.exception).hasMessageThat().isEqualTo("HTTP Error: status code - 400")
    }
}

private fun OidcEndpoints.copy(deviceAuthorizationEndpoint: HttpUrl?): OidcEndpoints {
    return OidcEndpoints(
        issuer = issuer,
        authorizationEndpoint = authorizationEndpoint,
        tokenEndpoint = tokenEndpoint,
        userInfoEndpoint = userInfoEndpoint,
        jwksUri = jwksUri,
        introspectionEndpoint = introspectionEndpoint,
        revocationEndpoint = revocationEndpoint,
        endSessionEndpoint = endSessionEndpoint,
        deviceAuthorizationEndpoint = deviceAuthorizationEndpoint,
    )
}
