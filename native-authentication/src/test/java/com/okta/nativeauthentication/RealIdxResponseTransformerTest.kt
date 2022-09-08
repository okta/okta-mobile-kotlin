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
package com.okta.nativeauthentication

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.client.OidcClientResult
import com.okta.idx.kotlin.client.InteractionCodeFlow
import com.okta.idx.kotlin.client.InteractionCodeFlow.Companion.createInteractionCodeFlow
import com.okta.idx.kotlin.dto.IdxRemediation
import com.okta.idx.kotlin.dto.IdxResponse
import com.okta.nativeauthentication.form.Element
import com.okta.testing.network.NetworkRule
import com.okta.testing.network.RequestMatchers.path
import com.okta.testing.testBodyFromFile
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class RealIdxResponseTransformerTest {
    @get:Rule val networkRule = NetworkRule()

    private fun setup(json: String): IdxResponse = runBlocking {
        networkRule.enqueue(path("/oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("SuccessInteractResponse.json")
        }
        networkRule.enqueue(path("/idp/idx/introspect")) { response ->
            response.setBody(json)
        }
        val oidcClient = networkRule.createOidcClient()
        val interactionCodeFlowResult = oidcClient.createInteractionCodeFlow("test.okta.com/login")
        val interactionCodeFlow = (interactionCodeFlowResult as OidcClientResult.Success<InteractionCodeFlow>).result
        (interactionCodeFlow.resume() as OidcClientResult.Success<IdxResponse>).result
    }

    @Test fun basicFormIsReturned() {
        val json = """
        {
          "version": "1.0.0",
          "stateHandle": "029ZAB",
          "expiresAt": "2021-05-21T16:41:22.000Z",
          "intent": "LOGIN",
          "remediation": {
            "type": "array",
            "value": [
              {
                "rel": [
                  "create-form"
                ],
                "name": "identify",
                "href": "https://foo.oktapreview.com/idp/idx/identify",
                "method": "POST",
                "produces": "application/ion+json; okta-version=1.0.0",
                "value": [
                  {
                    "name": "identifier",
                    "label": "Username"
                  },
                  {
                    "name": "stateHandle",
                    "required": true,
                    "value": "029ZAB",
                    "visible": false,
                    "mutable": false
                  }
                ],
                "accepts": "application/json; okta-version=1.0.0"
              }
            ]
          },
          "app": {
            "type": "object",
            "value": {
              "name": "oidc_client",
              "label": "OIE Android Sample",
              "id": "0oal2s4yhspmifyt65d6"
            }
          }
        }
        """.trimIndent()
        val response = setup(json)
        val clickCounter = AtomicInteger(0)
        val clickReference = AtomicReference<IdxRemediation>()
        val formBuilder = RealIdxResponseTransformer().transform(response) { remediation ->
            clickReference.set(remediation)
            clickCounter.incrementAndGet()
        }
        val elements = formBuilder.elements
        assertThat(elements).hasSize(2)
        assertThat((elements[0] as Element.TextInput.Builder).remediation.name).isEqualTo("identify")
        assertThat((elements[0] as Element.TextInput.Builder).idxField.name).isEqualTo("identifier")
        assertThat((elements[0] as Element.TextInput.Builder).label).isEqualTo("Username")
        assertThat((elements[0] as Element.TextInput.Builder).value).isEqualTo("")
        assertThat((elements[0] as Element.TextInput.Builder).isSecret).isFalse()
        assertThat((elements[1] as Element.Action.Builder).text).isEqualTo("Sign In")
        assertThat(clickCounter.get()).isEqualTo(0)
        (elements[1] as Element.Action.Builder).onClick()
        assertThat(clickCounter.get()).isEqualTo(1)
        assertThat(clickReference.get().name).isEqualTo("identify")
    }

    @Test fun nestedFormAddsElement() {
        val json = """
        {
          "version": "1.0.0",
          "stateHandle": "029ZAB",
          "expiresAt": "2021-05-21T16:41:22.000Z",
          "intent": "LOGIN",
          "remediation": {
            "type": "array",
            "value": [
              {
                "rel": [
                  "create-form"
                ],
                "name": "identify",
                "href": "https://foo.oktapreview.com/idp/idx/identify",
                "method": "POST",
                "produces": "application/ion+json; okta-version=1.0.0",
                "value": [
                  {
                    "name": "identifier",
                    "label": "Username"
                  },
                  {
                    "name": "credentials",
                    "type": "object",
                    "form": {
                      "value": [
                        {
                          "name": "passcode",
                          "label": "Password",
                          "secret": true
                        }
                      ]
                    },
                    "required": true
                  },
                  {
                    "name": "stateHandle",
                    "required": true,
                    "value": "029ZAB",
                    "visible": false,
                    "mutable": false
                  }
                ],
                "accepts": "application/json; okta-version=1.0.0"
              }
            ]
          },
          "app": {
            "type": "object",
            "value": {
              "name": "oidc_client",
              "label": "OIE Android Sample",
              "id": "0oal2s4yhspmifyt65d6"
            }
          }
        }
        """.trimIndent()
        val response = setup(json)
        val formBuilder = RealIdxResponseTransformer().transform(response) { }
        val elements = formBuilder.elements
        assertThat(elements).hasSize(3)
        assertThat((elements[0] as Element.TextInput.Builder).label).isEqualTo("Username")
        assertThat((elements[1] as Element.TextInput.Builder).label).isEqualTo("Password")
        assertThat((elements[1] as Element.TextInput.Builder).isSecret).isTrue()
        assertThat((elements[2] as Element.Action.Builder).text).isEqualTo("Sign In")
    }
}
