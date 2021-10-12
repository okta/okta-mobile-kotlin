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
import com.okta.idx.kotlin.dto.IdxAuthenticator
import com.okta.idx.kotlin.dto.IdxSendTrait
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test

class AuthenticatorMiddlewareTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test fun testSendAuthenticator() {
        val sendAuthenticatorJson = """
        {
          "profile": {
            "email": "j***a@gmail.com"
          },
          "send": {
            "rel": [
              "create-form"
            ],
            "name": "send",
            "href": "https://foo.okta.com/idp/idx/challenge/send",
            "method": "POST",
            "produces": "application/ion+json; okta-version=1.0.0",
            "value": [
              {
                "name": "stateHandle",
                "required": true,
                "value": "02ifdLyhqQ9Il4OtUU50jCdhFeCH-bzojwfpOci9EO",
                "visible": false,
                "mutable": false
              }
            ],
            "accepts": "application/json; okta-version=1.0.0"
          },
          "type": "email",
          "key": "okta_email",
          "id": "eaewrvclbBPr2PAxl5d6",
          "displayName": "Email",
          "methods": [
            {
              "type": "email"
            }
          ]
        }
        """.trimIndent()
        val v1Authenticator = json.decodeFromString<Authenticator>(sendAuthenticatorJson)
        val authenticator = v1Authenticator.toIdxAuthenticator(json, IdxAuthenticator.State.AUTHENTICATING)
        assertThat(authenticator.key).isEqualTo("okta_email")
        val sendTrait = authenticator.traits.get<IdxSendTrait>()!!

        val requestJson = sendTrait.remediation.toJsonContent().toString()
        assertThat(requestJson).isEqualTo("""{"stateHandle":"02ifdLyhqQ9Il4OtUU50jCdhFeCH-bzojwfpOci9EO"}""")
    }
}
