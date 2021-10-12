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
import org.junit.Test

class IdxRemediationTransformTest {
    @Test fun testCopyValuesFromPrevious() {
        val remediation = createRemediation(
            fields = listOf(
                createField("one")
            )
        )
        val previousRemediation = createRemediation(
            fields = listOf(
                createField("one", value = "Something!")
            )
        )
        assertThat(remediation.form[0].value).isNull()
        remediation.copyValuesFromPrevious(previousRemediation)
        assertThat(remediation.form[0].value).isEqualTo("Something!")
    }

    @Test fun testCopyValuesFromPreviousWithMisMatchedFieldNames() {
        val remediation = createRemediation(
            fields = listOf(
                createField("one")
            )
        )
        val previousRemediation = createRemediation(
            fields = listOf(
                createField("two", value = "Something!")
            )
        )
        assertThat(remediation.form[0].value).isNull()
        remediation.copyValuesFromPrevious(previousRemediation)
        assertThat(remediation.form[0].value).isNull()
    }

    @Test fun testCopyValuesFromPreviousWithMisMatchedRemediationNames() {
        val remediation = createRemediation(
            name = "identify",
            fields = listOf(
                createField("one")
            )
        )
        val previousRemediation = createRemediation(
            name = "challenge-authenticator",
            fields = listOf(
                createField("one", value = "Something!")
            )
        )
        assertThat(remediation.form[0].value).isNull()
        remediation.copyValuesFromPrevious(previousRemediation)
        assertThat(remediation.form[0].value).isNull()
    }

    @Test fun testCopyValuesFromPreviousWithMisMatchedFieldCount() {
        val remediation = createRemediation(
            fields = listOf(
                createField("one"),
                createField("two")
            )
        )
        val previousRemediation = createRemediation(
            fields = listOf(
                createField("one", value = "Something!")
            )
        )
        assertThat(remediation.form[0].value).isNull()
        remediation.copyValuesFromPrevious(previousRemediation)
        assertThat(remediation.form[0].value).isNull()
    }

    @Test fun testCopyValuesFromPreviousWithMisMatchedFieldType() {
        val remediation = createRemediation(
            fields = listOf(
                createField("one", type = "string"),
            )
        )
        val previousRemediation = createRemediation(
            fields = listOf(
                createField("one", type = "boolean", value = false)
            )
        )
        assertThat(remediation.form[0].value).isNull()
        remediation.copyValuesFromPrevious(previousRemediation)
        assertThat(remediation.form[0].value).isNull()
    }

    @Test fun testCopyValuesFromPreviousWithNonMutableField() {
        val remediation = createRemediation(
            fields = listOf(
                createField("one", mutable = false),
            )
        )
        val previousRemediation = createRemediation(
            fields = listOf(
                createField("one", mutable = false, value = "Something!")
            )
        )
        assertThat(remediation.form[0].value).isNull()
        remediation.copyValuesFromPrevious(previousRemediation)
        assertThat(remediation.form[0].value).isNull()
    }

    @Test fun testCopyValuesFromPreviousWithNestedForm() {
        val remediation = createRemediation(
            fields = listOf(
                createField(
                    "one",
                    form = createForm(
                        listOf(
                            createField("two")
                        )
                    )
                ),
            )
        )
        val previousRemediation = createRemediation(
            fields = listOf(
                createField(
                    "one",
                    form = createForm(
                        listOf(
                            createField("two", value = "Something!")
                        )
                    )
                ),
            )
        )
        assertThat(remediation.form[0].form?.get(0)?.value).isNull()
        remediation.copyValuesFromPrevious(previousRemediation)
        assertThat(remediation.form[0].form?.get(0)?.value).isEqualTo("Something!")
    }
}
