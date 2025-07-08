/*
 * Copyright 2025-Present Okta, Inc.
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
package com.okta.idx.kotlin.dto

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.json.JSONException
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdxWebAuthnCapabilityTest {

    private val activationData = """
        {
          "rp": {
            "name": "testName"
          },
          "user": {
            "displayName": "test test",
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
          "challenge": "testChallenge",
          "attestation": "direct",
          "authenticatorSelection": {
            "userVerification": "preferred",
            "requireResidentKey": false
          },
          "u2fParams": {
            "appid": "https://test.test.test"
          },
          "excludeCredentials": [],
          "extensions": {
            "credProps": true
          }
        }
    """.trimIndent()

    private val challengeData = """
        {
            "challengeData": {
                "challenge": "testChallenge",
                "userVerification": "preferred",
                "extensions": {
                    "appid": "https://test.test.com"
                }
            }
        }
    """.trimIndent()

    @Test
    fun `publicKeyCredentialCreationOptions returns original data when rpId is null`() {
        // arrange
        val capability = IdxWebAuthnRegistrationCapability(activationData)

        // act
        val result = capability.publicKeyCredentialCreationOptions().getOrThrow()

        // assert
        assertThat(result).isEqualTo(JSONObject(activationData).toString())
    }

    @Test
    fun `publicKeyCredentialCreationOptions returns original data when rpId is blank`() {
        // arrange
        val capability = IdxWebAuthnRegistrationCapability(activationData)

        // act
        val result = capability.publicKeyCredentialCreationOptions().getOrThrow()

        // assert
        assertThat(result).isEqualTo(JSONObject(activationData).toString())
    }

    @Test
    fun `publicKeyCredentialCreationOptions overrides rpId when provided`() {
        // arrange
        val capability = IdxWebAuthnRegistrationCapability(activationData)
        val customRpId = "customRpId"

        // act
        val result = capability.publicKeyCredentialCreationOptions(customRpId).getOrThrow()

        // assert
        assertThat(JSONObject(result).getJSONObject("rp").getString("id")).isEqualTo(customRpId)
    }

    @Test
    fun `publicKeyCredentialCreationOptions returns failure on invalid JSON`() {
        // arrange
        val invalidData = "{invalidjson"
        val capability = IdxWebAuthnRegistrationCapability(invalidData)

        // act
        val result = capability.publicKeyCredentialCreationOptions("customRpId").exceptionOrNull()

        // assert
        assertThat(result is JSONException).isTrue()
    }

    @Test
    fun `publicKeyCredentialCreationOptions with no rp object, returns invalid JSON`() {
        // arrange
        val activationData = "{}"
        val capability = IdxWebAuthnRegistrationCapability(activationData)

        // act
        val result = capability.publicKeyCredentialCreationOptions("customRpId").exceptionOrNull()

        // assert
        assertThat(result is JSONException).isTrue()
    }

    @Test
    fun `publicKeyCredentialCreationOptions with rpId overrides existing id`() {
        val activationData = """
        {
          "rp": {
            "name": "testName",
            "id": "original.id"
          }
        }
        """.trimIndent()
        val capability = IdxWebAuthnRegistrationCapability(activationData)
        val result = capability.publicKeyCredentialCreationOptions(relyingPartyIdentifier = "new.provided.id").getOrThrow()

        val json = JSONObject(result)
        assertThat(json.getJSONObject("rp").getString("id")).isEqualTo("new.provided.id")
    }

    @Test
    fun `publicKeyCredentialCreationOptions without rpId derives id from u2fParams`() {
        val activationData = """
        {
          "rp": {
            "name": "testName",
            "u2fParams": {
              "appid": "https://test.test.com"
            }
          }
        }
        """.trimIndent()
        val capability = IdxWebAuthnRegistrationCapability(activationData)
        val result = capability.publicKeyCredentialCreationOptions().getOrThrow()

        val json = JSONObject(result)
        assertThat(json.getJSONObject("rp").getString("id")).isEqualTo("test.test.com")
    }

    @Test
    fun `publicKeyCredentialCreationOptions without rpId preserves existing id`() {
        val activationData = """
        {
          "rp": {
            "name": "testName",
            "id": "existing.id",
            "u2fParams": {
              "appid": "https://test.test.com"
            }
          }
        }
        """.trimIndent()
        val capability = IdxWebAuthnRegistrationCapability(activationData)
        val result = capability.publicKeyCredentialCreationOptions().getOrThrow()

        val json = JSONObject(result)
        // Verifies that the original ID is preserved and not overwritten by the u2fParams logic.
        assertThat(json.getJSONObject("rp").getString("id")).isEqualTo("existing.id")
    }

    @Test
    fun `publicKeyCredentialCreationOptions without rpId and no source for id`() {
        val activationData = """
        {
          "rp": {
            "name": "testName"
          }
        }
        """.trimIndent()
        val capability = IdxWebAuthnRegistrationCapability(activationData)
        val result = capability.publicKeyCredentialCreationOptions().getOrThrow()

        val json = JSONObject(result)
        // Verifies that the 'id' key is not added if it can't be derived.
        assertThat(json.getJSONObject("rp").has("id")).isFalse()
    }

    @Test
    fun `publicKeyCredentialCreationOptions with invalid appid does not add id`() {
        val activationData = """
        {
          "rp": {
            "name": "testName",
            "u2fParams": {
              "appid": "this is not a valid uri"
            }
          }
        }
        """.trimIndent()
        val capability = IdxWebAuthnRegistrationCapability(activationData)
        val result = capability.publicKeyCredentialCreationOptions().getOrThrow()

        val json = JSONObject(result)
        // Verifies that a malformed appid doesn't cause a crash or add an invalid id.
        assertThat(json.getJSONObject("rp").has("id")).isFalse()
    }

    @Test
    fun `challengeData overrides rpId when provided`() {
        // arrange
        val capability = IdxWebAuthnAuthenticationCapability(challengeData)
        val customRpId = "customRpId"

        // act
        val result = capability.challengeData(customRpId).getOrThrow()

        // assert
        assertThat(JSONObject(result).getString("rpId")).isEqualTo(customRpId)
    }

    @Test
    fun `challengeData returns failure on invalid JSON`() {
        // arrange
        val invalidData = "{invalidjson"
        val capability = IdxWebAuthnAuthenticationCapability(invalidData)

        // act
        val result = capability.challengeData("customRpId").exceptionOrNull()

        // assert
        assertThat(result is JSONException).isTrue()
    }

    @Test
    fun `challengeData with rpId overrides existing rpId`() {
        // arrange
        val challengeJson = """
            {
              "challenge": "randomChallengeString",
              "rpId": "original.id",
              "extensions": {
                "appid": "https://test.test.com"
              }
            }
        """.trimIndent()
        val capability = IdxWebAuthnAuthenticationCapability(challengeJson)

        // act
        val result = capability.challengeData(relyingPartyIdentifier = "new.provided.id").getOrThrow()

        // assert
        val json = JSONObject(result)
        assertThat(json.getString("rpId")).isEqualTo("new.provided.id")
    }

    @Test
    fun `challengeData without rpId preserves existing rpId`() {
        // arrange
        val challengeJson = """
            {
              "challenge": "randomChallengeString",
              "rpId": "existing.id",
              "extensions": {
                "appid": "https://test.test.com"
              }
            }
        """.trimIndent()
        val capability = IdxWebAuthnAuthenticationCapability(challengeJson)

        // act
        val result = capability.challengeData().getOrThrow()

        // assert
        val json = JSONObject(result)
        assertThat(json.getString("rpId")).isEqualTo("existing.id")
    }

    @Test
    fun `challengeData without rpId derives id from extensions appid`() {
        // arrange
        val challengeJson = """
            {
              "challenge": "randomChallengeString",
              "extensions": {
                "appid": "https://test.test.com"
              }
            }
        """.trimIndent()
        val capability = IdxWebAuthnAuthenticationCapability(challengeJson)

        // act
        val result = capability.challengeData().getOrThrow()

        // assert
        val json = JSONObject(result)
        assertThat(json.getString("rpId")).isEqualTo("test.test.com")
    }

    @Test
    fun `challengeData with invalid appid does not add rpId`() {
        // arrange
        val challengeJson = """
            {
              "challenge": "randomChallengeString",
              "extensions": {
                "appid": "invalid appid"
              }
            }
        """.trimIndent()
        val capability = IdxWebAuthnAuthenticationCapability(challengeJson)

        // act
        val result = capability.challengeData().getOrThrow()

        // assert
        val json = JSONObject(result)
        assertThat(json.has("rpId")).isFalse()
    }

    @Test
    fun `challengeData without rpId and no source for id does not add rpId`() {
        // arrange
        val challengeJson = """
            {
              "challenge": "randomChallengeString"
            }
        """.trimIndent()
        val capability = IdxWebAuthnAuthenticationCapability(challengeJson)

        // act
        val result = capability.challengeData().getOrThrow()

        // assert
        val json = JSONObject(result)
        assertThat(json.has("rpId")).isFalse()
    }
}
