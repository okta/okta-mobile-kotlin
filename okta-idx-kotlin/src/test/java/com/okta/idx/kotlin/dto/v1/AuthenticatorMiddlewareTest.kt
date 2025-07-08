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
import com.okta.idx.kotlin.dto.IdxSendCapability
import com.okta.idx.kotlin.dto.IdxWebAuthnAuthenticationCapability
import com.okta.idx.kotlin.dto.IdxWebAuthnRegistrationCapability
import kotlinx.serialization.json.Json
import org.junit.Test

class AuthenticatorMiddlewareTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun testSendAuthenticator() {
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
        val capability = authenticator.capabilities.get<IdxSendCapability>()!!

        val requestJson = capability.remediation.toJsonContent().toString()
        assertThat(requestJson).isEqualTo("""{"stateHandle":"02ifdLyhqQ9Il4OtUU50jCdhFeCH-bzojwfpOci9EO"}""")
    }

    @Test
    fun `to webAuthnRegistrationCapability with valid contextualData, expect publicKeyCredentialCreationOptions returned`() {
        // arrange
        val authenticatorJson = """
      {
          "contextualData": {
            "activationData": {
              "rp": {
                "name": "rpName"
              },
              "user": {
                "displayName": "testName",
                "name": "test@test.com",
                "id": "testId"
              },
              "pubKeyCredParams": [
                {
                  "type": "public-key",
                  "alg": -7
                },
                {
                  "type": "public-key",
                  "alg": -257
                }
              ],
              "challenge": "test-challenge",
              "attestation": "direct",
              "authenticatorSelection": {
                "userVerification": "preferred",
                "requireResidentKey": false
              },
              "u2fParams": {
                "appid": "https://test.test.com"
              },
              "excludeCredentials": [],
              "extensions": {
                "credProps": true
              }
            }
          },
          "type": "security_key",
          "key": "webauthn",
          "id": "authenticatorId",
          "displayName": "Security Key or Biometric",
          "methods": [
            {
              "type": "webauthn"
            }
          ]
      }
        """.trimIndent()

        val v1Authenticator = json.decodeFromString<Authenticator>(authenticatorJson)
        // act
        val authenticator = v1Authenticator.toIdxAuthenticator(json, IdxAuthenticator.State.ENROLLING)

        // assert
        val pubKeyCreationJson = requireNotNull(authenticator.capabilities.get<IdxWebAuthnRegistrationCapability>()).publicKeyCredentialCreationOptions().getOrThrow()

        assertThat(pubKeyCreationJson).contains("pubKeyCredParams")
        assertThat(pubKeyCreationJson).contains("challenge")
        assertThat(pubKeyCreationJson).contains("rp")
        assertThat(pubKeyCreationJson).contains("attestation")
    }

    @Test
    fun `to webAuthnRegistrationCapability with empty contextualData, expect null value`() {
        // arrange
        val authenticatorJson = """
      {
          "contextualData": {
          },
          "type": "security_key",
          "key": "webauthn",
          "id": "authenticatorId",
          "displayName": "Security Key or Biometric",
          "methods": [
            {
              "type": "webauthn"
            }
          ]
      }
        """.trimIndent()

        val v1Authenticator = json.decodeFromString<Authenticator>(authenticatorJson)
        // act
        val authenticator = v1Authenticator.toIdxAuthenticator(json, IdxAuthenticator.State.ENROLLING)

        // assert
        val pubKeyCreationJson = authenticator.capabilities.get<IdxWebAuthnRegistrationCapability>()
        assertThat(pubKeyCreationJson).isNull()
    }

    @Test
    fun `to webAuthnAuthenticationCapability with valid contextualData, expect publicKeyCredentialCreationOptions returned`() {
        val contextualData = """
            {
              "contextualData": {
                "challengeData": {
                  "challenge": "testChallenge",
                  "userVerification": "preferred",
                  "extensions": {
                    "appid": "https://test.test.com"
                  }
                }
              },
              "type": "security_key",
              "key": "webauthn",
              "id": "testId",
              "displayName": "Security Key or Biometric",
              "methods": [
                {
                  "type": "webauthn"
                }
              ]
            }
        """.trimIndent()

        val v1Authenticator = json.decodeFromString<Authenticator>(contextualData)
        // act
        val authenticator = v1Authenticator.toIdxAuthenticator(json, IdxAuthenticator.State.ENROLLING)

        // assert
        val challengeData = requireNotNull(authenticator.capabilities.get<IdxWebAuthnAuthenticationCapability>()).challengeData().getOrThrow()

        assertThat(challengeData).contains("testChallenge")
        assertThat(challengeData).contains("userVerification")
    }

    @Test
    fun `to webAuthnAuthenticationCapability with valid empty contextualData, expect null returned`() {
        val contextualData = """
            {
              "contextualData": {
              },
              "type": "security_key",
              "key": "webauthn",
              "id": "testId",
              "displayName": "Security Key or Biometric",
              "methods": [
                {
                  "type": "webauthn"
                }
              ]
            }
        """.trimIndent()
        val v1Authenticator = json.decodeFromString<Authenticator>(contextualData)

        // act
        val authenticator = v1Authenticator.toIdxAuthenticator(json, IdxAuthenticator.State.ENROLLING)

        // assert
        val capability = authenticator.capabilities.get<IdxWebAuthnAuthenticationCapability>()
        assertThat(capability).isNull()
    }

    @Test
    fun `to webAuthnRegistrationCapability not called when not a security key, expect null returned`() {
        val contextualData = """
            {
              "contextualData": {
                "challengeData": {
                  "challenge": "testChallenge",
                  "userVerification": "preferred",
                  "extensions": {
                    "appid": "https://test.test.com"
                  }
                }
              },
              "type": "password",
              "key": "password",
              "id": "testId",
              "displayName": "Password",
              "methods": [
                {
                  "type": "password"
                }
              ]
            }
        """.trimIndent()
        val v1Authenticator = json.decodeFromString<Authenticator>(contextualData)

        // act
        val authenticator = v1Authenticator.toIdxAuthenticator(json, IdxAuthenticator.State.ENROLLING)

        // assert
        val capability = authenticator.capabilities.get<IdxWebAuthnRegistrationCapability>()
        assertThat(capability).isNull()
    }

    @Test
    fun `to webAuthnAuthenticationCapability not called when not a security key, expect null returned`() {
        val contextualData = """
            {
              "contextualData": {
                "challengeData": {
                  "challenge": "testChallenge",
                  "userVerification": "preferred",
                  "extensions": {
                    "appid": "https://test.test.com"
                  }
                }
              },
              "type": "password",
              "key": "password",
              "id": "testId",
              "displayName": "Password",
              "methods": [
                {
                  "type": "password"
                }
              ]
            }
        """.trimIndent()

        val v1Authenticator = json.decodeFromString<Authenticator>(contextualData)
        // act
        val authenticator = v1Authenticator.toIdxAuthenticator(json, IdxAuthenticator.State.ENROLLING)

        // assert
        val capability = authenticator.capabilities.get<IdxWebAuthnAuthenticationCapability>()
        assertThat(capability).isNull()
    }
}
