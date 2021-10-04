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
package com.okta.idx.kotlin.dto

import com.google.common.truth.Truth.assertThat
import com.okta.idx.kotlin.client.toJsonContent
import com.okta.idx.kotlin.dto.v1.Response
import com.okta.idx.kotlin.dto.v1.toIdxResponse
import com.okta.idx.kotlin.infrastructure.stringFromResources
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test

class IdxResponseTest {
    val json = Json {
        ignoreUnknownKeys = true
    }

    private fun Response.toIdxResponse(): IdxResponse {
        return toIdxResponse(json)
    }

    @Test fun testJson() {
        val response = json.decodeFromString<Response>(stringFromResources("dto/idx_response.json"))
        assertThat(response).isNotNull()
        val idxResponse = response.toIdxResponse()
        assertThat(idxResponse).isNotNull()
    }

    @Test fun testAuthenticators() {
        val response = json.decodeFromString<Response>(stringFromResources("dto/select_authenticator.json"))
        assertThat(response).isNotNull()
        val idxResponse = response.toIdxResponse()
        assertThat(idxResponse).isNotNull()
        val remediation = idxResponse.remediations[IdxRemediation.Type.SELECT_AUTHENTICATOR_AUTHENTICATE]!!
        val field = remediation.form["authenticator"]
        field?.selectedOption = field?.options!![0]
        val requestJson = remediation.toJsonContent().toString()
        assertThat(requestJson).isEqualTo("""{"authenticator":{"id":"auttbu5xxmIlrSqER5d6","methodType":"email"},"stateHandle":"02jbM3ltYruI-MEq8h8RCpHfnjPy-kJxpVq2HlfO2l"}""")
    }

    @Test fun testEnrollSms() {
        val response = json.decodeFromString<Response>(stringFromResources("dto/enroll_sms.json"))
        assertThat(response).isNotNull()
        val idxResponse = response.toIdxResponse()
        assertThat(idxResponse).isNotNull()
        val remediation = idxResponse.remediations[IdxRemediation.Type.AUTHENTICATOR_ENROLLMENT_DATA]!!
        val field = remediation.form["authenticator"]!!
        val methodTypeField = field.form!!["methodType"]!!
        methodTypeField.selectedOption = methodTypeField.options!![0]
        val phoneNumberField = field.form!!["phoneNumber"]!!
        phoneNumberField.value = "+11234567"
        assertThat(phoneNumberField.label).isEqualTo("Phone")
        val requestJson = remediation.toJsonContent().toString()
        assertThat(requestJson).isEqualTo("""{"authenticator":{"id":"auttbu5xyM4W2p68j5d6","methodType":"sms","phoneNumber":"+11234567"},"stateHandle":"02lJ5iq1Q9wj50tLVyHYid1OnjAGmQG3taz6A521u9"}""")
    }

    @Test fun testRecover() {
        val response = json.decodeFromString<Response>(stringFromResources("dto/idx_response.json"))
        assertThat(response).isNotNull()
        val idxResponse = response.toIdxResponse()
        assertThat(idxResponse).isNotNull()
        val idxAuthenticator = idxResponse.authenticators.current!!
        assertThat(idxAuthenticator).isNotNull()
        assertThat(idxAuthenticator.traits.get<IdxRecoverTrait>()).isNotNull()
    }

    @Test fun testIdp() {
        val response = json.decodeFromString<Response>(stringFromResources("dto/idx_response.json"))
        assertThat(response).isNotNull()
        val idxResponse = response.toIdxResponse()
        assertThat(idxResponse).isNotNull()
        val idxRemediation = idxResponse.remediations[IdxRemediation.Type.REDIRECT_IDP]!!
        assertThat(idxRemediation.traits.get<IdxIdpTrait>()).isNotNull()
    }

    @Test fun testAuthenticatorMapping() {
        val response = json.decodeFromString<Response>(stringFromResources("dto/enroll_sms.json"))
        val idxResponse = response.toIdxResponse()

        val enrollmentDataRemediation = idxResponse.remediations[IdxRemediation.Type.AUTHENTICATOR_ENROLLMENT_DATA]!!
        assertThat(enrollmentDataRemediation.authenticators.size).isEqualTo(1)
        assertThat(enrollmentDataRemediation.authenticators[0].traits.get<IdxResendTrait>()?.remediation).isNotNull()

        val selectAuthenticatorEnrollRemediation = idxResponse.remediations[IdxRemediation.Type.SELECT_AUTHENTICATOR_ENROLL]!!
        val phoneAuthenticator = selectAuthenticatorEnrollRemediation["authenticator"]!!.options!![0].authenticator!!
        assertThat(phoneAuthenticator).isNotNull()
        assertThat(phoneAuthenticator.id).isEqualTo("auttbu5xyM4W2p68j5d6")
    }

