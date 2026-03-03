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
package com.okta.directauth.http.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WebAuthnChallengeResponseTest {
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }

    private val issuerUrl = "https://example.okta.com"

    @Test
    fun challengeData_withRpId_serializesPublicKeyAsIs() {
        val response =
            WebAuthnChallengeResponse(
                publicKey =
                    PublicKeyCredentialRequestOptions(
                        challenge = "dGVzdC1jaGFsbGVuZ2U",
                        rpId = "custom.okta.com"
                    )
            )

        val result = response.challengeData(json, issuerUrl)

        val parsed = json.decodeFromString<PublicKeyCredentialRequestOptions>(result)
        assertEquals("dGVzdC1jaGFsbGVuZ2U", parsed.challenge)
        assertEquals("custom.okta.com", parsed.rpId)
    }

    @Test
    fun challengeData_withoutRpId_derivesRpIdFromIssuerUrl() {
        val response =
            WebAuthnChallengeResponse(
                publicKey =
                    PublicKeyCredentialRequestOptions(
                        challenge = "dGVzdC1jaGFsbGVuZ2U",
                        rpId = null
                    )
            )

        val result = response.challengeData(json, issuerUrl)

        val parsed = json.decodeFromString<PublicKeyCredentialRequestOptions>(result)
        assertEquals("example.okta.com", parsed.rpId)
    }

    @Test
    fun challengeData_withoutRpId_withAppIdExtension_derivesRpIdFromAppId() {
        val response =
            WebAuthnChallengeResponse(
                publicKey =
                    PublicKeyCredentialRequestOptions(
                        challenge = "dGVzdC1jaGFsbGVuZ2U",
                        rpId = null,
                        extensions =
                            buildJsonObject {
                                put("appid", JsonPrimitive("https://appid.example.com"))
                            }
                    )
            )

        val result = response.challengeData(json, issuerUrl)

        val parsed = json.decodeFromString<PublicKeyCredentialRequestOptions>(result)
        assertEquals("appid.example.com", parsed.rpId)
    }

    @Test
    fun challengeData_withAllFields_serializesCorrectly() {
        val response =
            WebAuthnChallengeResponse(
                publicKey =
                    PublicKeyCredentialRequestOptions(
                        challenge = "dGVzdC1jaGFsbGVuZ2U",
                        rpId = "example.okta.com",
                        allowCredentials =
                            listOf(
                                PublicKeyCredentialDescriptor(id = "Y3JlZC0x", type = PublicKeyCredentialType.PUBLIC_KEY),
                                PublicKeyCredentialDescriptor(id = "Y3JlZC0y", type = PublicKeyCredentialType.PUBLIC_KEY)
                            ),
                        timeout = 60000,
                        userVerification = UserVerificationRequirement.PREFERRED
                    )
            )

        val result = response.challengeData(json, issuerUrl)

        val parsed = json.decodeFromString<PublicKeyCredentialRequestOptions>(result)
        assertEquals("dGVzdC1jaGFsbGVuZ2U", parsed.challenge)
        assertEquals("example.okta.com", parsed.rpId)
        assertEquals(2, parsed.allowCredentials?.size)
        assertEquals("Y3JlZC0x", parsed.allowCredentials?.get(0)?.id)
        assertEquals("Y3JlZC0y", parsed.allowCredentials?.get(1)?.id)
        assertEquals(60000, parsed.timeout)
        assertEquals(UserVerificationRequirement.PREFERRED, parsed.userVerification)
    }

    @Test
    fun challengeData_withMinimalFields_omitsNullFields() {
        val response =
            WebAuthnChallengeResponse(
                publicKey =
                    PublicKeyCredentialRequestOptions(
                        challenge = "dGVzdC1jaGFsbGVuZ2U",
                        rpId = "example.okta.com"
                    )
            )

        val result = response.challengeData(json, issuerUrl)

        val parsed = json.decodeFromString<PublicKeyCredentialRequestOptions>(result)
        assertEquals("dGVzdC1jaGFsbGVuZ2U", parsed.challenge)
        assertEquals("example.okta.com", parsed.rpId)
        assertNull(parsed.allowCredentials)
        assertNull(parsed.timeout)
        assertNull(parsed.userVerification)
    }

    @Test
    fun challengeData_excludesOuterResponseFields() {
        val response =
            WebAuthnChallengeResponse(
                challengeType = "urn:okta:params:oauth:grant-type:mfa-webauthn",
                publicKey =
                    PublicKeyCredentialRequestOptions(
                        challenge = "dGVzdC1jaGFsbGVuZ2U",
                        rpId = "example.okta.com"
                    ),
                authenticatorEnrollments =
                    listOf(
                        AuthenticatorEnrollment(credentialId = "cred-1", displayName = "My Key")
                    )
            )

        val result = response.challengeData(json, issuerUrl)

        // The serialized output should only contain PublicKeyCredentialRequestOptions fields,
        // not the outer WebAuthnChallengeResponse fields like challengeType or authenticatorEnrollments.
        val jsonElement = json.parseToJsonElement(result)
        val jsonObject = jsonElement as kotlinx.serialization.json.JsonObject
        assertNull(jsonObject["challengeType"])
        assertNull(jsonObject["authenticatorEnrollments"])
        assertNotNull(jsonObject["challenge"])
        assertNotNull(jsonObject["rpId"])
    }

    @Test
    fun challengeData_withoutRpId_withoutExtensions_derivesRpIdFromIssuerUrl() {
        val response =
            WebAuthnChallengeResponse(
                publicKey =
                    PublicKeyCredentialRequestOptions(
                        challenge = "dGVzdC1jaGFsbGVuZ2U",
                        rpId = null,
                        extensions = null
                    )
            )

        val result = response.challengeData(json, "https://subdomain.corp.example.com")

        val parsed = json.decodeFromString<PublicKeyCredentialRequestOptions>(result)
        assertEquals("subdomain.corp.example.com", parsed.rpId)
    }

    @Test
    fun challengeData_preservesRpId_whenPresent() {
        val response =
            WebAuthnChallengeResponse(
                publicKey =
                    PublicKeyCredentialRequestOptions(
                        challenge = "dGVzdC1jaGFsbGVuZ2U",
                        rpId = "explicit.okta.com",
                        extensions =
                            buildJsonObject {
                                put("appid", JsonPrimitive("https://appid.example.com"))
                            }
                    )
            )

        val result = response.challengeData(json, issuerUrl)

        val parsed = json.decodeFromString<PublicKeyCredentialRequestOptions>(result)
        // rpId should remain as-is when already present, ignoring appid extension and issuerUrl.
        assertEquals("explicit.okta.com", parsed.rpId)
    }
}
