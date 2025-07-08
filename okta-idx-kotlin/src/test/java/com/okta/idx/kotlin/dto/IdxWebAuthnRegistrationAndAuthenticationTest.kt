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
import com.google.common.truth.Truth
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert
import org.json.JSONException
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdxWebAuthnRegistrationAndAuthenticationTest {

    private val base64UrlString = "Pj8-Pw"
    private val base64String = "Pj8+Pw=="
    private val simpleB64String = "c2lnbmF0dXJl"

    private fun createChallengeRemediation(
        hasCapability: Boolean = true,
        formFields: List<String> = listOf(
            "authenticatorData",
            "clientData",
            "signatureData"
        )
    ): IdxRemediation {
        val challengeData = """
            "challengeData": {
                "challenge": "7lnfbItamo2HerI_cLsjFH7t9ubvh89r",
                "userVerification": "preferred",
                "extensions": {
                    "appid": "https://test.test.com"
                }
            }
        """.trimIndent()

        val capabilities = if (hasCapability) {
            setOf(IdxWebAuthnAuthenticationCapability(challengeData))
        } else {
            emptySet()
        }

        val authenticator = IdxAuthenticator(
            key = "test-authenticator",
            displayName = "Test Authenticator",
            type = IdxAuthenticator.Kind.SECURITY_KEY,
            methods = listOf(IdxAuthenticator.Method.WEB_AUTHN),
            state = IdxAuthenticator.State.ENROLLING,
            capabilities = IdxCapabilityCollection(capabilities),
            id = "fakeId",
            methodNames = listOf("webauthn"),
        )

        val fields = formFields.map { fieldName ->
            IdxRemediation.Form.Field(
                name = fieldName, isMutable = true,
                label = fieldName,
                type = "string",
                isRequired = false,
                isSecret = false,
                form = null,
                options = emptyList(),
                messages = IdxMessageCollection(emptyList()),
                authenticator = null,
                isVisible = false,
                _value = ""
            )
        }
        val credentialField = IdxRemediation.Form.Field(
            name = "credentials", isMutable = true,
            label = "credentials",
            type = "string",
            isRequired = false,
            isSecret = false,
            form = IdxRemediation.Form(fields),
            options = emptyList(),
            messages = IdxMessageCollection(emptyList()),
            authenticator = null,
            isVisible = false,
            _value = ""
        )

        return IdxRemediation(
            type = IdxRemediation.Type.CHALLENGE_AUTHENTICATOR,
            name = "challenge-authenticator",
            form = IdxRemediation.Form(listOf(credentialField)),
            authenticators = IdxAuthenticatorCollection(listOf(authenticator)),
            capabilities = IdxCapabilityCollection(emptySet()),
            method = "method",
            href = "https://test.okta.com/idp/idx/identify".toHttpUrl(),
            accepts = null
        )
    }

    private val authenticator = IdxAuthenticator(
        key = "test-authenticator",
        displayName = "Test Authenticator",
        type = IdxAuthenticator.Kind.SECURITY_KEY,
        methods = listOf(IdxAuthenticator.Method.WEB_AUTHN),
        state = IdxAuthenticator.State.ENROLLING,
        capabilities = IdxCapabilityCollection(setOf(IdxWebAuthnRegistrationCapability("activationData"))),
        id = "fakeId",
        methodNames = listOf("webauthn"),
    )

    private val remediationEmptyForm = IdxRemediation(
        type = IdxRemediation.Type.UNKNOWN,
        name = "test",
        form = IdxRemediation.Form(emptyList()),
        authenticators = IdxAuthenticatorCollection(listOf(authenticator)),
        capabilities = IdxCapabilityCollection(emptySet()),
        method = "method",
        href = "https://test.okta.com/idp/idx/identify".toHttpUrl(),
        accepts = null
    )

    private val attestationValue = "attestation-object"
    private val clientDataValue = "client-data-json"
    private val registrationResponseJson = """
{
  "rawId": "fakeRawId",
  "authenticatorAttachment": "platform",
  "type": "public-key",
  "id": "fakeId",
  "response": {
    "clientDataJSON": "$clientDataValue",
    "attestationObject": "$attestationValue",
    "transports": [
      "internal",
      "hybrid"
    ],
    "authenticatorData": "fakedata",
    "publicKeyAlgorithm": -7,
    "publicKey": "fakedPublicKey"
  },
  "clientExtensionResults": {
    "credProps": {
      "rk": true
    }
  }
}
    """.trimIndent()

    private inline fun <reified Capability : IdxAuthenticator.Capability> IdxAuthenticatorCollection.capability(): Capability? {
        val authenticator = firstOrNull { it.capabilities.get<Capability>() != null } ?: return null
        return authenticator.capabilities.get()
    }

    @Test
    fun `withRegistrationResponse sets attestation and clientData fields`() {
        // arrange
        val clientDataField = IdxRemediation.Form.Field(
            name = "clientData",
            label = null,
            type = "string",
            _value = null,
            isVisible = true,
            isMutable = true,
            isRequired = false,
            isSecret = false,
            form = null,
            options = null,
            messages = IdxMessageCollection(emptyList()),
            authenticator = null
        )

        val attestationValueField = clientDataField.copy(name = "attestation")
        val credentialField = clientDataField.copy(name = "credentials", form = IdxRemediation.Form(listOf(attestationValueField, clientDataField)))

        val form = IdxRemediation.Form(listOf(credentialField))

        val remediation = IdxRemediation(
            type = IdxRemediation.Type.UNKNOWN,
            name = "test",
            form = form,
            authenticators = IdxAuthenticatorCollection(listOf(authenticator)),
            capabilities = IdxCapabilityCollection(emptySet()),
            method = "method",
            href = "https://test.okta.com/idp/idx/identify".toHttpUrl(),
            accepts = null
        )

        val capability = requireNotNull(remediation.authenticators.capability<IdxWebAuthnRegistrationCapability>())

        // act
        val updated = capability.withRegistrationResponse(remediation, registrationResponseJson).getOrThrow()

        // assert
        MatcherAssert.assertThat(updated.form["credentials.attestation"]?.value, `is`(attestationValue))
        MatcherAssert.assertThat(updated.form["credentials.clientData"]?.value, `is`(clientDataValue))
    }

    @Test
    fun `withRegistrationResponse throws if attestation field missing`() {
        // arrange
        val form = IdxRemediation.Form(emptyList())
        val remediation = IdxRemediation(
            type = IdxRemediation.Type.UNKNOWN,
            name = "test",
            form = form,
            authenticators = IdxAuthenticatorCollection(listOf(authenticator)),
            capabilities = IdxCapabilityCollection(emptySet()),
            method = "method",
            href = "https://test.okta.com/idp/idx/identify".toHttpUrl(),
            accepts = null
        )
        val capability = requireNotNull(remediation.authenticators.capability<IdxWebAuthnRegistrationCapability>())

        // act
        val exception = capability.withRegistrationResponse(remediation, registrationResponseJson).exceptionOrNull()

        // assert
        MatcherAssert.assertThat(exception, notNullValue())
        MatcherAssert.assertThat(exception, instanceOf(IllegalArgumentException::class.java))
        MatcherAssert.assertThat(exception?.message, `is`("The 'credentials.attestation' field is not present in the remediation form."))
    }

    @Test
    fun `withRegistrationResponse throws if clientData field missing`() {
        // arrange
        val attestationValueField = IdxRemediation.Form.Field(
            name = "attestation",
            label = null,
            type = "string",
            _value = null,
            isVisible = true,
            isMutable = true,
            isRequired = false,
            isSecret = false,
            form = null,
            options = null,
            messages = IdxMessageCollection(emptyList()),
            authenticator = null
        )

        val credentialField = attestationValueField.copy(name = "credentials", form = IdxRemediation.Form(listOf(attestationValueField)))
        val remediation = IdxRemediation(
            type = IdxRemediation.Type.UNKNOWN,
            name = "test",
            form = IdxRemediation.Form(listOf(credentialField)),
            authenticators = IdxAuthenticatorCollection(listOf(authenticator)),
            capabilities = IdxCapabilityCollection(emptySet()),
            method = "method",
            href = "https://test.okta.com/idp/idx/identify".toHttpUrl(),
            accepts = null
        )
        val capability = requireNotNull(remediation.authenticators.capability<IdxWebAuthnRegistrationCapability>())

        // act
        val exception = capability.withRegistrationResponse(remediation, registrationResponseJson).exceptionOrNull()

        // assert
        MatcherAssert.assertThat(exception, notNullValue())
        MatcherAssert.assertThat(exception, instanceOf(IllegalArgumentException::class.java))
        MatcherAssert.assertThat(exception?.message, `is`("The 'credentials.clientData' field is not present in the remediation form."))
    }

    @Test
    fun `withRegistrationResponse throws if registrationResponseJson is invalid json`() {
        // arrange
        val invalidJson = "invalid json"
        val capability = IdxWebAuthnRegistrationCapability("activationData")

        // act
        val exception = capability.withRegistrationResponse(remediationEmptyForm, invalidJson).exceptionOrNull()

        // assert
        MatcherAssert.assertThat(exception, notNullValue())
        MatcherAssert.assertThat(exception, instanceOf(JSONException::class.java))
        MatcherAssert.assertThat(exception?.message, `is`("Value invalid of type java.lang.String cannot be converted to JSONObject"))
    }

    @Test
    fun `withRegistrationResponse throws if registrationResponseJson is missing response object`() {
        // arrange
        val missingResponse = JSONObject(registrationResponseJson).remove("response")?.toString() ?: error("Failed to remove response object")
        val capability = IdxWebAuthnRegistrationCapability("activationData")

        // act
        val exception = capability.withRegistrationResponse(remediationEmptyForm, missingResponse).exceptionOrNull()

        // assert
        MatcherAssert.assertThat(exception, notNullValue())
        MatcherAssert.assertThat(exception, instanceOf(JSONException::class.java))
        MatcherAssert.assertThat(exception?.message, `is`("No value for response"))
    }

    @Test
    fun `withRegistrationResponse throws if registrationResponseJson is missing attestationObject field`() {
        // arrange
        val json = JSONObject(registrationResponseJson).apply {
            getJSONObject("response").remove("attestationObject")
        }.toString()
        val capability = IdxWebAuthnRegistrationCapability("activationData")

        // act
        val exception = capability.withRegistrationResponse(remediationEmptyForm, json).exceptionOrNull()

        // assert
        MatcherAssert.assertThat(exception, notNullValue())
        MatcherAssert.assertThat(exception, instanceOf(JSONException::class.java))
        MatcherAssert.assertThat(exception?.message, `is`("No value for attestationObject"))
    }

    @Test
    fun `withRegistrationResponse throws if registrationResponseJson is missing clientDataJSON field`() {
        // arrange
        val json = JSONObject(registrationResponseJson).apply {
            getJSONObject("response").remove("clientDataJSON")
        }.toString()
        val capability = IdxWebAuthnRegistrationCapability("activationData")

        // act
        val exception = capability.withRegistrationResponse(remediationEmptyForm, json).exceptionOrNull()

        // assert
        MatcherAssert.assertThat(exception, notNullValue())
        MatcherAssert.assertThat(exception, instanceOf(JSONException::class.java))
        MatcherAssert.assertThat(exception?.message, `is`("No value for clientDataJSON"))
    }

    @Test
    fun `withRegistrationResponse throws if attestationObject field is blank`() {
        // arrange
        val json = JSONObject(registrationResponseJson).apply {
            getJSONObject("response").put("attestationObject", " ")
        }.toString()
        val capability = IdxWebAuthnRegistrationCapability("activationData")

        // act
        val exception = capability.withRegistrationResponse(remediationEmptyForm, json).exceptionOrNull()

        // assert
        MatcherAssert.assertThat(exception, notNullValue())
        MatcherAssert.assertThat(exception, instanceOf(IllegalArgumentException::class.java))
        MatcherAssert.assertThat(exception?.message, `is`("The 'attestationObject' field is not present in the create credential response."))
    }

    @Test
    fun `withRegistrationResponse throws if clientDataJSON field is blank`() {
        // arrange
        val json = JSONObject(registrationResponseJson).apply {
            getJSONObject("response").put("clientDataJSON", " ")
        }.toString()
        val capability = IdxWebAuthnRegistrationCapability("activationData")

        // act
        val exception = capability.withRegistrationResponse(remediationEmptyForm, json).exceptionOrNull()

        // assert
        MatcherAssert.assertThat(exception, notNullValue())
        MatcherAssert.assertThat(exception, instanceOf(IllegalArgumentException::class.java))
        MatcherAssert.assertThat(exception?.message, `is`("The 'clientDataJSON' field is not present in the create credential response."))
    }

    @Test
    fun `withRegistrationResponse throws if remediation does not have webauthn capability`() {
        // arrange
        val form = IdxRemediation.Form(emptyList())
        val remediation = IdxRemediation(
            type = IdxRemediation.Type.UNKNOWN,
            name = "test",
            form = form,
            authenticators = IdxAuthenticatorCollection(listOf()),
            capabilities = IdxCapabilityCollection(emptySet()),
            method = "method",
            href = "https://test.okta.com/idp/idx/identify".toHttpUrl(),
            accepts = null
        )
        val capability = IdxWebAuthnRegistrationCapability("activationData")

        // act
        val exception = capability.withRegistrationResponse(remediation, registrationResponseJson).exceptionOrNull()

        // assert
        MatcherAssert.assertThat(exception, notNullValue())
        MatcherAssert.assertThat(exception, instanceOf(IllegalArgumentException::class.java))
        MatcherAssert.assertThat(exception?.message, `is`("This remediation does not have a WebAuthn registration capability."))
    }

    @Test
    fun `withAuthenticationResponseJson succeeds with valid Base64Url data`() {
        // arrange
        val remediation = createChallengeRemediation()
        val authResponseJson = """
            {
              "response": {
                "clientDataJson": "$base64UrlString",
                "authenticatorData": "$base64UrlString",
                "signature": "$base64UrlString"
              }
            }
        """.trimIndent()
        val capability = requireNotNull(remediation.authenticators.capability<IdxWebAuthnAuthenticationCapability>())

        // act
        val updatedRemediation = capability.withAuthenticationResponseJson(remediation, authResponseJson).getOrThrow()

        // assert
        Truth.assertThat(updatedRemediation.form["credentials.clientData"]?.value).isEqualTo(base64String)
        Truth.assertThat(updatedRemediation.form["credentials.authenticatorData"]?.value).isEqualTo(base64String)
        Truth.assertThat(updatedRemediation.form["credentials.signatureData"]?.value).isEqualTo(base64String)
    }

    @Test
    fun `withAuthenticationResponseJson succeeds with valid standard Base64 data`() {
        // arrange
        val remediation = createChallengeRemediation()
        val authResponseJson = """
            {
              "response": {
                "clientDataJson": "$simpleB64String",
                "authenticatorData": "$simpleB64String",
                "signature": "$simpleB64String"
              }
            }
        """.trimIndent()
        val capability = requireNotNull(remediation.authenticators.capability<IdxWebAuthnAuthenticationCapability>())

        // act
        val updatedRemediation = capability.withAuthenticationResponseJson(remediation, authResponseJson).getOrThrow()

        // assert
        Truth.assertThat(updatedRemediation.form["credentials.clientData"]?.value).isEqualTo(simpleB64String)
        Truth.assertThat(updatedRemediation.form["credentials.authenticatorData"]?.value).isEqualTo(simpleB64String)
        Truth.assertThat(updatedRemediation.form["credentials.signatureData"]?.value).isEqualTo(simpleB64String)
    }

    @Test
    fun `withAuthenticationResponseJson fails when capability is missing`() {
        // arrange
        val remediation = createChallengeRemediation(hasCapability = false)
        val authResponseJson = """{"response":{}}"""
        val capability = IdxWebAuthnAuthenticationCapability("challengeData")

        // act
        val exception = capability.withAuthenticationResponseJson(remediation, authResponseJson).exceptionOrNull()

        // assert
        Truth.assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
        Truth.assertThat(exception).hasMessageThat().isEqualTo("This remediation does not have a WebAuthn authentication capability.")
    }

    @Test
    fun `withAuthenticationResponseJson fails when clientDataJson is missing from response`() {
        // arrange
        val remediation = createChallengeRemediation()
        val authResponseJson = """
            {
              "response": {
                "clientDataJson": "",
                "authenticatorData": "data",
                "signature": "data"
              }
            }
        """.trimIndent()
        val capability = requireNotNull(remediation.authenticators.capability<IdxWebAuthnAuthenticationCapability>())

        // act
        val exception = capability.withAuthenticationResponseJson(remediation, authResponseJson).exceptionOrNull()

        // assert
        Truth.assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
        Truth.assertThat(exception).hasMessageThat().isEqualTo("The 'clientDataJson' field is not present in the authentication response.")
    }

    @Test
    fun `withAuthenticationResponseJson fails when authenticatorData is missing from response`() {
        // arrange
        val remediation = createChallengeRemediation()
        val authResponseJson = """
            {
              "response": {
                "clientDataJson": "data",
                "authenticatorData": "",
                "signature": "data"
              }
            }
        """.trimIndent()
        val capability = requireNotNull(remediation.authenticators.capability<IdxWebAuthnAuthenticationCapability>())

        // act
        val exception = capability.withAuthenticationResponseJson(remediation, authResponseJson).exceptionOrNull()

        // assert
        Truth.assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
        Truth.assertThat(exception).hasMessageThat().isEqualTo("The 'authenticatorData' field is not present in the authentication response.")
    }

    @Test
    fun `withAuthenticationResponseJson fails when signature is missing from response`() {
        // arrange
        val remediation = createChallengeRemediation()
        val authResponseJson = """
            {
              "response": {
                "clientDataJson": "data",
                "authenticatorData": "data",
                "signature" : ""
              }
            }
        """.trimIndent()
        val capability = requireNotNull(remediation.authenticators.capability<IdxWebAuthnAuthenticationCapability>())

        // act
        val exception = capability.withAuthenticationResponseJson(remediation, authResponseJson).exceptionOrNull()

        // assert
        Truth.assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
        Truth.assertThat(exception).hasMessageThat().isEqualTo("The 'signature' field is not present in the authentication response.")
    }

    @Test
    fun `withAuthenticationResponseJson fails when form field is missing`() {
        // arrange
        val remediation = createChallengeRemediation(formFields = listOf("credentials.clientData", "credentials.signatureData"))
        val authResponseJson = """
            {
              "response": {
                "clientDataJson": "$simpleB64String",
                "authenticatorData": "$simpleB64String",
                "signature": "$simpleB64String"
              }
            }
        """.trimIndent()
        val capability = requireNotNull(remediation.authenticators.capability<IdxWebAuthnAuthenticationCapability>())

        // act
        val exception = capability.withAuthenticationResponseJson(remediation, authResponseJson).exceptionOrNull()

        // assert
        Truth.assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
        Truth.assertThat(exception).hasMessageThat().isEqualTo("The 'credentials.authenticatorData' field is not present in the remediation form.")
    }

    @Test
    fun `withAuthenticationResponseJson fails with invalid JSON`() {
        // arrange
        val remediation = createChallengeRemediation()
        val invalidJson = """{"response": { "clientDataJson": "data" """
        val capability = requireNotNull(remediation.authenticators.capability<IdxWebAuthnAuthenticationCapability>())

        // act
        val result = capability.withAuthenticationResponseJson(remediation, invalidJson)

        // assert
        Truth.assertThat(result.isFailure).isTrue()
        Truth.assertThat(result.exceptionOrNull()).isInstanceOf(JSONException::class.java)
    }

    @Test
    fun `withAuthenticationResponseJson fails with invalid Base64 data`() {
        // arrange
        val remediation = createChallengeRemediation()
        val authResponseJson = """
            {
              "response": {
                "clientDataJson": "$simpleB64String",
                "authenticatorData": "this is not valid base64!",
                "signature": "$simpleB64String"
              }
            }
        """.trimIndent()
        val capability = requireNotNull(remediation.authenticators.capability<IdxWebAuthnAuthenticationCapability>())

        // act
        val result = capability.withAuthenticationResponseJson(remediation, authResponseJson)
        Truth.assertThat(result.isFailure).isTrue()

        // assert
        Truth.assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    fun IdxRemediation.Form.Field.copy(
        name: String? = this.name,
        label: String? = this.label,
        type: String = this.type,
        value: Any? = this.value,
        isMutable: Boolean = this.isMutable,
        isRequired: Boolean = this.isRequired,
        isSecret: Boolean = this.isSecret,
        form: IdxRemediation.Form? = this.form,
        options: List<IdxRemediation.Form.Field>? = this.options,
        messages: IdxMessageCollection = this.messages,
        authenticator: IdxAuthenticator? = this.authenticator,
        isVisible: Boolean = this.isVisible,
    ): IdxRemediation.Form.Field {
        return IdxRemediation.Form.Field(name, label, type, value, isMutable, isRequired, isSecret, form, options, messages, authenticator, isVisible)
    }
}
