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
package com.okta.idx.android.sdk

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.common.truth.Truth.assertThat
import com.okta.idx.android.sdk.steps.ChallengeAuthenticatorStep
import com.okta.idx.android.sdk.steps.EnrollAuthenticatorStep
import com.okta.idx.android.sdk.steps.IdentifyUsernameAndPasswordStep
import com.okta.idx.android.sdk.steps.IdentifyUsernameStep
import com.okta.idx.android.sdk.steps.SelectAuthenticatorStep
import com.okta.idx.android.sdk.steps.SelectEnrollProfileStep
import com.okta.idx.android.sdk.steps.SkipStep
import com.okta.idx.sdk.api.response.IDXResponse
import okio.Buffer
import org.junit.Test

internal class StepFactoryTest {
    private val objectMapper: ObjectMapper = ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)

    private fun idxResponse(filename: String): IDXResponse {
        val inputStream =
            StepFactoryTest::class.java.classLoader.getResourceAsStream("step_factory/$filename")
        val buffer = Buffer()
        buffer.readFrom(inputStream)

        val responseJsonNode: JsonNode = objectMapper.readTree(buffer.readUtf8())
        return objectMapper.convertValue(responseJsonNode, IDXResponse::class.java)
    }

    private fun <T> assertFactoryReturnsNonNull(
        filename: String,
        factory: StepFactory<T>,
        stepAssertionCallback: (step: Step<T>) -> Unit,
    ) {
        val response = idxResponse(filename)
        val remediationOption = response.remediation().remediationOptions()[0]
        val step = factory.get(remediationOption)
        assertThat(step).isNotNull()
        stepAssertionCallback(step!!)
    }

    @Test fun identify_username_returnsStep() {
        assertFactoryReturnsNonNull(
            "identify_username.json",
            IdentifyUsernameStep.Factory()
        ) { step ->
            assertThat(step.viewModel.usernameLabel).isEqualTo("Username")
            assertThat(step.viewModel.rememberMeLabel).isEqualTo("Remember this device")
        }
    }

    @Test fun identify_usernameAndPassword_returnsStep() {
        assertFactoryReturnsNonNull(
            "identify_username_password.json",
            IdentifyUsernameAndPasswordStep.Factory()
        ) { step ->
            assertThat(step.viewModel.usernameLabel).isEqualTo("Username")
            assertThat(step.viewModel.passwordLabel).isEqualTo("Enter code")
            assertThat(step.viewModel.rememberMeLabel).isEqualTo("Remember this device")
        }
    }

    @Test fun `select-authenticator-authenticate_returnsStep`() {
        assertFactoryReturnsNonNull(
            "select-authenticator-authenticate.json",
            SelectAuthenticatorStep.Factory()
        ) { step ->
            assertThat(step.viewModel.options).hasSize(3)
            assertThat(step.viewModel.options[0].id).isEqualTo("aut2ihzk1gHl7ynhd1d6")
            assertThat(step.viewModel.options[0].label).isEqualTo("Email")
            val method =
                step.viewModel.options[0].method as SelectAuthenticatorStep.Option.Method.Flat
            assertThat(method.value).isEqualTo("email")
        }
    }

    @Test fun `challenge-authenticator_returnsStep`() {
        assertFactoryReturnsNonNull(
            "challenge-authenticator.json",
            ChallengeAuthenticatorStep.Factory()
        ) { step ->
            assertThat(step.viewModel.passcodeLabel).isEqualTo("Enter code")
        }
    }

    @Test fun `select-authenticator-enroll_returnsStep`() {
        assertFactoryReturnsNonNull(
            "select-authenticator-enroll.json",
            SelectAuthenticatorStep.Factory()
        ) { step ->
            assertThat(step.viewModel.options[0].label).isEqualTo("Security Question")
            assertThat(step.viewModel.options[0].id).isEqualTo("autzvyil7o5nQqC5j2o4")
            val method =
                step.viewModel.options[0].method as SelectAuthenticatorStep.Option.Method.Flat
            assertThat(method.value).isEqualTo("security_question")
        }
    }

    @Test fun `select-authenticator-enroll2_returnsStep`() {
        assertFactoryReturnsNonNull(
            "select-authenticator-enroll2.json",
            SelectAuthenticatorStep.Factory()
        ) { step ->
            assertThat(step.viewModel.options[0].label).isEqualTo("Phone")
            assertThat(step.viewModel.options[0].id).isEqualTo("aut3jya5v26pKeUb30g7")
            val method =
                step.viewModel.options[0].method as SelectAuthenticatorStep.Option.Method.Nested
            assertThat(method.options[0].label).isEqualTo("SMS")
            assertThat(method.options[0].value).isEqualTo("sms")
        }
    }

    @Test fun `enroll-authenticator_returnsStep`() {
        assertFactoryReturnsNonNull(
            "enroll-authenticator.json",
            EnrollAuthenticatorStep.Factory()
        ) { step ->
            assertThat(step.viewModel.options).hasSize(2)

            val firstOption = step.viewModel.options[0] as EnrollAuthenticatorStep.Option.QuestionSelect
            assertThat(firstOption.answerLabel).isEqualTo("Answer#Select")
            assertThat(firstOption.optionLabel).isEqualTo("Choose a security question")
            assertThat(firstOption.fieldLabel).isEqualTo("Choose a security question #field label")
            val firstQuestion = firstOption.questions[0]
            assertThat(firstQuestion.label).isEqualTo("What is the food you least liked as a child?")
            assertThat(firstQuestion.value).isEqualTo("disliked_food")

            val secondOption = step.viewModel.options[1] as EnrollAuthenticatorStep.Option.QuestionCustom
            assertThat(secondOption.answerLabel).isEqualTo("Answer#Custom")
            assertThat(secondOption.optionLabel).isEqualTo("Create my own security question")
            assertThat(secondOption.fieldLabel).isEqualTo("Create a security question")
            assertThat(secondOption.questionKey).isEqualTo("custom")
        }
    }

    @Test fun `enroll-authenticator-password_returnsStep`() {
        assertFactoryReturnsNonNull(
            "enroll-authenticator-password.json",
            EnrollAuthenticatorStep.Factory()
        ) { step ->
            assertThat(step.viewModel.options).hasSize(1)

            val option = step.viewModel.options[0] as EnrollAuthenticatorStep.Option.Passcode
            assertThat(option.optionLabel).isEqualTo("Enter password")
        }
    }

    @Test fun skip_returnsStep() {
        assertFactoryReturnsNonNull(
            "skip.json",
            SkipStep.Factory()
        ) { step ->
            assertThat(step.viewModel).isNotNull()
        }
    }
}
