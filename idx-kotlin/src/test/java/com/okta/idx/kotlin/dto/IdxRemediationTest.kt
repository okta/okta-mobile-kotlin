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

class IdxRemediationTest {
    @Test fun testGetFindsField() {
        val remediation = createRemediation(
            fields = listOf(
                createField("one")
            )
        )
        assertThat(remediation["one"]?.name).isEqualTo("one")
    }

    @Test fun testGetFindsHiddenField() {
        val remediation = createRemediation(
            fields = listOf(
                createField("one", isVisible = false)
            )
        )
        assertThat(remediation["one"]?.name).isEqualTo("one")
    }

    @Test fun testGetFindsNestedField() {
        val remediation = createRemediation(
            fields = listOf(
                createField(
                    name = "one",
                    form = createForm(listOf(createField("two")))
                )
            )
        )
        assertThat(remediation["one.two"]?.name).isEqualTo("two")
    }

    @Test fun testGetFindsNestedHiddenField() {
        val remediation = createRemediation(
            fields = listOf(
                createField(
                    name = "one",
                    isVisible = false,
                    form = createForm(listOf(createField(name = "two", isVisible = false)))
                )
            )
        )
        assertThat(remediation["one.two"]?.name).isEqualTo("two")
    }
}