    @Test fun testTotp() {
        val response = json.decodeFromString<Response>(stringFromResources("dto/totp.json"))
        val idxResponse = response.toIdxResponse()

        val currentAuthenticator = idxResponse.authenticators.current!!
        val totpTrait = currentAuthenticator.traits.get<IdxTotpTrait>()!!
        assertThat(totpTrait.imageData).isEqualTo("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMgAAADICAYAAACtWK6eAAAE8klEQVR42u3dwYrjMBAE0Pz/T+/eB5ZliNVVLb+COWVIHEsv0Jbc/vwRkX/m4xSIACICiAggIoCIACICiAggIoCIACIigIgAIgKICCAigIgAIgKICCA/3+jzGf373+f/9vh++35Pv//p4/vt8ae/79PzAxBAAAEEEEAAAQSQ+4E8Xix9+f7tE/A0qOnj2zY/AAEEEEAAAQQQQAC5D8h0Ef4tkPbvny5ypy+ipAACAggggAACCCCAAALIqYW9byfE1MLVFJhtFyEAAQQQQAABBBBAAAFkW5E+DTD9eacvGgACCCCAAAIIIIAAAkgaSPr92yfsdNE+ffw2KwICCCCAAAIIIIAAchpIe9MGr2df19UEEK8DAojXAQHE64DcltNFYbrITd/g1Ha+j40jIIAAAggggAACCCC1QKabBLR9/tMTctvxn/686zYrAgIIIIAAAggggAAyDqS9CG77wXhb47vTgAEBBBBAAAEEEEAA2Q8kXYSlmw60n7/p8UmPLyCAAAIIIIAAAggg9wNJP9g+XYRu+z5tF0VedxULEEAAAQQQQAABBJD1ReR0EZ5uDDddJG8DCAgggAACCCCAAALI+4C0TcC33dDVuvDW+gMMCCCAAAIIIIAAAoh1kPbNjOm/6YsC6WbTgAACCCCAAAIIIIAAcnoAphcSt12EaH+IZltzbUAAAQQQQAABBBBA7gfS/tDH0wt/6YW8dFOHts2VgAACCCCAAAIIIIAo0tMLg7dNwLZm0qd/YMfmJSCAAAIIIIAAAggga4FsW0g6DWzb+ZlukgEIIIAAAggggAACCCDTYNoGeHujutNF9/QNUa+/YQoQQAABBBBAAAEEkPjmvXQjuekmDNMP9Ek3pksV9YAAAggggAACCCCA7C3S2xqbtRX97Td0td0QBggggAACCCCAAAIIIOlm0+mFybaFxvYbwNp+cAEBBBBAAAEEEEAA2Q/kdBF8eoDagW5vzg0IIIAAAggggAACCCDTRfrTE+jpCdX+ENI0wOm8bjcvIIAAAggggAACCCDHv3AbqPSEP31+0+crvdkTEEAAAQQQQAABBJD7+mI9vdCU3mzY9gPRthC5fXMlIIAAAggggAACCCD39eZtO+FtC2ntRfPp8Zr+f0AAAQQQQAABBBBAAGlr/LZtYW97k4vWiyqAAAIIIIAAAggggOzZrDi9WW77gKQfmnk67TdkAQIIIIAAAggggACyD8j0QyfTRW1bs+f0DUbpG94AAQQQQAABBBBAAHkfkG0TanqA0wup0++X/gEEBBBAAAEEEEAAAeR9QG5/IEx6s156PKYneKpoBwQQQAABBBBAAAFk7zpI+iLA9IDc1jQivRB8+iIBIIAAAggggAACCCD7gGzbTNheJD89IVy0AAQQQAABBBBAAAHkdiCni/Dpov100Xy6qUT7A2xe/4QpQAABBBBAAAEEEEDGF8bevlkx3Wy67SJNSxEPCCCAAAIIIIAAAgggKUDtA97WOK7thitAAAEEEEAAAQQQQABpK9KmG6m1NS1oO97rn3ILCCCAAAIIIIAAAsjxEzw9gNuPv6259PRFkfR4AQIIIIAAAggggACyD0j6ATNPF5nbH7Dz9IS8rakGIIAAAggggAACCCD7gIjcGEBEABEBRAQQEUBEABEBRAQQEUBEABERQEQAEQFEBBARQEQAEQFE5Jr8Be5WavNTCnMNAAAAAElFTkSuQmCC")
        assertThat(totpTrait.sharedSecret).isEqualTo("AANACLY3MX6EHKJJ")

        assertThat(idxResponse.remediations[0].authenticators[0]).isEqualTo(currentAuthenticator)
    }

