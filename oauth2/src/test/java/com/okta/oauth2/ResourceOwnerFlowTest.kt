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
import com.okta.oauth2.ResourceOwnerFlow.Companion.resourceOwnerFlow
import com.okta.testhelpers.OktaRule
import com.okta.testhelpers.RequestMatchers.body
import com.okta.testhelpers.RequestMatchers.method
import com.okta.testhelpers.RequestMatchers.path
import com.okta.testhelpers.testBodyFromFile
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class ResourceOwnerFlowTest {
    private val mockPrefix = "test_responses"

    @get:Rule val oktaRule = OktaRule()

    @Test fun testStart(): Unit = runBlocking {
        oktaRule.enqueue(
            method("POST"),
            path("/oauth2/default/v1/token"),
            body("username=foo&password=bar&client_id=unit_test_client_id&grant_type=password&scope=openid%20email%20profile%20offline_access")
        ) { response ->
            response.testBodyFromFile("$mockPrefix/token.json")
        }
        val resourceOwnerFlow = oktaRule.createOidcClient().resourceOwnerFlow()
        val result = resourceOwnerFlow.start("foo", "bar")
        val token = (result as ResourceOwnerFlow.Result.Token).token
        assertThat(token.tokenType).isEqualTo("Bearer")
        assertThat(token.expiresIn).isEqualTo(3600)
        assertThat(token.accessToken).isEqualTo("exampleAccessToken")
        assertThat(token.scope).isEqualTo("offline_access profile openid email")
        assertThat(token.refreshToken).isEqualTo("exampleRefreshToken")
        assertThat(token.idToken).isEqualTo("exampleIdToken")
    }

    @Test fun testStartFailure(): Unit = runBlocking {
        oktaRule.enqueue(
            path("/oauth2/default/v1/token"),
        ) { response ->
            response.setResponseCode(503)
        }
        val resourceOwnerFlow = oktaRule.createOidcClient().resourceOwnerFlow()
        val result = resourceOwnerFlow.start("foo", "bar")
        assertThat(result).isInstanceOf(ResourceOwnerFlow.Result.Error::class.java)
        val errorResult = result as ResourceOwnerFlow.Result.Error
        assertThat(errorResult.exception).isInstanceOf(IOException::class.java)
        assertThat(errorResult.exception).hasMessageThat().isEqualTo("Request failed.")
        assertThat(errorResult.message).isEqualTo("Token request failed.")
    }
}
