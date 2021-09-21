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
}
