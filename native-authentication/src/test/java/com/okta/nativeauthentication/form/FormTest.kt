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
package com.okta.nativeauthentication.form

import com.google.common.truth.Truth.assertThat
import com.okta.idx.kotlin.dto.IdxRemediation
import org.junit.Test
import org.mockito.kotlin.mock

internal class FormTest {
    @Test fun testFormBuilderCreatesForm() {
        val builder = Form.Builder()
        val element = Element.Label.Builder(
            remediation = null,
            text = "Example",
        )
        builder.elements += element
        val form = builder.build(emptyList())
        assertThat(form.elements).hasSize(1)
        assertThat((form.elements[0] as Element.Label).text).isEqualTo("Example")
    }

    @Test fun testFormBuilderTransformsForm() {
        val builder = Form.Builder()
        val transformer = FormTransformer {
            val element = Element.Label.Builder(
                remediation = null,
                text = "Example",
            )
            elements += element
        }
        val form = builder.build(listOf(transformer))
        assertThat(form.elements).hasSize(1)
        assertThat((form.elements[0] as Element.Label).text).isEqualTo("Example")
    }

    @Test fun testValidateFormWithoutErrors() {
        val builder = Form.Builder()
        val remediation = mock<IdxRemediation>()
        val idxField = mock<IdxRemediation.Form.Field>()
        val element = Element.TextInput.Builder(
            remediation = remediation,
            idxField = idxField,
            label = "Example",
            isSecret = false,
            isRequired = true,
            errorMessage = "",
            value = "",
        )
        builder.elements += element
        val form = builder.build(emptyList())
        (form.elements[0] as Element.TextInput).value = "something"
        val (formIsValid, updatedForm) = form.validate(remediation)
        assertThat(formIsValid).isTrue()
        assertThat(updatedForm.elements).hasSize(1)
        assertThat((updatedForm.elements[0] as Element.TextInput.Builder).value).isEqualTo("something")
    }

    @Test fun testValidateFormWithError() {
        val builder = Form.Builder()
        val remediation = mock<IdxRemediation>()
        val idxField = mock<IdxRemediation.Form.Field>()
        val loadingElement = Element.TextInput.Builder(
            remediation = remediation,
            idxField = idxField,
            label = "Example",
            isSecret = false,
            isRequired = true,
            errorMessage = "",
            value = "",
        )
        builder.elements += loadingElement
        val form = builder.build(emptyList())
        val (formIsValid, updatedForm) = form.validate(remediation)
        assertThat(formIsValid).isFalse()
        assertThat(updatedForm.elements).hasSize(1)
    }
}
