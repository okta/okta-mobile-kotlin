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

internal fun IdxRemediation.copyValuesFromPrevious(previous: IdxRemediation) {
    if (name != previous.name) return
    form.copyValuesFromPrevious(previous.form)
}

private fun IdxRemediation.Form.copyValuesFromPrevious(previous: IdxRemediation.Form?) {
    if (previous == null) return
    if (visibleFields.size != previous.visibleFields.size) return

    visibleFields.forEachIndexed { index, field ->
        val previousField = previous.visibleFields[index]
        if (field.name != previousField.name) return
        if (field.type != previousField.type) return
        if (field.isMutable) {
            field.value = previousField.value
        }
        field.form?.copyValuesFromPrevious(previousField.form)
    }
}
