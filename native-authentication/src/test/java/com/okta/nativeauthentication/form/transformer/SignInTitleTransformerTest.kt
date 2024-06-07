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
package com.okta.nativeauthentication.form.transformer

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.idx.kotlin.client.InteractionCodeFlow
import com.okta.idx.kotlin.dto.IdxResponse
import com.okta.nativeauthentication.RealIdxResponseTransformer
import com.okta.nativeauthentication.form.Element
import com.okta.nativeauthentication.form.Form
import com.okta.nativeauthentication.utils.IdxResponseFactory
import com.okta.testing.network.NetworkRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class SignInTitleTransformerTest {
    @get:Rule val networkRule = NetworkRule()

    private val idxResponseFactory = IdxResponseFactory(networkRule)

    private fun getFormFromJson(json: String): Form = runBlocking {
        val responseTransformer: suspend (resultProducer: suspend (InteractionCodeFlow) -> OAuth2ClientResult<IdxResponse>) -> Unit = {
            throw AssertionError("Not expected")
        }
        val formBuilder = RealIdxResponseTransformer().transform(responseTransformer, idxResponseFactory.fromJson(json)) { _, _ -> }
        formBuilder.build(listOf(SignInTitleTransformer()))
    }

    @Test fun testSignInTitleTransformerAddsTitleToIdentifyRemediation() {
        val form = getFormFromJson(
            """
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
        )
        assertThat(form.elements).hasSize(4)
        assertThat((form.elements[0] as Element.Label).text).isEqualTo("Sign In")
        assertThat((form.elements[1] as Element.TextInput).label).isEqualTo("Username")
        assertThat((form.elements[2] as Element.TextInput).label).isEqualTo("Password")
        assertThat((form.elements[3] as Element.Action).text).isEqualTo("Sign In")
    }

    @Test fun testSignInTitleTransformerDoesNotAddTitleToOtherRemediation() {
        val form = getFormFromJson(
            """
            {
              "version": "1.0.0",
              "stateHandle": "029ZAB",
              "expiresAt": "2021-05-21T16:41:22.000Z",
              "intent": "LOGIN",
              "remediation": {
                "type": "array",
                "value": [
                  {
                    "rel": ["create-form"],
                    "name": "select-enroll-profile",
                    "href": "https://jnewstrom-test.okta.com/idp/idx/enroll",
                    "method": "POST",
                    "produces": "application/ion+json; okta-version=1.0.0",
                    "value": [{
                        "name": "stateHandle",
                        "required": true,
                        "value": "02.id.bEOo8f7nf7ml0kBpaHgL5Mu69UbyyeZQwFgwIrld",
                        "visible": false,
                        "mutable": false
                    }],
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
        )
        assertThat(form.elements).hasSize(1)
        assertThat((form.elements[0] as Element.Action).text).isEqualTo("Sign Up")
    }
}