    @Test fun testFieldError() {
        val response = json.decodeFromString<Response>(stringFromResources("dto/field_error.json"))
        val idxResponse = response.toIdxResponse()

        val remediation = idxResponse.remediations.first()
        val userProfileForm = remediation.form.visibleFields.first()

        val lastNameField = userProfileForm.form!!.visibleFields[1]
        assertThat(lastNameField.messages).hasSize(0)

        val emailField = userProfileForm.form!!.visibleFields[2]
        assertThat(emailField.messages).hasSize(2)
        assertThat(emailField.messages[0].message).isEqualTo("'Email' must be in the form of an email address")
        assertThat(emailField.messages[0].localizationKey).isEqualTo("registration.error.invalidLoginEmail")
        assertThat(emailField.messages[0].type).isEqualTo(IdxMessage.Severity.ERROR)
        assertThat(emailField.messages[1].message).isEqualTo("Provided value for property 'Email' does not match required pattern")
        assertThat(emailField.messages[1].localizationKey).isEqualTo("registration.error.doesNotMatchPattern")
        assertThat(emailField.messages[1].type).isEqualTo(IdxMessage.Severity.ERROR)
    }

    @Test fun testTopLevelError() {
        val response = json.decodeFromString<Response>(stringFromResources("dto/top_level_error.json"))
        val idxResponse = response.toIdxResponse()

        assertThat(idxResponse.messages).hasSize(1)
        assertThat(idxResponse.messages[0].type).isEqualTo(IdxMessage.Severity.ERROR)
        assertThat(idxResponse.messages[0].localizationKey).isEqualTo("errors.E0000004")
        assertThat(idxResponse.messages[0].message).isEqualTo("Authentication failed")
    }

    @Test fun testSecurityQuestionsSelectFromList() {
        val response = json.decodeFromString<Response>(stringFromResources("dto/enroll_security_question.json"))
        val idxResponse = response.toIdxResponse()

        val remediation = idxResponse.remediations.first()
        assertThat(remediation).isNotNull()

        val field = remediation.form["credentials"]!!

        val chooseSecurityQuestionOption = field.options!![0]
        field.selectedOption = chooseSecurityQuestionOption

        val questionField = chooseSecurityQuestionOption.form!!.visibleFields[0]
        questionField.selectedOption = questionField.options!![0]

        val answerOption = chooseSecurityQuestionOption.form!!.visibleFields[1]
        answerOption.value = "Green eggs and ham"

        val requestJson = remediation.toJsonContent().toString()
        assertThat(requestJson).isEqualTo("""{"credentials":{"questionKey":"disliked_food","answer":"Green eggs and ham"},"stateHandle":"02QPkKzfnfgzF5qcKwWW-o39cODD1_MNgnPoiOclXg"}""")
    }

    @Test fun testSecurityQuestionsCustom() {
        val response = json.decodeFromString<Response>(stringFromResources("dto/enroll_security_question.json"))
        val idxResponse = response.toIdxResponse()

        val remediation = idxResponse.remediations.first()
        assertThat(remediation).isNotNull()

        val field = remediation.form["credentials"]!!

        val createSecurityQuestionOption = field.options!![1]
        field.selectedOption = createSecurityQuestionOption

        val questionField = createSecurityQuestionOption.form!!.visibleFields[0]
        questionField.value = "Favorite Marvel Movie"

        val answerOption = createSecurityQuestionOption.form!!.visibleFields[1]
        answerOption.value = "Iron Man"

        val requestJson = remediation.toJsonContent().toString()
        assertThat(requestJson).isEqualTo("""{"credentials":{"questionKey":"custom","question":"Favorite Marvel Movie","answer":"Iron Man"},"stateHandle":"02QPkKzfnfgzF5qcKwWW-o39cODD1_MNgnPoiOclXg"}""")
    }

    @Test fun testPoll() {
        val response = json.decodeFromString<Response>(stringFromResources("dto/challenge_email.json"))
        val idxResponse = response.toIdxResponse()

        val remediation = idxResponse.remediations.first()
        val authenticator = remediation.authenticators.first()
        val pollTrait = authenticator.traits.get<IdxPollTrait>()!!

        assertThat(pollTrait.wait).isEqualTo(4000)
        assertThat(pollTrait.authenticatorId).isEqualTo("eaewrvclbBPr2PAxl5d6")

        val requestJson = pollTrait.remediation.toJsonContent().toString()
        assertThat(requestJson).isEqualTo("""{"stateHandle":"02ifdLyhqQ9Il4OtUU50jCdhFeCH-bzojwfpOci9EO"}""")
    }
}
