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
import com.okta.idx.kotlin.dto.IdxRemediation
import com.okta.nativeauthentication.form.Element
import com.okta.nativeauthentication.utils.IdxResponseFactory
import com.okta.testing.network.NetworkRule
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class RealIdxResponseTransformerTest {
    @get:Rule val networkRule = NetworkRule()

    private val idxResponseFactory = IdxResponseFactory(networkRule)

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
        val response = idxResponseFactory.fromJson(json)
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
        val response = idxResponseFactory.fromJson(json)
        val formBuilder = RealIdxResponseTransformer().transform(response) { }
        val elements = formBuilder.elements
        assertThat(elements).hasSize(3)
        assertThat((elements[0] as Element.TextInput.Builder).label).isEqualTo("Username")
        assertThat((elements[1] as Element.TextInput.Builder).label).isEqualTo("Password")
        assertThat((elements[1] as Element.TextInput.Builder).isSecret).isTrue()
        assertThat((elements[2] as Element.Action.Builder).text).isEqualTo("Sign In")
    }
}
